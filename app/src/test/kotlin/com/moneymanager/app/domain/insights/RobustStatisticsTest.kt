package com.moneymanager.app.domain.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RobustStatisticsTest {

    @Test
    fun testMedian_emptyAndOddEven() {
        assertNull(RobustStatistics.median(emptyList()))
        assertEquals(3.0, RobustStatistics.median(listOf(1.0, 3.0, 5.0))!!, 1e-9)
        assertEquals(3.5, RobustStatistics.median(listOf(1.0, 3.0, 4.0, 6.0))!!, 1e-9)
        assertEquals(42.0, RobustStatistics.median(listOf(42.0))!!, 1e-9)
    }

    @Test
    fun testMedian_ignoresNaNAndInfinity() {
        val sample = listOf(1.0, Double.NaN, 3.0, Double.POSITIVE_INFINITY, 5.0)
        assertEquals(3.0, RobustStatistics.median(sample)!!, 1e-9)
    }

    @Test
    fun testMad_referenceDataset() {
        // Sample: [1, 1, 2, 2, 4, 6, 9] -> Median = 2.0
        // Deviations from 2: [1, 1, 0, 0, 2, 4, 7]
        // Sorted deviations: [0, 0, 1, 1, 2, 4, 7] -> Median of deviations = 1.0
        val sample = listOf(1.0, 1.0, 2.0, 2.0, 4.0, 6.0, 9.0)
        val mad = RobustStatistics.mad(sample)
        assertNotNull(mad)
        assertEquals(1.0, mad!!, 1e-9)
    }

    @Test
    fun testRobustZ_standardCalculation() {
        // Baseline: median = 100, MAD = 10
        // Value: 150
        // Z = 0.6745 * (150 - 100) / 10 = 0.6745 * 5 = 3.3725
        val sample = listOf(80.0, 90.0, 100.0, 110.0, 120.0)
        val med = RobustStatistics.median(sample)!!
        val mad = RobustStatistics.mad(sample)!!
        assertEquals(100.0, med, 1e-9)
        assertEquals(10.0, mad, 1e-9)

        val z = RobustStatistics.robustZ(150.0, sample)
        assertNotNull(z)
        assertEquals(3.3725, z!!, 1e-4)
    }

    @Test
    fun testRobustZ_zeroMadMeanAdFallback() {
        // When >= 50% values are identical, MAD is 0
        // Sample: [100, 100, 100, 100, 150, 200, 250]
        val sample = listOf(100.0, 100.0, 100.0, 100.0, 150.0, 200.0, 250.0)
        val mad = RobustStatistics.mad(sample)!!
        assertEquals(0.0, mad, 1e-9)

        val z = RobustStatistics.robustZ(300.0, sample)
        assertNotNull(z)
        assertTrue("Z should be finite and positive using MeanAD fallback", z!! > 0 && z.isFinite())
    }

    @Test
    fun testRobustZ_identicalSample() {
        val sample = listOf(50.0, 50.0, 50.0, 50.0)
        assertEquals(0.0, RobustStatistics.robustZ(50.0, sample)!!, 1e-9)
        val zHigh = RobustStatistics.robustZ(75.0, sample)!!
        assertTrue(zHigh.isInfinite() && zHigh > 0)
        val zLow = RobustStatistics.robustZ(25.0, sample)!!
        assertTrue(zLow.isInfinite() && zLow < 0)
    }

    @Test
    fun testPercentageChange() {
        assertEquals(0.0, RobustStatistics.calculatePercentageChange(0.0, 0.0)!!, 1e-9)
        assertNull(RobustStatistics.calculatePercentageChange(100.0, 0.0))
        assertEquals(-100.0, RobustStatistics.calculatePercentageChange(0.0, 100.0)!!, 1e-9)
        assertEquals(50.0, RobustStatistics.calculatePercentageChange(150.0, 100.0)!!, 1e-9)
        assertEquals(-25.0, RobustStatistics.calculatePercentageChange(75.0, 100.0)!!, 1e-9)
    }
}
