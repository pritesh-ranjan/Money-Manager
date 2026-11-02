package com.moneymanager.app.domain.gmail.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object GmailTokenStorage {

    private const val PREFS_NAME = "gmail_secure_auth_prefs"
    private const val KEY_ALIAS = "MoneyManagerGmailAuthKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private const val PREF_ACCESS_TOKEN_ENC = "pref_access_token_enc"
    private const val PREF_REFRESH_TOKEN_ENC = "pref_refresh_token_enc"
    private const val PREF_TOKEN_EXPIRES_AT = "pref_token_expires_at"
    private const val PREF_CONNECTED_EMAIL = "pref_connected_email"
    private const val PREF_CLIENT_ID = "pref_client_id"
    private const val PREF_CLIENT_SECRET = "pref_client_secret"
    private const val PREF_PDF_PASSWORD_ENC = "pref_pdf_password_enc"

    // Fallback key in memory/unit tests
    private var testSecretKey: SecretKey? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        testSecretKey?.let { return it }

        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGen = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
                val keyGenParameterSpec = android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(keyGenParameterSpec)
                keyGen.generateKey()
            } else {
                keyStore.getKey(KEY_ALIAS, null) as SecretKey
            }
        } catch (_: Exception) {
            // JVM Unit test fallback without AndroidKeyStore
            val fallback = testSecretKey ?: run {
                val keyBytes = ByteArray(32) { (it * 7).toByte() }
                SecretKeySpec(keyBytes, "AES").also { testSecretKey = it }
            }
            fallback
        }
    }

    private fun encrypt(plainText: String?): String? {
        if (plainText == null) return null
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            java.util.Base64.getEncoder().encodeToString(combined)
        } catch (_: Exception) {
            null
        }
    }

    private fun decrypt(encryptedBase64: String?): String? {
        if (encryptedBase64 == null) return null
        return try {
            val combined = java.util.Base64.getDecoder().decode(encryptedBase64)
            if (combined.size < GCM_IV_LENGTH) return null

            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun saveTokens(
        context: Context,
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long,
        email: String? = null
    ) {
        val expiryTime = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        val editor = getPrefs(context).edit()
            .putString(PREF_ACCESS_TOKEN_ENC, encrypt(accessToken))
            .putLong(PREF_TOKEN_EXPIRES_AT, expiryTime)

        if (refreshToken != null) {
            editor.putString(PREF_REFRESH_TOKEN_ENC, encrypt(refreshToken))
        }
        if (email != null) {
            editor.putString(PREF_CONNECTED_EMAIL, email)
        }
        editor.apply()
    }

    fun getAccessToken(context: Context): String? {
        val enc = getPrefs(context).getString(PREF_ACCESS_TOKEN_ENC, null)
        return decrypt(enc)
    }

    fun getRefreshToken(context: Context): String? {
        val enc = getPrefs(context).getString(PREF_REFRESH_TOKEN_ENC, null)
        return decrypt(enc)
    }

    fun getTokenExpiresAt(context: Context): Long {
        return getPrefs(context).getLong(PREF_TOKEN_EXPIRES_AT, 0L)
    }

    fun isAccessTokenExpired(context: Context, bufferSeconds: Long = 300): Boolean {
        val expiresAt = getTokenExpiresAt(context)
        return System.currentTimeMillis() >= (expiresAt - bufferSeconds * 1000L)
    }

    fun getConnectedEmail(context: Context): String? {
        return getPrefs(context).getString(PREF_CONNECTED_EMAIL, null)
    }

    fun setConnectedEmail(context: Context, email: String) {
        getPrefs(context).edit().putString(PREF_CONNECTED_EMAIL, email).apply()
    }

    fun isConnected(context: Context): Boolean {
        return getRefreshToken(context) != null
    }

    fun saveClientCredentials(context: Context, clientId: String?, clientSecret: String?) {
        getPrefs(context).edit()
            .putString(PREF_CLIENT_ID, clientId?.trim())
            .putString(PREF_CLIENT_SECRET, clientSecret?.trim())
            .apply()
    }

    fun getClientId(context: Context): String? {
        return getPrefs(context).getString(PREF_CLIENT_ID, null)
    }

    fun getClientSecret(context: Context): String? {
        return getPrefs(context).getString(PREF_CLIENT_SECRET, null)
    }

    fun saveDefaultPdfPassword(context: Context, password: String?) {
        getPrefs(context).edit()
            .putString(PREF_PDF_PASSWORD_ENC, encrypt(password))
            .apply()
    }

    fun getDefaultPdfPassword(context: Context): String? {
        val enc = getPrefs(context).getString(PREF_PDF_PASSWORD_ENC, null)
        return decrypt(enc)
    }

    fun clearAll(context: Context) {
        getPrefs(context).edit()
            .remove(PREF_ACCESS_TOKEN_ENC)
            .remove(PREF_REFRESH_TOKEN_ENC)
            .remove(PREF_TOKEN_EXPIRES_AT)
            .remove(PREF_CONNECTED_EMAIL)
            .apply()
    }
}
