package com.moneymanager.app.domain

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom

class AppLockSecurityTest {

    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setup() {
        fakePrefs = FakeSharedPreferences()
        AppLockManager.init(fakePrefs)
        AppLockManager.timeProvider = { 1_000_000L }
    }

    @Test
    fun testPinFormatValidation_rejectsInvalidFormats() {
        // Valid PINs
        assertTrue(AppLockManager.isValidPinFormat("1234"))
        assertTrue(AppLockManager.isValidPinFormat("0000"))
        assertTrue(AppLockManager.isValidPinFormat("9876"))

        // Invalid PINs
        assertFalse(AppLockManager.isValidPinFormat(""))
        assertFalse(AppLockManager.isValidPinFormat("123"))
        assertFalse(AppLockManager.isValidPinFormat("12345"))
        assertFalse(AppLockManager.isValidPinFormat("12a4"))
        assertFalse(AppLockManager.isValidPinFormat("abcd"))
        assertFalse(AppLockManager.isValidPinFormat("1 34"))
        assertFalse(AppLockManager.isValidPinFormat(" 123"))
    }

    @Test
    fun testSaltedHash_differentSaltsProduceDifferentHashes() {
        val pin = "4321"
        val salt1 = ByteArray(16) { 1 }
        val salt2 = ByteArray(16) { 2 }

        val hash1 = AppLockManager.hashPinWithSalt(pin, salt1)
        val hash2 = AppLockManager.hashPinWithSalt(pin, salt2)

        // Hashes with different salts must never collide
        assertFalse(hash1 == hash2)

        // Hash with same salt must reproduce identically
        val hash1Again = AppLockManager.hashPinWithSalt(pin, salt1)
        assertEquals(hash1, hash1Again)
    }

    @Test
    fun testSetLockAndVerification_matchingPinUnlocks() {
        AppLockManager.setLock("1234")
        assertTrue(AppLockManager.isLockConfigured())

        // Lock the app
        AppLockManager.lock()
        assertTrue(AppLockManager.isLocked.value)

        // Wrong PIN fails and app stays locked
        assertFalse(AppLockManager.verifyPin("9999"))
        assertTrue(AppLockManager.isLocked.value)

        // Correct PIN succeeds and unlocks app
        assertTrue(AppLockManager.verifyPin("1234"))
        assertFalse(AppLockManager.isLocked.value)
    }

    @Test
    fun testSetLock_rejectsInvalidPin() {
        try {
            AppLockManager.setLock("12")
            fail("Expected IllegalArgumentException for 2-digit PIN")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("4 digits", ignoreCase = true))
        }

        try {
            AppLockManager.setLock("12345")
            fail("Expected IllegalArgumentException for 5-digit PIN")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("4 digits", ignoreCase = true))
        }
    }

    @Test
    fun testBruteForceRateLimiting_lockoutAfter5FailedAttempts() {
        var currentTime = 100_000L
        AppLockManager.timeProvider = { currentTime }
        AppLockManager.setLock("7890")

        // Fail 4 times -> not locked out yet
        for (i in 1..4) {
            assertFalse(AppLockManager.verifyPin("0000"))
            assertFalse(AppLockManager.isLockedOut(currentTime))
        }

        // 5th failed attempt triggers lockout
        assertFalse(AppLockManager.verifyPin("0000"))
        assertTrue(AppLockManager.isLockedOut(currentTime))
        assertEquals(30L, AppLockManager.remainingLockoutSeconds(currentTime))

        // While locked out, even the CORRECT PIN is rejected
        assertFalse(AppLockManager.verifyPin("7890"))

        // Fast-forward 15 seconds -> still locked out
        currentTime += 15_000L
        assertTrue(AppLockManager.isLockedOut(currentTime))
        assertFalse(AppLockManager.verifyPin("7890"))
        assertEquals(15L, AppLockManager.remainingLockoutSeconds(currentTime))

        // Fast-forward beyond 30-second lockout window (e.g. +16s = 31s total)
        currentTime += 16_000L
        assertFalse(AppLockManager.isLockedOut(currentTime))
        assertEquals(0L, AppLockManager.remainingLockoutSeconds(currentTime))

        // Correct PIN now succeeds
        assertTrue(AppLockManager.verifyPin("7890"))
        assertFalse(AppLockManager.isLocked.value)
    }

    @Test
    fun testTamperResilience_failClosedIfPreferencesCorrupted() {
        AppLockManager.setLock("5555")

        // Tamper with prefs by clearing the salt
        fakePrefs.edit().remove("key_pin_salt").apply()

        // App remains configured as locked
        assertTrue(AppLockManager.isLockConfigured())

        // PIN verification fails closed safely instead of crashing or unlocking
        assertFalse(AppLockManager.verifyPin("5555"))
    }

    @Test
    fun testOnAppBackgrounded_locksWhenConfigured() {
        AppLockManager.setLock("1234")
        assertFalse(AppLockManager.isLocked.value)

        AppLockManager.onAppBackgrounded()
        assertTrue(AppLockManager.isLocked.value)
    }

    @Test
    fun testExternalActivityGracePeriod_staysUnlockedWithinGrace() {
        var currentTime = 200_000L
        AppLockManager.timeProvider = { currentTime }
        AppLockManager.setLock("1234")
        assertFalse(AppLockManager.isLocked.value)

        // Launch external activity (e.g. file picker)
        AppLockManager.beginExternalActivity()

        // App goes to background
        AppLockManager.onAppBackgrounded()
        // Stays unlocked while external activity is in-flight
        assertFalse(AppLockManager.isLocked.value)

        // Return within 120s (e.g. 50s later)
        currentTime += 50_000L
        AppLockManager.onAppForegrounded()
        assertFalse(AppLockManager.isLocked.value)
    }

    @Test
    fun testExternalActivityGracePeriod_locksWhenExceedsGrace() {
        var currentTime = 200_000L
        AppLockManager.timeProvider = { currentTime }
        AppLockManager.setLock("1234")
        assertFalse(AppLockManager.isLocked.value)

        // Launch external activity
        AppLockManager.beginExternalActivity()
        AppLockManager.onAppBackgrounded()

        // Return after 125 seconds (exceeds 120_000ms grace)
        currentTime += 125_000L
        AppLockManager.onAppForegrounded()
        assertTrue(AppLockManager.isLocked.value)
    }

    @Test
    fun testBiometricAutoPrompt_consumedOncePerSession() {
        AppLockManager.setLock("1234", enableBiometrics = true)
        AppLockManager.lock()
        assertTrue(AppLockManager.isLocked.value)

        // First prompt check in foreground session returns true
        assertTrue(AppLockManager.consumeBiometricAutoPrompt())
        // Second check in same session returns false to avoid prompt loops
        assertFalse(AppLockManager.consumeBiometricAutoPrompt())

        // App backgrounded and returned resets session
        AppLockManager.onAppBackgrounded()
        assertTrue(AppLockManager.consumeBiometricAutoPrompt())

        // If biometrics disabled, returns false
        AppLockManager.setBiometricEnabled(false)
        AppLockManager.onAppBackgrounded()
        assertFalse(AppLockManager.consumeBiometricAutoPrompt())
    }

    @Test
    fun testSetLock_defaultsBiometricToTrueOnInitialSetup_preservesChoiceOnUpdate() {
        // Initial setup defaults biometrics to true
        AppLockManager.setLock("1234")
        assertTrue(AppLockManager.isBiometricEnabled())

        // User turns biometrics off
        AppLockManager.setBiometricEnabled(false)
        assertFalse(AppLockManager.isBiometricEnabled())

        // User changes PIN without specifying biometric parameter
        AppLockManager.setLock("5678")
        // Preserves user's disabled biometric preference
        assertFalse(AppLockManager.isBiometricEnabled())
    }
}

/**
 * In-memory SharedPreferences for local unit tests.
 */
class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor(data)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val backingMap: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val tempMap = mutableMapOf<String, Any?>()
        private val removeKeys = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) tempMap[key] = value
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) tempMap[key] = values
            return this
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) tempMap[key] = value
            return this
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) tempMap[key] = value
            return this
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) tempMap[key] = value
            return this
        }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) tempMap[key] = value
            return this
        }
        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) removeKeys.add(key)
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clearAll) backingMap.clear()
            removeKeys.forEach { backingMap.remove(it) }
            backingMap.putAll(tempMap)
        }
    }
}
