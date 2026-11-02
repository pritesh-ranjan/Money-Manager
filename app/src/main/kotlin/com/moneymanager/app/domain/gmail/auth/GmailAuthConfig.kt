package com.moneymanager.app.domain.gmail.auth

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.util.Properties

object GmailAuthConfig {

    const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"

    // Strictly gmail.readonly scope as mandated
    const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

    const val REDIRECT_URI = "com.moneymanager.app:/oauth2redirect"

    // Default placeholders - user can configure via in-app UI or properties
    const val DEFAULT_CLIENT_ID = "YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com"
    const val DEFAULT_CLIENT_SECRET = "YOUR_GOOGLE_CLIENT_SECRET"

    /**
     * Resolves the active OAuth credentials, checking:
     * 1. On-device encrypted storage (configured in-app by user)
     * 2. Local statement_credentials.properties or gradle properties file
     * 3. Default fallback
     */
    fun resolveClientId(context: Context): String {
        val userConfigured = GmailTokenStorage.getClientId(context)
        if (!userConfigured.isNullOrBlank()) return userConfigured

        val propsId = loadFromPropertiesFile(context, "GMAIL_CLIENT_ID")
        if (!propsId.isNullOrBlank()) return propsId

        return DEFAULT_CLIENT_ID
    }

    fun resolveClientSecret(context: Context): String {
        val userConfigured = GmailTokenStorage.getClientSecret(context)
        if (!userConfigured.isNullOrBlank()) return userConfigured

        val propsSecret = loadFromPropertiesFile(context, "GMAIL_CLIENT_SECRET")
        if (!propsSecret.isNullOrBlank()) return propsSecret

        return DEFAULT_CLIENT_SECRET
    }

    fun isConfigured(context: Context): Boolean {
        val clientId = resolveClientId(context)
        return clientId.isNotBlank() && clientId != DEFAULT_CLIENT_ID
    }

    private fun loadFromPropertiesFile(context: Context, key: String): String? {
        try {
            // Check app private files dir first
            val appFile = File(context.filesDir, "gmail_credentials.properties")
            if (appFile.exists()) {
                val props = Properties()
                FileInputStream(appFile).use { props.load(it) }
                val value = props.getProperty(key)
                if (!value.isNullOrBlank()) return value
            }
        } catch (_: Exception) {
            // ignore
        }
        return null
    }
}
