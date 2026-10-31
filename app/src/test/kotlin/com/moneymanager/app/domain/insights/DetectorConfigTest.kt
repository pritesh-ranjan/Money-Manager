package com.moneymanager.app.domain.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorConfigTest {

    @Test
    fun testSanitized_clampsToValidRanges() {
        val extremeConfig = DetectorConfig(
            lookbackMonths = 10,                 // max 6
            minBaselineSamples = 1,              // min 3
            robustZThreshold = 1.0,              // min 2.0
            largeTxMinAmount = -100.0,           // min 0.0
            detectionWindowDays = 200,           // max 90
            spikeThresholdPercent = 5.0,         // min 10.0
            spikeMinAbsoluteIncrease = -50.0,    // min 0.0
            spikeMinBaselineMonths = 10,         // max 6
            duplicateExactWindowMinutes = 0,     // min 1
            duplicateMerchantSimilarity = 0.2,   // min 0.5
            amountTolerance = -0.5,              // min 0.0
            recurrenceAmountVariationPercent = 80.0, // max 50.0
            recurrenceMinConfidence = -0.5,      // min 0.0
            autoTagMinConfidence = 0.3,          // min 0.5
            forgottenMinCycles = 1               // min 2
        )

        val clean = extremeConfig.sanitized()

        assertEquals(6, clean.lookbackMonths)
        assertEquals(3, clean.minBaselineSamples)
        assertEquals(2.0, clean.robustZThreshold, 1e-9)
        assertEquals(0.0, clean.largeTxMinAmount, 1e-9)
        assertEquals(90, clean.detectionWindowDays)
        assertEquals(10.0, clean.spikeThresholdPercent, 1e-9)
        assertEquals(0.0, clean.spikeMinAbsoluteIncrease, 1e-9)
        assertEquals(6, clean.spikeMinBaselineMonths)
        assertEquals(1, clean.duplicateExactWindowMinutes)
        assertEquals(0.5, clean.duplicateMerchantSimilarity, 1e-9)
        assertEquals(0.0, clean.amountTolerance, 1e-9)
        assertEquals(50.0, clean.recurrenceAmountVariationPercent, 1e-9)
        assertEquals(0.0, clean.recurrenceMinConfidence, 1e-9)
        assertEquals(0.5, clean.autoTagMinConfidence, 1e-9)
        assertEquals(2, clean.forgottenMinCycles)
    }

    @Test
    fun testDefaultConfig_hasValidDefaults() {
        val default = DetectorConfig()
        val sanitized = default.sanitized()

        assertEquals(default.lookbackMonths, sanitized.lookbackMonths)
        assertEquals(default.robustZThreshold, sanitized.robustZThreshold, 1e-9)
        assertEquals(default.spikeThresholdPercent, sanitized.spikeThresholdPercent, 1e-9)
        assertEquals(default.duplicateExactWindowMinutes, sanitized.duplicateExactWindowMinutes)
        assertTrue(sanitized.autoTagEnabled)
    }
}
