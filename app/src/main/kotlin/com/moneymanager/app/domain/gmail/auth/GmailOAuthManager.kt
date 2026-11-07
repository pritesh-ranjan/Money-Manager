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
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
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

        @Volatile
        private var pendingRedirectUri: String? = null

        @Volatile
        private var activeLoopbackServer: ServerSocket? = null
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
    open fun buildAuthorizationUrl(
        clientId: String,
        state: String,
        codeChallenge: String,
        redirectUri: String = GmailAuthConfig.REDIRECT_URI
    ): String {
        val encodedScope = URLEncoder.encode(GmailAuthConfig.GMAIL_READONLY_SCOPE, "UTF-8")
        val encodedRedirectUri = URLEncoder.encode(redirectUri, "UTF-8")
        val encodedClientId = URLEncoder.encode(clientId, "UTF-8")
        val encodedState = URLEncoder.encode(state, "UTF-8")
        val encodedCodeChallenge = URLEncoder.encode(codeChallenge, "UTF-8")

        return StringBuilder(GmailAuthConfig.AUTH_ENDPOINT)
            .append("?client_id=").append(encodedClientId)
            .append("&redirect_uri=").append(encodedRedirectUri)
            .append("&response_type=code")
            .append("&scope=").append(encodedScope)
            .append("&access_type=offline")
            .append("&prompt=consent") // Explicit consent to guarantee refresh_token
            .append("&code_challenge=").append(encodedCodeChallenge)
            .append("&code_challenge_method=S256")
            .append("&state=").append(encodedState)
            .toString()
    }

    open fun buildAuthorizationUrl(
        context: Context,
        state: String,
        codeChallenge: String,
        redirectUri: String = GmailAuthConfig.REDIRECT_URI
    ): String {
        val clientId = GmailAuthConfig.resolveClientId(context)
        return buildAuthorizationUrl(clientId, state, codeChallenge, redirectUri)
    }

    /**
     * Executes the RFC 8252 Loopback Interface flow on 127.0.0.1.
     * Google Cloud "Desktop app" OAuth clients accept any dynamic port on 127.0.0.1 without pre-registration.
     */
    open suspend fun startLoopbackOAuthFlow(
        activity: Activity,
        timeoutMs: Long = 180_000L
    ): OAuthResult = withContext(Dispatchers.IO) {
        stopLoopbackServer()

        val socket = try {
            ServerSocket(0, 5, InetAddress.getByName(GmailAuthConfig.LOOPBACK_REDIRECT_HOST)).apply {
                soTimeout = timeoutMs.toInt()
            }
        } catch (e: Exception) {
            return@withContext OAuthResult.Error("Failed to initialize local loopback server: ${e.message}")
        }

        activeLoopbackServer = socket
        val port = socket.localPort
        val loopbackRedirectUri = GmailAuthConfig.buildLoopbackRedirectUri(port)

        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val stateBytes = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val state = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes)

        pendingCodeVerifier = verifier
        pendingState = state
        pendingRedirectUri = loopbackRedirectUri

        val authUrl = buildAuthorizationUrl(
            context = activity,
            state = state,
            codeChallenge = challenge,
            redirectUri = loopbackRedirectUri
        )

        // Launch browser / Custom Tab on main thread
        withContext(Dispatchers.Main) {
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

        var authCode: String? = null
        var authError: String? = null
        var receivedState: String? = null

        try {
            while (authCode == null && authError == null) {
                val clientSocket = try {
                    socket.accept().apply {
                        soTimeout = 10_000 // 10 second timeout on accepted socket read
                    }
                } catch (e: SocketTimeoutException) {
                    return@withContext OAuthResult.Error("Sign-in timed out. Please try again.")
                } catch (e: IOException) {
                    if (socket.isClosed) {
                        return@withContext OAuthResult.Error("Sign-in cancelled.")
                    }
                    throw e
                }

                try {
                    val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream(), Charsets.UTF_8))
                    val requestLine = reader.readLine()

                    // Consume remaining HTTP request headers
                    var headerLine: String?
                    while (true) {
                        headerLine = reader.readLine()
                        if (headerLine.isNullOrEmpty()) break
                    }

                    if (requestLine != null) {
                        val parts = requestLine.split(" ")
                        if (parts.size >= 2) {
                            val pathAndQuery = parts[1]
                            val requestUri = Uri.parse("http://127.0.0.1$pathAndQuery")
                            val code = requestUri.getQueryParameter("code")
                            val err = requestUri.getQueryParameter("error")
                            val st = requestUri.getQueryParameter("state")

                            if (!code.isNullOrBlank() || !err.isNullOrBlank()) {
                                authCode = code
                                authError = err
                                receivedState = st

                                val isSuccess = !authCode.isNullOrBlank()
                                val responseHtml = buildResponseHtml(isSuccess = isSuccess, error = authError)
                                val responseBytes = responseHtml.toByteArray(Charsets.UTF_8)

                                val out = clientSocket.getOutputStream()
                                val header = "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: text/html; charset=utf-8\r\n" +
                                        "Content-Length: ${responseBytes.size}\r\n" +
                                        "X-Content-Type-Options: nosniff\r\n" +
                                        "Cache-Control: no-store\r\n" +
                                        "Connection: close\r\n\r\n"
                                out.write(header.toByteArray(Charsets.UTF_8))
                                out.write(responseBytes)
                                out.flush()
                                clientSocket.close()
                                break
                            } else {
                                // Ignore non-OAuth requests like /favicon.ico
                                val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                clientSocket.getOutputStream().write(notFound.toByteArray(Charsets.UTF_8))
                                clientSocket.close()
                            }
                        }
                    }
                } catch (_: Exception) {
                    try { clientSocket.close() } catch (_: Exception) {}
                }
            }
        } finally {
            stopLoopbackServer()
        }

        if (!authError.isNullOrBlank()) {
            return@withContext OAuthResult.Error("Google OAuth Error: $authError")
        }

        if (authCode.isNullOrBlank()) {
            return@withContext OAuthResult.Error("No authorization code received from Google.")
        }

        if (state == null || receivedState == null ||
            !java.security.MessageDigest.isEqual(state.toByteArray(Charsets.UTF_8), receivedState.toByteArray(Charsets.UTF_8))
        ) {
            return@withContext OAuthResult.Error("Invalid OAuth state parameter. Possible CSRF attempt.")
        }

        exchangeAuthorizationCode(
            context = activity,
            code = authCode,
            codeVerifier = verifier,
            redirectUri = loopbackRedirectUri
        )
    }

    open fun stopLoopbackServer() {
        try {
            activeLoopbackServer?.close()
        } catch (_: Exception) {}
        activeLoopbackServer = null
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }

    /**
     * Builds a responsive, polished HTML landing page displayed in Chrome when the OAuth flow completes.
     */
    open fun buildResponseHtml(isSuccess: Boolean, error: String? = null): String {
        val title = if (isSuccess) "Connected to Money Manager" else "Authentication Failed"
        val icon = if (isSuccess) "&#9989;" else "&#9888;&#65039;"
        val safeError = error?.let { escapeHtml(it) }
        val message = if (isSuccess) {
            "Google authentication was successful! You can now return to the Money Manager app."
        } else {
            "Authentication could not be completed: ${safeError ?: "Unknown error"}. Please return to Money Manager and try again."
        }
        val buttonText = if (isSuccess) "Return to Money Manager" else "Back to App"
        val statusParam = if (isSuccess) "done" else "error"

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>$title</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        background-color: #0b0f19;
                        color: #f1f5f9;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                        padding: 24px 16px;
                    }
                    .card {
                        background-color: #1e293b;
                        border: 1px solid #334155;
                        border-radius: 24px;
                        padding: 36px 24px;
                        max-width: 420px;
                        width: 100%;
                        text-align: center;
                        box-shadow: 0 20px 40px rgba(0,0,0,0.5);
                    }
                    .icon { font-size: 48px; margin-bottom: 20px; line-height: 1; }
                    h1 { font-size: 22px; font-weight: 700; color: #38bdf8; margin-bottom: 12px; }
                    p { font-size: 15px; color: #94a3b8; line-height: 1.6; margin-bottom: 28px; }
                    .btn {
                        display: inline-block;
                        background-color: #2563eb;
                        color: #ffffff;
                        font-weight: 600;
                        font-size: 15px;
                        text-decoration: none;
                        padding: 14px 32px;
                        border-radius: 9999px;
                        box-shadow: 0 4px 12px rgba(37,99,235,0.3);
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="icon">$icon</div>
                    <h1>$title</h1>
                    <p>$message</p>
                    <a class="btn" href="com.moneymanager.app:/oauth2redirect?status=$statusParam">$buttonText</a>
                </div>
                <script>
                    setTimeout(function() {
                        window.location.href = "com.moneymanager.app:/oauth2redirect?status=$statusParam";
                    }, 800);
                </script>
            </body>
            </html>
        """.trimIndent()
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
        pendingRedirectUri = GmailAuthConfig.REDIRECT_URI

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

        // Verify CSRF state and exchange code with cleanup
        val expectedState = pendingState
        val verifier = pendingCodeVerifier
        val redirectUriToUse = pendingRedirectUri ?: GmailAuthConfig.REDIRECT_URI

        try {
            if (expectedState == null || state == null ||
                !java.security.MessageDigest.isEqual(expectedState.toByteArray(Charsets.UTF_8), state.toByteArray(Charsets.UTF_8))
            ) {
                return@withContext OAuthResult.Error("Invalid OAuth state parameter. Possible CSRF attempt.")
            }

            if (verifier == null) {
                return@withContext OAuthResult.Error("Missing PKCE code verifier. Please retry authentication.")
            }

            exchangeAuthorizationCode(context, code, verifier, redirectUriToUse)
        } finally {
            pendingCodeVerifier = null
            pendingState = null
            pendingRedirectUri = null
        }
    }

    /**
     * Exchanges auth code + PKCE verifier for access_token and refresh_token
     */
    open suspend fun exchangeAuthorizationCode(
        context: Context,
        code: String,
        codeVerifier: String,
        redirectUri: String = pendingRedirectUri ?: GmailAuthConfig.REDIRECT_URI
    ): OAuthResult = withContext(Dispatchers.IO) {
        try {
            val clientId = GmailAuthConfig.resolveClientId(context)
            val clientSecret = GmailAuthConfig.resolveClientSecret(context)

            val formBuilder = FormBody.Builder()
                .add("client_id", clientId)
                .add("code", code)
                .add("code_verifier", codeVerifier)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", redirectUri)

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
                pendingRedirectUri = null

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
