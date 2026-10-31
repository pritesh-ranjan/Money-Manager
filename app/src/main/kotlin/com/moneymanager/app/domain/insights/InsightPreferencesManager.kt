package com.moneymanager.app.domain.insights

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object InsightPreferencesManager {
    private const val PREFS_NAME = "money_manager_insights_prefs"

    private const val KEY_LOOKBACK_MONTHS = "key_lookback_months"
    private const val KEY_ROBUST_Z_THRESHOLD = "key_robust_z_threshold"
    private const val KEY_SPIKE_PERCENT = "key_spike_percent"
    private const val KEY_SPIKE_MIN_AMOUNT = "key_spike_min_amount"
    private const val KEY_RECURRENCE_VARIATION = "key_recurrence_variation"
    private const val KEY_DUPLICATE_WINDOW = "key_duplicate_window"
    private const val KEY_AUTO_TAG_ENABLED = "key_auto_tag_enabled"

    private lateinit var prefs: SharedPreferences

    private val _config = MutableStateFlow(DetectorConfig())
    val config: StateFlow<DetectorConfig> = _config.asStateFlow()

    fun init(context: Context) {
        init(context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }

    fun init(sharedPreferences: SharedPreferences) {
        prefs = sharedPreferences
        loadConfig()
    }

    private fun loadConfig() {
        if (!::prefs.isInitialized) return
        val default = DetectorConfig()

        val loaded = DetectorConfig(
            lookbackMonths = prefs.getInt(KEY_LOOKBACK_MONTHS, default.lookbackMonths),
            robustZThreshold = prefs.getFloat(KEY_ROBUST_Z_THRESHOLD, default.robustZThreshold.toFloat()).toDouble(),
            spikeThresholdPercent = prefs.getFloat(KEY_SPIKE_PERCENT, default.spikeThresholdPercent.toFloat()).toDouble(),
            spikeMinAbsoluteIncrease = prefs.getFloat(KEY_SPIKE_MIN_AMOUNT, default.spikeMinAbsoluteIncrease.toFloat()).toDouble(),
            recurrenceAmountVariationPercent = prefs.getFloat(KEY_RECURRENCE_VARIATION, default.recurrenceAmountVariationPercent.toFloat()).toDouble(),
            duplicateExactWindowMinutes = prefs.getInt(KEY_DUPLICATE_WINDOW, default.duplicateExactWindowMinutes),
            autoTagEnabled = prefs.getBoolean(KEY_AUTO_TAG_ENABLED, default.autoTagEnabled)
        ).sanitized()

        _config.value = loaded
    }

    fun updateConfig(newConfig: DetectorConfig) {
        val sanitized = newConfig.sanitized()
        _config.value = sanitized
        if (::prefs.isInitialized) {
            prefs.edit()
                .putInt(KEY_LOOKBACK_MONTHS, sanitized.lookbackMonths)
                .putFloat(KEY_ROBUST_Z_THRESHOLD, sanitized.robustZThreshold.toFloat())
                .putFloat(KEY_SPIKE_PERCENT, sanitized.spikeThresholdPercent.toFloat())
                .putFloat(KEY_SPIKE_MIN_AMOUNT, sanitized.spikeMinAbsoluteIncrease.toFloat())
                .putFloat(KEY_RECURRENCE_VARIATION, sanitized.recurrenceAmountVariationPercent.toFloat())
                .putInt(KEY_DUPLICATE_WINDOW, sanitized.duplicateExactWindowMinutes)
                .putBoolean(KEY_AUTO_TAG_ENABLED, sanitized.autoTagEnabled)
                .apply()
        }
    }
}
