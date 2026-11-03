package com.moneymanager.app.domain.gmail

import com.moneymanager.app.domain.gmail.auth.GmailAuthConfig
import com.moneymanager.app.domain.gmail.auth.GmailOAuthManager
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

class GmailOAuthManagerTest {

    private val manager = GmailOAuthManager()

    @Test
    fun testPkceVerifierAndChallengeGeneration() {
        val verifier = manager.generateCodeVerifier()
        assertNotNull(verifier)
        assertTrue("Verifier should be sufficiently long for high entropy", verifier.length >= 43)

        val challenge = manager.generateCodeChallenge(verifier)
        assertNotNull(challenge)
        assertTrue("Challenge must not be empty", challenge.isNotBlank())
        assertNotEquals("Challenge must be hashed, not equal to verifier", verifier, challenge)

        // Deterministic check
        val challenge2 = manager.generateCodeChallenge(verifier)
        assertEquals("Same verifier must always generate the exact same challenge", challenge, challenge2)
    }

    @Test
    fun testBuildAuthorizationUrlContainsMandatoryParameters() {
        val verifier = manager.generateCodeVerifier()
        val challenge = manager.generateCodeChallenge(verifier)
        val state = "test_csrf_state_12345"
        val clientId = "test-client-123.apps.googleusercontent.com"

        val url = manager.buildAuthorizationUrl(clientId = clientId, state = state, codeChallenge = challenge)

        assertTrue("URL must point to Google OAuth endpoint", url.startsWith(GmailAuthConfig.AUTH_ENDPOINT))
        assertTrue("URL must specify response_type=code", url.contains("response_type=code"))
        assertTrue("URL must specify access_type=offline for refresh token", url.contains("access_type=offline"))
        assertTrue("URL must require prompt=consent for guaranteed refresh token", url.contains("prompt=consent"))
        assertTrue("URL must include code_challenge_method=S256", url.contains("code_challenge_method=S256"))
        assertTrue("URL must include client_id", url.contains("client_id=$clientId"))
        assertTrue("URL must include state", url.contains("state=$state"))
        assertTrue("URL must include code_challenge", url.contains("code_challenge=$challenge"))

        val decoded = URLDecoder.decode(url, "UTF-8")
        assertTrue("URL must contain strictly gmail.readonly scope", decoded.contains("scope=https://www.googleapis.com/auth/gmail.readonly"))
        assertTrue("URL must contain redirect_uri", decoded.contains("redirect_uri=com.moneymanager.app:/oauth2redirect"))
    }

    @Test
    fun testBuildAuthorizationUrlWithDynamicLoopbackUri() {
        val verifier = manager.generateCodeVerifier()
        val challenge = manager.generateCodeChallenge(verifier)
        val state = "test_state_xyz"
        val clientId = "desktop-client.apps.googleusercontent.com"
        val loopbackUri = "http://127.0.0.1:49152"

        val url = manager.buildAuthorizationUrl(
            clientId = clientId,
            state = state,
            codeChallenge = challenge,
            redirectUri = loopbackUri
        )

        val decoded = URLDecoder.decode(url, "UTF-8")
        assertTrue("URL must contain dynamic loopback redirect URI", decoded.contains("redirect_uri=$loopbackUri"))
    }

    @Test
    fun testGmailAuthConfigLoopbackUri() {
        assertEquals("http://127.0.0.1:8080", GmailAuthConfig.buildLoopbackRedirectUri(8080))
        assertEquals("http://127.0.0.1:54321", GmailAuthConfig.buildLoopbackRedirectUri(54321))
    }

    @Test
    fun testBuildResponseHtmlSuccessAndError() {
        val successHtml = manager.buildResponseHtml(isSuccess = true)
        assertTrue(successHtml.contains("Connected to Money Manager"))
        assertTrue(successHtml.contains("com.moneymanager.app:/oauth2redirect?status=done"))

        val errorHtml = manager.buildResponseHtml(isSuccess = false, error = "access_denied")
        assertTrue(errorHtml.contains("Authentication Failed"))
        assertTrue(errorHtml.contains("access_denied"))
        assertTrue(errorHtml.contains("com.moneymanager.app:/oauth2redirect?status=error"))
    }
}
