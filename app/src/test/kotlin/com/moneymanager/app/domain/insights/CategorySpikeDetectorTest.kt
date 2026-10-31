package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryType
import com.moneymanager.app.data.local.entity.InsightType
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CategorySpikeDetectorTest {

    private val zone = ZoneId.systemDefault()

    private val shoppingCat = CategoryEntity(
        uid = "CAT_SHOPPING",
        name = "Shopping",
        type = CategoryType.EXPENSE
    )
    private val categoriesMap = mapOf(shoppingCat.uid to shoppingCat)

    @Test
    fun testDetect_flagsCategorySpikeAboveThreshold() {
        val refDate = LocalDate.of(2026, 10, 15) // Day 15 of October

        // Baseline: Months 7, 8, 9 (July, August, September).
        // In days 1..15 of each baseline month, user spent ~₹2,000 on Shopping.
        val txs = mutableListOf<TransactionEntity>()

        // July 1..15: ₹2,000
        txs.add(createTx(2000.0, LocalDate.of(2026, 7, 5)))
        // Aug 1..15: ₹2,200
        txs.add(createTx(2200.0, LocalDate.of(2026, 8, 8)))
        // Sep 1..15: ₹1,800
        txs.add(createTx(1800.0, LocalDate.of(2026, 9, 10)))
        // Baseline avg = ₹2,000

        // Current month (October 1..15): ₹6,000 (Spike of +200%, diff = +₹4,000)
        txs.add(createTx(6000.0, LocalDate.of(2026, 10, 12)))

        val config = DetectorConfig(
            lookbackMonths = 3,
            spikeThresholdPercent = 50.0,
            spikeMinAbsoluteIncrease = 1000.0,
            spikeMinBaselineMonths = 3
        )

        val insights = CategorySpikeDetector.detect(
            allTransactions = txs,
            categoriesMap = categoriesMap,
            referenceDate = refDate,
            config = config,
            zone = zone
        )

        assertEquals(1, insights.size)
        val spike = insights.first()
        assertEquals(InsightType.CATEGORY_SPIKE, spike.type)
        assertEquals("CAT_SHOPPING", spike.categoryUid)
        assertEquals(6000.0, spike.amount!!, 1e-9)
        assertEquals(2000.0, spike.historicalAverage!!, 1e-9)
        assertEquals(200.0, spike.percentChange!!, 1e-9)
    }

    @Test
    fun testDetect_ignoresWhenBelowPercentageOrAbsoluteThreshold() {
        val refDate = LocalDate.of(2026, 10, 15)
        val txs = mutableListOf<TransactionEntity>()

        // Baseline avg = ₹2,000
        txs.add(createTx(2000.0, LocalDate.of(2026, 7, 5)))
        txs.add(createTx(2000.0, LocalDate.of(2026, 8, 8)))
        txs.add(createTx(2000.0, LocalDate.of(2026, 9, 10)))

        // Current = ₹2,400 (Only +20%, below 50% threshold)
        txs.add(createTx(2400.0, LocalDate.of(2026, 10, 12)))

        val config = DetectorConfig(
            lookbackMonths = 3,
            spikeThresholdPercent = 50.0,
            spikeMinAbsoluteIncrease = 1000.0,
            spikeMinBaselineMonths = 3
        )

        val insights = CategorySpikeDetector.detect(
            allTransactions = txs,
            categoriesMap = categoriesMap,
            referenceDate = refDate,
            config = config,
            zone = zone
        )

        assertTrue(insights.isEmpty())
    }

    private fun createTx(amount: Double, date: LocalDate): TransactionEntity {
        return TransactionEntity(
            uid = java.util.UUID.randomUUID().toString(),
            type = TransactionType.EXPENSE,
            accountAmount = amount,
            baseAmount = amount,
            currencyUid = "CURRENCY_INR",
            accountUid = "ACC_1",
            categoryUid = "CAT_SHOPPING",
            note = "Store",
            timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }
}
