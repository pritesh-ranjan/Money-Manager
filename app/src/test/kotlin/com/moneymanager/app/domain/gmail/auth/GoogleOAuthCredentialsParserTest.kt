package com.moneymanager.app.domain.gmail.auth

import org.junit.Assert.*
import org.junit.Test

class GoogleOAuthCredentialsParserTest {

    @Test
    fun testParseInstalledAppCredentials() {
        val json = """
            {
              "installed": {
                "client_id": "123456789-abcdef.apps.googleusercontent.com",
                "project_id": "my-expense-project",
                "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                "token_uri": "https://oauth2.googleapis.com/token",
                "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
                "client_secret": "GOCSPX-secret12345",
                "redirect_uris": ["http://localhost"]
              }
            }
        """.trimIndent()

        val parsed = GoogleOAuthCredentialsParser.parse(json)
        assertEquals("123456789-abcdef.apps.googleusercontent.com", parsed.clientId)
        assertEquals("GOCSPX-secret12345", parsed.clientSecret)
    }

    @Test
    fun testParseWebAppCredentials() {
        val json = """
            {
              "web": {
                "client_id": "987654321-zyxwvu.apps.googleusercontent.com",
                "project_id": "web-finance-app",
                "client_secret": "GOCSPX-websecret999"
              }
            }
        """.trimIndent()

        val parsed = GoogleOAuthCredentialsParser.parse(json)
        assertEquals("987654321-zyxwvu.apps.googleusercontent.com", parsed.clientId)
        assertEquals("GOCSPX-websecret999", parsed.clientSecret)
    }

    @Test
    fun testParseFlatCredentialsWithoutSecret() {
        val json = """
            {
              "client_id": "installed-client-id-no-secret.apps.googleusercontent.com"
            }
        """.trimIndent()

        val parsed = GoogleOAuthCredentialsParser.parse(json)
        assertEquals("installed-client-id-no-secret.apps.googleusercontent.com", parsed.clientId)
        assertNull(parsed.clientSecret)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testParseEmptyThrowsException() {
        GoogleOAuthCredentialsParser.parse("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testParseInvalidJsonThrowsException() {
        GoogleOAuthCredentialsParser.parse("not a valid json")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testParseMissingClientIdThrowsException() {
        val json = """
            {
              "installed": {
                "project_id": "no-client-id-here"
              }
            }
        """.trimIndent()
        GoogleOAuthCredentialsParser.parse(json)
    }
}
