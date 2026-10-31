package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryType
import com.moneymanager.app.data.local.entity.InsightSeverity
import com.moneymanager.app.data.local.entity.InsightType
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LargeTransactionDetectorTest {

    private val zone = ZoneId.systemDefault()

    private val diningCategory = CategoryEntity(
        uid = "CAT_DINING",
        name = "Dining",
        type = CategoryType.EXPENSE
    )
    private val categoriesMap = mapOf(diningCategory.uid to diningCategory)

    @Test
    fun testDetect_flagsOutlierExceedingZThreshold() {
        // Baseline: typical dining is ₹500 (amounts: 450, 480, 500, 520, 550) -> median 500, MAD 30
        val baselineAmounts = listOf(450.0, 480.0, 500.0, 520.0, 550.0)
        val categoryBaselines = mapOf(
            "CAT_DINING" to SpendingBaseline(
                segmentKey = "CAT_DINING",
                sampleCount = 5,
                monthsCovered = 3,
                median = 500.0,
                mad = 30.0,
                mean = 500.0,
                monthlyAverage = 833.0,
                monthlyTxnFrequency = 1.6,
                amounts = baselineAmounts,
                hasSufficientHistory = true
            )
        )

        // Candidate: ₹3,500 dining bill
        // Z = 0.6745 * (3500 - 500) / 30 = 0.6745 * 100 = 67.45 (>> 3.5)
        val candidate = TransactionEntity(
            uid = "TX_OUTLIER",
            type = TransactionType.EXPENSE,
            accountAmount = 3500.0,
            baseAmount = 3500.0,
            currencyUid = "CURRENCY_INR",
            accountUid = "ACC_1",
            categoryUid = "CAT_DINING",
            note = "Luxury Dinner",
            timestamp = System.currentTimeMillis()
        )

        val insights = LargeTransactionDetector.detect(
            transactions = listOf(candidate),
            merchantBaselines = emptyMap(),
            categoryBaselines = categoryBaselines,
            categoriesMap = categoriesMap,
            config = DetectorConfig(robustZThreshold = 3.5, largeTxMinAmount = 500.0)
        )

        assertEquals(1, insights.size)
        val insight = insights.first()
        assertEquals(InsightType.LARGE_TRANSACTION, insight.type)
        assertEquals(InsightSeverity.HIGH, insight.severity)
        assertEquals(3500.0, insight.amount!!, 1e-9)
        assertEquals(500.0, insight.historicalMedian!!, 1e-9)
        assertTrue(insight.robustZ!! > 3.5)
    }

    @Test
    fun testDetect_ignoresNormalTransaction() {
        val categoryBaselines = mapOf(
            "CAT_DINING" to SpendingBaseline(
                segmentKey = "CAT_DINING",
                sampleCount = 5,
                monthsCovered = 3,
                median = 500.0,
                mad = 30.0,
                mean = 500.0,
                monthlyAverage = 833.0,
                monthlyTxnFrequency = 1.6,
                amounts = listOf(450.0, 480.0, 500.0, 520.0, 550.0),
                hasSufficientHistory = true
            )
        )

        val normalTx = TransactionEntity(
            uid = "TX_NORMAL",
            type = TransactionType.EXPENSE,
            accountAmount = 530.0,
            baseAmount = 530.0,
            currencyUid = "CURRENCY_INR",
            accountUid = "ACC_1",
            categoryUid = "CAT_DINING",
            note = "Cafe Coffee Day",
            timestamp = System.currentTimeMillis()
        )

        val insights = LargeTransactionDetector.detect(
            transactions = listOf(normalTx),
            merchantBaselines = emptyMap(),
            categoryBaselines = categoryBaselines,
            categoriesMap = categoriesMap,
            config = DetectorConfig(robustZThreshold = 3.5)
        )

        assertTrue(insights.isEmpty())
    }

    @Test
    fun testDetect_skipsWhenInsufficientHistory() {
        val categoryBaselines = mapOf(
            "CAT_DINING" to SpendingBaseline(
                segmentKey = "CAT_DINING",
                sampleCount = 2,
                monthsCovered = 1,
                median = 500.0,
                mad = 30.0,
                mean = 500.0,
                monthlyAverage = 500.0,
                monthlyTxnFrequency = 2.0,
                amounts = listOf(480.0, 520.0),
                hasSufficientHistory = false // Not enough history
            )
        )

        val largeTx = TransactionEntity(
            uid = "TX_LARGE",
            type = TransactionType.EXPENSE,
            accountAmount = 5000.0,
            baseAmount = 5000.0,
            currencyUid = "CURRENCY_INR",
            accountUid = "ACC_1",
            categoryUid = "CAT_DINING",
            note = "Big Dinner",
            timestamp = System.currentTimeMillis()
        )

        val insights = LargeTransactionDetector.detect(
            transactions = listOf(largeTx),
            merchantBaselines = emptyMap(),
            categoryBaselines = categoryBaselines,
            categoriesMap = categoriesMap,
            config = DetectorConfig()
        )

        // Must skip to avoid false positive
        assertTrue(insights.isEmpty())
    }
}
