package com.moneymanager.app.domain

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AppLockManager {

    private const val PREFS_NAME = "money_manager_security_prefs"
    private const val KEY_LOCK_ENABLED = "key_app_lock_enabled"
    private const val KEY_PIN_HASH = "key_pin_hash"
    private const val KEY_PIN_SALT = "key_pin_salt"
    private const val KEY_BIOMETRIC_ENABLED = "key_biometric_enabled"
    private const val KEY_FAILED_ATTEMPTS = "key_failed_attempts"
    private const val KEY_LOCKOUT_UNTIL = "key_lockout_until"

    const val MAX_FAILED_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MS = 30_000L
    const val EXTERNAL_ACTIVITY_GRACE_MS = 120_000L
    private const val HASH_ITERATIONS = 10_000
    private const val HASH_KEY_LENGTH = 256

    private lateinit var prefs: SharedPreferences

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var cancellationSignal: CancellationSignal? = null
    private var externalActivityLaunchedAt = 0L
    private var biometricAutoPromptConsumed = false

    // Injectable clock for deterministic security testing
    var timeProvider: () -> Long = { System.currentTimeMillis() }

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        init(context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }

    fun init(sharedPreferences: SharedPreferences) {
        prefs = sharedPreferences
        externalActivityLaunchedAt = 0L
        biometricAutoPromptConsumed = false
        if (isLockConfigured()) {
            _isLocked.value = true
        }
    }

    fun isLockConfigured(): Boolean {
        if (!::prefs.isInitialized) return false
        return prefs.getBoolean(KEY_LOCK_ENABLED, false)
    }

    fun isBiometricEnabled(): Boolean {
        if (!::prefs.isInitialized) return false
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun isValidPinFormat(pin: String): Boolean {
        return pin.length == 4 && pin.all { it.isDigit() }
    }

    fun setLock(
        pin: String,
        enableBiometrics: Boolean = if (isLockConfigured()) isBiometricEnabled() else true
    ) {
        require(isValidPinFormat(pin)) { "PIN must be exactly 4 digits" }

        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val saltHex = saltBytes.joinToString("") { "%02x".format(it) }
        val hash = hashPinWithSalt(pin, saltBytes)

        prefs.edit()
            .putBoolean(KEY_LOCK_ENABLED, true)
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, saltHex)
            .putBoolean(KEY_BIOMETRIC_ENABLED, enableBiometrics)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()

        _isLocked.value = false
        biometricAutoPromptConsumed = false
    }

    fun disableLock() {
        prefs.edit()
            .putBoolean(KEY_LOCK_ENABLED, false)
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()

        _isLocked.value = false
        biometricAutoPromptConsumed = false
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isLockedOut(now: Long = timeProvider()): Boolean {
        if (!::prefs.isInitialized) return false
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        return now < lockoutUntil
    }

    fun remainingLockoutSeconds(now: Long = timeProvider()): Long {
        if (!::prefs.isInitialized) return 0L
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val diff = lockoutUntil - now
        return if (diff > 0) (diff + 999) / 1000 else 0L
    }

    fun verifyPin(pin: String): Boolean {
        if (!::prefs.isInitialized) return false

        val now = timeProvider()
        if (isLockedOut(now)) {
            return false
        }

        if (!isValidPinFormat(pin)) {
            recordFailedAttempt(now)
            return false
        }

        val storedHash = prefs.getString(KEY_PIN_HASH, null)
        val storedSaltHex = prefs.getString(KEY_PIN_SALT, null)

        // Fail-closed if configured lock data is missing or corrupted
        if (storedHash.isNullOrEmpty() || storedSaltHex.isNullOrEmpty()) {
            return false
        }

        val saltBytes = try {
            storedSaltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (_: Exception) {
            return false
        }

        val enteredHash = hashPinWithSalt(pin, saltBytes)
        val matches = MessageDigest.isEqual(storedHash.toByteArray(Charsets.UTF_8), enteredHash.toByteArray(Charsets.UTF_8))

        if (matches) {
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply()
            _isLocked.value = false
            return true
        } else {
            recordFailedAttempt(now)
            return false
        }
    }

    private fun recordFailedAttempt(now: Long) {
        val currentFailed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = prefs.edit()
        if (currentFailed >= MAX_FAILED_ATTEMPTS) {
            editor.putInt(KEY_FAILED_ATTEMPTS, 0)
            editor.putLong(KEY_LOCKOUT_UNTIL, now + LOCKOUT_DURATION_MS)
        } else {
            editor.putInt(KEY_FAILED_ATTEMPTS, currentFailed)
        }
        editor.apply()
    }

    fun unlock() {
        _isLocked.value = false
        biometricAutoPromptConsumed = false
    }

    fun lock() {
        if (isLockConfigured()) {
            _isLocked.value = true
        }
    }

    /** Call right before launching a system activity/picker (e.g. Storage Access Framework) from inside the app */
    fun beginExternalActivity() {
        externalActivityLaunchedAt = timeProvider()
    }

    fun onAppBackgrounded() {
        biometricAutoPromptConsumed = false
        if (externalActivityLaunchedAt == 0L) {
            lock()
        }
    }

    fun onAppForegrounded() {
        if (externalActivityLaunchedAt != 0L) {
            val elapsed = timeProvider() - externalActivityLaunchedAt
            if (elapsed > EXTERNAL_ACTIVITY_GRACE_MS) {
                lock()
            }
            externalActivityLaunchedAt = 0L
        }
    }

    fun consumeBiometricAutoPrompt(): Boolean {
        if (!_isLocked.value || !isBiometricEnabled() || !isLockConfigured() || biometricAutoPromptConsumed) {
            return false
        }
        biometricAutoPromptConsumed = true
        return true
    }

    fun promptBiometric(activity: Activity, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isBiometricEnabled() && isLockConfigured()) {
            try {
                cancellationSignal?.cancel()
                cancellationSignal = CancellationSignal()

                val prompt = BiometricPrompt.Builder(activity)
                    .setTitle("Unlock Money Manager")
                    .setSubtitle("Use biometric authentication to unlock")
                    .setNegativeButton("Use PIN", activity.mainExecutor) { _, _ ->
                        onResult(false)
                    }
                    .build()

                prompt.authenticate(
                    cancellationSignal!!,
                    activity.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            unlock()
                            onResult(true)
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            onResult(false)
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            onResult(false)
                        }
                    }
                )
            } catch (_: Exception) {
                onResult(false)
            }
        } else {
            onResult(false)
        }
    }

    fun cancelBiometric() {
        cancellationSignal?.cancel()
        cancellationSignal = null
    }

    fun hashPinWithSalt(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, HASH_ITERATIONS, HASH_KEY_LENGTH)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashBytes = skf.generateSecret(spec).encoded
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
