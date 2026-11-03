package com.moneymanager.app.domain.gmail.auth

import org.json.JSONObject

data class ParsedOAuthCredentials(
    val clientId: String,
    val clientSecret: String?
)

object GoogleOAuthCredentialsParser {

    /**
     * Parses Google OAuth 2.0 client credentials JSON exported from Google Cloud Console.
     * Supports:
     * - Desktop/Installed app structure: { "installed": { "client_id": "...", "client_secret": "..." } }
     * - Web app structure: { "web": { "client_id": "...", "client_secret": "..." } }
     * - Flat JSON: { "client_id": "...", "client_secret": "..." }
     *
     * @throws IllegalArgumentException if JSON is malformed or missing required client_id.
     */
    @Throws(IllegalArgumentException::class)
    fun parse(jsonContent: String): ParsedOAuthCredentials {
        val trimmed = jsonContent.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Credentials file is empty.")
        }

        val json = try {
            JSONObject(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON file: ${e.message}")
        }

        // Google Cloud Console nests client details inside "installed" or "web"
        val targetObj = when {
            json.has("installed") -> json.getJSONObject("installed")
            json.has("web") -> json.getJSONObject("web")
            else -> json
        }

        val clientId = targetObj.optString("client_id", "").trim()
        if (clientId.isEmpty()) {
            throw IllegalArgumentException("Missing 'client_id' in credentials file.")
        }

        val clientSecret = targetObj.optString("client_secret", "").trim().takeIf { it.isNotEmpty() }

        return ParsedOAuthCredentials(
            clientId = clientId,
            clientSecret = clientSecret
        )
    }
}
