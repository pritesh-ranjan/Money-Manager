package com.moneymanager.app.domain.gmail.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class GmailTokenStorageTest {

    private lateinit var context: Context
    private val memoryPrefs = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        memoryPrefs.clear()

        var editorProxy: SharedPreferences.Editor? = null
        editorProxy = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { _, editMethod, editArgs ->
            when (editMethod.name) {
                "putString" -> {
                    if (editArgs[1] != null) memoryPrefs[editArgs[0] as String] = editArgs[1]
                    else memoryPrefs.remove(editArgs[0] as String)
                    editorProxy
                }
                "putLong" -> {
                    memoryPrefs[editArgs[0] as String] = editArgs[1]
                    editorProxy
                }
                "remove" -> {
                    memoryPrefs.remove(editArgs[0] as String)
                    editorProxy
                }
                "clear" -> {
                    memoryPrefs.clear()
                    editorProxy
                }
                "apply", "commit" -> null
                else -> editorProxy
            }
        } as SharedPreferences.Editor

        val fakePrefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> memoryPrefs[args[0] as String] ?: (if (args.size > 1) args[1] else null)
                "getLong" -> memoryPrefs[args[0] as String] ?: (if (args.size > 1) args[1] else 0L)
                "getBoolean" -> memoryPrefs[args[0] as String] ?: (if (args.size > 1) args[1] else false)
                "edit" -> editorProxy
                else -> null
            }
        } as SharedPreferences

        context = object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = fakePrefs
        }
    }

    @Test
    fun testClientCredentialsAreEncryptedAndDecrypted() {
        val testClientId = "123456789-abcdef.apps.googleusercontent.com"
        val testClientSecret = "GOCSPX-SecretValue12345"

        // 1. Save credentials
        GmailTokenStorage.saveClientCredentials(context, testClientId, testClientSecret)

        // 2. Direct inspection of SharedPreferences to verify storage is encrypted and not in plaintext
        val rawStoredClientId = memoryPrefs["pref_client_id_enc"] as? String
        val rawStoredSecret = memoryPrefs["pref_client_secret_enc"] as? String

        assertNotNull("Encrypted client ID should exist in prefs", rawStoredClientId)
        assertNotNull("Encrypted client secret should exist in prefs", rawStoredSecret)

        // Stored values MUST NOT be plaintext
        assertNotEquals(testClientId, rawStoredClientId)
        assertNotEquals(testClientSecret, rawStoredSecret)

        // Legacy unencrypted keys must not exist
        assertNull(memoryPrefs["pref_client_id"])
        assertNull(memoryPrefs["pref_client_secret"])

        // 3. Decrypted getters must return original plaintexts
        assertEquals(testClientId, GmailTokenStorage.getClientId(context))
        assertEquals(testClientSecret, GmailTokenStorage.getClientSecret(context))
    }

    @Test
    fun testLegacyPlaintextMigration() {
        val legacyId = "legacy_client_id_999.apps.googleusercontent.com"
        val legacySecret = "legacy_secret_xyz"

        // Simulate legacy unencrypted entries stored in preferences
        memoryPrefs["pref_client_id"] = legacyId
        memoryPrefs["pref_client_secret"] = legacySecret

        // Reading should return the legacy values
        assertEquals(legacyId, GmailTokenStorage.getClientId(context))
        assertEquals(legacySecret, GmailTokenStorage.getClientSecret(context))

        // And verify they have been migrated to encrypted keys and legacy removed
        assertNotNull(memoryPrefs["pref_client_id_enc"])
        assertNotNull(memoryPrefs["pref_client_secret_enc"])
        assertNull(memoryPrefs["pref_client_id"])
        assertNull(memoryPrefs["pref_client_secret"])
    }

    @Test
    fun testOAuthTokensAreEncrypted() {
        val accessToken = "ya29.sample_access_token_123"
        val refreshToken = "1//sample_refresh_token_456"

        GmailTokenStorage.saveTokens(
            context = context,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresInSeconds = 3600L,
            email = "user@example.com"
        )

        // Stored raw values in prefs must be encrypted
        val rawAccess = memoryPrefs["pref_access_token_enc"] as? String
        val rawRefresh = memoryPrefs["pref_refresh_token_enc"] as? String

        assertNotNull(rawAccess)
        assertNotNull(rawRefresh)
        assertNotEquals(accessToken, rawAccess)
        assertNotEquals(refreshToken, rawRefresh)

        // Decrypted getters return original values
        assertEquals(accessToken, GmailTokenStorage.getAccessToken(context))
        assertEquals(refreshToken, GmailTokenStorage.getRefreshToken(context))
        assertEquals("user@example.com", GmailTokenStorage.getConnectedEmail(context))
        assertTrue(GmailTokenStorage.isConnected(context))
    }

    @Test
    fun testPdfPasswordsMultiListAddGetRemove() {
        // Initially empty
        assertTrue(GmailTokenStorage.getPdfPasswords(context).isEmpty())
        assertNull(GmailTokenStorage.getDefaultPdfPassword(context))

        // Add first password
        val added1 = GmailTokenStorage.addPdfPassword(context, "hdfc_pass_123")
        assertTrue(added1)
        assertEquals(listOf("hdfc_pass_123"), GmailTokenStorage.getPdfPasswords(context))
        assertEquals("hdfc_pass_123", GmailTokenStorage.getDefaultPdfPassword(context))

        // Add second password
        val added2 = GmailTokenStorage.addPdfPassword(context, "axis_pass_456")
        assertTrue(added2)
        assertEquals(listOf("hdfc_pass_123", "axis_pass_456"), GmailTokenStorage.getPdfPasswords(context))

        // Remove first password
        val removed1 = GmailTokenStorage.removePdfPassword(context, "hdfc_pass_123")
        assertTrue(removed1)
        assertEquals(listOf("axis_pass_456"), GmailTokenStorage.getPdfPasswords(context))
        assertEquals("axis_pass_456", GmailTokenStorage.getDefaultPdfPassword(context))
    }

    @Test
    fun testPdfPasswordsDeduplication() {
        GmailTokenStorage.addPdfPassword(context, "common_pass")
        val duplicateAdded = GmailTokenStorage.addPdfPassword(context, "common_pass")
        assertFalse("Duplicate password should not be added", duplicateAdded)
        assertEquals(1, GmailTokenStorage.getPdfPasswords(context).size)
    }

    @Test
    fun testHasCustomCredentialsAndClear() {
        assertFalse(GmailTokenStorage.hasCustomCredentials(context))

        GmailTokenStorage.saveClientCredentials(context, "my-client-id", "my-secret")
        assertTrue(GmailTokenStorage.hasCustomCredentials(context))

        GmailTokenStorage.clearClientCredentials(context)
        assertFalse(GmailTokenStorage.hasCustomCredentials(context))
        assertNull(GmailTokenStorage.getClientId(context))
        assertNull(GmailTokenStorage.getClientSecret(context))
    }

    @Test
    fun testLegacySinglePdfPasswordMigration() {
        // Simulate legacy single password saved under PREF_PDF_PASSWORD_ENC
        GmailTokenStorage.saveDefaultPdfPassword(context, "legacy_secret_password")

        // Reading multi-passwords list should return it
        val passwords = GmailTokenStorage.getPdfPasswords(context)
        assertEquals(listOf("legacy_secret_password"), passwords)
        assertEquals("legacy_secret_password", GmailTokenStorage.getDefaultPdfPassword(context))

        // Adding another password preserves the migrated one
        GmailTokenStorage.addPdfPassword(context, "new_bank_password")
        assertEquals(listOf("legacy_secret_password", "new_bank_password"), GmailTokenStorage.getPdfPasswords(context))
    }
}
