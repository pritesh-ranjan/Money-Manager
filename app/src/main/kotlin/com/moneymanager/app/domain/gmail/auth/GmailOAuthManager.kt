package com.moneymanager.app.domain.gmail.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

sealed class OAuthResult {
    data class Success(val email: String?) : OAuthResult()
    data class Error(val message: String) : OAuthResult()
}

open class GmailOAuthManager(
    private val client: OkHttpClient = defaultOkHttpClient()
) {

    companion object {
        fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        @Volatile
        private var pendingCodeVerifier: String? = null

        @Volatile
        private var pendingState: String? = null
    }

    /**
     * Generates PKCE code_verifier (high-entropy cryptographic string)
     */
    open fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Calculates S256 code_challenge = Base64Url(SHA256(code_verifier))
     */
    open fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    /**
     * Builds the Google OAuth 2.0 authorization URL with explicit consent, offline access, and gmail.readonly scope.
     */
    open fun buildAuthorizationUrl(clientId: String, state: String, codeChallenge: String): String {
        val encodedScope = URLEncoder.encode(GmailAuthConfig.GMAIL_READONLY_SCOPE, "UTF-8")
        val encodedRedirectUri = URLEncoder.encode(GmailAuthConfig.REDIRECT_URI, "UTF-8")

        return StringBuilder(GmailAuthConfig.AUTH_ENDPOINT)
            .append("?client_id=").append(clientId)
            .append("&redirect_uri=").append(encodedRedirectUri)
            .append("&response_type=code")
            .append("&scope=").append(encodedScope)
            .append("&access_type=offline")
            .append("&prompt=consent") // Explicit consent to guarantee refresh_token
            .append("&code_challenge=").append(codeChallenge)
            .append("&code_challenge_method=S256")
            .append("&state=").append(state)
            .toString()
    }

    open fun buildAuthorizationUrl(context: Context, state: String, codeChallenge: String): String {
        val clientId = GmailAuthConfig.resolveClientId(context)
        return buildAuthorizationUrl(clientId, state, codeChallenge)
    }

    /**
     * Starts the interactive OAuth flow using Chrome Custom Tabs or browser fallback.
     */
    open fun startOAuthFlow(activity: Activity) {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val stateBytes = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val state = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes)

        pendingCodeVerifier = verifier
        pendingState = state

        val authUrl = buildAuthorizationUrl(activity, state, challenge)
        val uri = Uri.parse(authUrl)

        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(activity, uri)
        } catch (_: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, uri)
            activity.startActivity(browserIntent)
        }
    }

    /**
     * Extracts authorization code from redirect URI and exchanges it for tokens.
     */
    open suspend fun handleRedirectUri(context: Context, redirectUri: Uri): OAuthResult = withContext(Dispatchers.IO) {
        val error = redirectUri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val errorDesc = redirectUri.getQueryParameter("error_description") ?: error
            return@withContext OAuthResult.Error("Google OAuth Error: $errorDesc")
        }

        val code = redirectUri.getQueryParameter("code")
        val state = redirectUri.getQueryParameter("state")

        if (code.isNullOrBlank()) {
            return@withContext OAuthResult.Error("No authorization code returned from Google.")
        }

        // Verify CSRF state
        val expectedState = pendingState
        if (expectedState != null && state != expectedState) {
            return@withContext OAuthResult.Error("Invalid OAuth state parameter. Possible CSRF attempt.")
        }

        val verifier = pendingCodeVerifier
            ?: return@withContext OAuthResult.Error("Missing PKCE code verifier. Please retry authentication.")

        exchangeAuthorizationCode(context, code, verifier)
    }

    /**
     * Exchanges auth code + PKCE verifier for access_token and refresh_token
     */
    open suspend fun exchangeAuthorizationCode(
        context: Context,
        code: String,
        codeVerifier: String
    ): OAuthResult = withContext(Dispatchers.IO) {
        try {
            val clientId = GmailAuthConfig.resolveClientId(context)
            val clientSecret = GmailAuthConfig.resolveClientSecret(context)

            val formBuilder = FormBody.Builder()
                .add("client_id", clientId)
                .add("code", code)
                .add("code_verifier", codeVerifier)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", GmailAuthConfig.REDIRECT_URI)

            if (clientSecret.isNotBlank() && clientSecret != GmailAuthConfig.DEFAULT_CLIENT_SECRET) {
                formBuilder.add("client_secret", clientSecret)
            }

            val request = Request.Builder()
                .url(GmailAuthConfig.TOKEN_ENDPOINT)
                .post(formBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = try {
                        JSONObject(body).optString("error_description", "HTTP ${response.code}")
                    } catch (_: Exception) {
                        "HTTP ${response.code}: $body"
                    }
                    return@withContext OAuthResult.Error("Token exchange failed: $errMsg")
                }

                val json = JSONObject(body)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
                val expiresIn = json.optLong("expires_in", 3600L)

                GmailTokenStorage.saveTokens(
                    context = context,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSeconds = expiresIn
                )

                pendingCodeVerifier = null
                pendingState = null

                OAuthResult.Success(email = null)
            }
        } catch (e: Exception) {
            OAuthResult.Error(e.message ?: "Network error during token exchange")
        }
    }

    /**
     * Ensures a valid, unexpired access token is available. Refreshes if expired.
     */
    open suspend fun getFreshAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        if (!GmailTokenStorage.isAccessTokenExpired(context)) {
            val token = GmailTokenStorage.getAccessToken(context)
            if (!token.isNullOrBlank()) return@withContext token
        }

        val refreshToken = GmailTokenStorage.getRefreshToken(context) ?: return@withContext null
        val clientId = GmailAuthConfig.resolveClientId(context)
        val clientSecret = GmailAuthConfig.resolveClientSecret(context)

        try {
            val formBuilder = FormBody.Builder()
                .add("client_id", clientId)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")

            if (clientSecret.isNotBlank() && clientSecret != GmailAuthConfig.DEFAULT_CLIENT_SECRET) {
                formBuilder.add("client_secret", clientSecret)
            }

            val request = Request.Builder()
                .url(GmailAuthConfig.TOKEN_ENDPOINT)
                .post(formBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext null

                val json = JSONObject(body)
                val newAccessToken = json.getString("access_token")
                val expiresIn = json.optLong("expires_in", 3600L)

                GmailTokenStorage.saveTokens(
                    context = context,
                    accessToken = newAccessToken,
                    refreshToken = null, // refresh token remains unchanged
                    expiresInSeconds = expiresIn
                )

                newAccessToken
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Revokes token on Google's servers and purges on-device storage.
     */
    suspend fun disconnectAndRevoke(context: Context): Boolean = withContext(Dispatchers.IO) {
        val tokenToRevoke = GmailTokenStorage.getRefreshToken(context)
            ?: GmailTokenStorage.getAccessToken(context)

        if (tokenToRevoke != null) {
            try {
                val formBody = FormBody.Builder()
                    .add("token", tokenToRevoke)
                    .build()
                val request = Request.Builder()
                    .url(GmailAuthConfig.REVOKE_ENDPOINT)
                    .post(formBody)
                    .build()
                client.newCall(request).execute().close()
            } catch (_: Exception) {
                // Ignore network error on revoke, still clear local credentials
            }
        }

        GmailTokenStorage.clearAll(context)
        true
    }
}
