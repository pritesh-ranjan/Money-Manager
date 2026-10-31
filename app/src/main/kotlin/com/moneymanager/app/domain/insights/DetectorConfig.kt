package com.moneymanager.app.domain.insights

data class ConfidenceWeights(
    val intervalWeight: Double = 0.40,
    val amountWeight: Double = 0.25,
    val targetWeight: Double = 0.15,
    val historyWeight: Double = 0.20
)

data class DetectorConfig(
    val lookbackMonths: Int = 6,                         // Historical baseline span (3..6)
    val minBaselineSamples: Int = 5,                     // Min samples in segment for MAD
    val robustZThreshold: Double = 3.5,                  // Iglewicz-Hoaglin cutoff
    val largeTxMinAmount: Double = 500.0,                // Absolute floor to avoid flagging small variations
    val detectionWindowDays: Int = 30,                   // Recent window evaluated for anomalies
    val spikeThresholdPercent: Double = 50.0,            // Category spending spike %
    val spikeMinAbsoluteIncrease: Double = 1000.0,       // Min absolute increase for spike
    val spikeMinBaselineMonths: Int = 3,                 // Min history months required for spike
    val duplicateExactWindowMinutes: Int = 10,           // Time window for high-confidence duplicates
    val duplicateSameDayEnabled: Boolean = true,         // Consider same-day matches
    val duplicateMerchantSimilarity: Double = 0.85,      // Fuzzy string threshold
    val amountTolerance: Double = 0.01,                  // Floating point amount equality tolerance
    val recurrenceAmountVariationPercent: Double = 10.0, // Acceptable variation for subscriptions/SIPs
    val recurrenceMinConfidence: Double = 0.60,          // Cutoff to surface in UI
    val autoTagMinConfidence: Double = 0.80,              // Threshold for Approach A auto-tagging
    val autoTagEnabled: Boolean = true,                  // Master toggle for Approach A auto-tagging
    val forgottenMinCycles: Int = 3,                     // Unused cycles before forgotten flag
    val weights: ConfidenceWeights = ConfidenceWeights()
) {
    fun sanitized(): DetectorConfig {
        return copy(
            lookbackMonths = lookbackMonths.coerceIn(3, 6),
            minBaselineSamples = minBaselineSamples.coerceAtLeast(3),
            robustZThreshold = robustZThreshold.coerceIn(2.0, 10.0),
            largeTxMinAmount = largeTxMinAmount.coerceAtLeast(0.0),
            detectionWindowDays = detectionWindowDays.coerceIn(7, 90),
            spikeThresholdPercent = spikeThresholdPercent.coerceIn(10.0, 500.0),
            spikeMinAbsoluteIncrease = spikeMinAbsoluteIncrease.coerceAtLeast(0.0),
            spikeMinBaselineMonths = spikeMinBaselineMonths.coerceIn(1, 6),
            duplicateExactWindowMinutes = duplicateExactWindowMinutes.coerceIn(1, 120),
            duplicateMerchantSimilarity = duplicateMerchantSimilarity.coerceIn(0.5, 1.0),
            amountTolerance = amountTolerance.coerceAtLeast(0.0),
            recurrenceAmountVariationPercent = recurrenceAmountVariationPercent.coerceIn(0.0, 50.0),
            recurrenceMinConfidence = recurrenceMinConfidence.coerceIn(0.0, 1.0),
            autoTagMinConfidence = autoTagMinConfidence.coerceIn(0.5, 1.0),
            forgottenMinCycles = forgottenMinCycles.coerceAtLeast(2)
        )
    }
}
