package com.moneymanager.app.domain.insights

import kotlin.math.abs

object RobustStatistics {

    // Consistency factor for normal distribution: 1 / norm.ppf(0.75) ≈ 0.67448975
    const val MAD_CONSISTENCY = 0.6745

    // Asymptotic relative efficiency factor for Mean Absolute Deviation: sqrt(pi / 2) ≈ 1.253314
    private const val MEAN_AD_FACTOR = 1.253314

    /**
     * Computes the sample median.
     * Returns null if the list is empty or contains no finite numbers.
     */
    fun median(values: List<Double>): Double? {
        val clean = values.filter { it.isFinite() }
        if (clean.isEmpty()) return null
        val sorted = clean.sorted()
        val n = sorted.size
        return if (n % 2 == 1) {
            sorted[n / 2]
        } else {
            (sorted[(n / 2) - 1] + sorted[n / 2]) / 2.0
        }
    }

    /**
     * Computes the Median Absolute Deviation: median(|x_i - median(X)|).
     * Returns null if the list is empty or contains no finite numbers.
     */
    fun mad(values: List<Double>): Double? {
        val med = median(values) ?: return null
        val absoluteDeviations = values.filter { it.isFinite() }.map { abs(it - med) }
        return median(absoluteDeviations)
    }

    /**
     * Computes the robust Z-score for value [x] with respect to sample [values]:
     * Z = 0.6745 * (x - median) / MAD
     *
     * Fallback handling:
     * - When MAD is 0 (due to >= 50% identical values), uses the Mean Absolute Deviation:
     *   Z = (x - median) / (1.253314 * MeanAD)
     * - If MeanAD is also 0 (all samples identical):
     *   returns 0.0 if x == median, else ±POSITIVE_INFINITY.
     */
    fun robustZ(x: Double, values: List<Double>): Double? {
        if (!x.isFinite()) return null
        val clean = values.filter { it.isFinite() }
        if (clean.isEmpty()) return null

        val med = median(clean) ?: return null
        val madVal = mad(clean) ?: return null

        val diff = x - med
        if (abs(diff) < 1e-12) return 0.0

        if (madVal > 1e-9) {
            return (MAD_CONSISTENCY * diff) / madVal
        }

        // Fallback to Mean Absolute Deviation (MeanAD)
        val meanAd = clean.map { abs(it - med) }.average()
        if (meanAd > 1e-9) {
            return diff / (MEAN_AD_FACTOR * meanAd)
        }

        // Zero variance: all baseline values are identical
        return if (diff > 0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
    }

    /**
     * Calculates percentage change: ((current - previous) / previous) * 100.
     * Safely handles zero baselines:
     * - Returns 0.0 if both are 0.0
     * - Returns null if previous is 0.0 (prevents misleading Infinity%)
     * - Returns -100.0 if previous > 0.0 and current is 0.0
     */
    fun calculatePercentageChange(current: Double, previous: Double): Double? {
        if (previous == 0.0 && current == 0.0) return 0.0
        if (previous == 0.0) return null
        if (current == 0.0) return -100.0
        return ((current - previous) / previous) * 100.0
    }
}
