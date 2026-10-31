package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.local.entity.InsightSeverity
import com.moneymanager.app.data.local.entity.InsightType
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DuplicateDetectorTest {

    private val zone = ZoneId.systemDefault()

    @Test
    fun testDetect_flagsExactDuplicateChargesWithin10Minutes() {
        val baseTime = LocalDate.of(2026, 10, 15).atTime(14, 30).atZone(zone).toInstant().toEpochMilli()

        val tx1 = TransactionEntity(
            uid = "TX_1",
            type = TransactionType.EXPENSE,
            accountAmount = 450.0,
            baseAmount = 450.0,
            currencyUid = "CURRENCY_INR",
            accountUid = "ACC_HDFC",
            note = "Swiggy",
            timestamp = baseTime
        )

        val tx2 = TransactionEntity(
            uid = "TX_2",
            type = TransactionType.EXPENSE,
            accountAmount = 450.0,
            baseAmount = 450.0,
            currencyUid = "CURRENCY_INR",
            accountUid = "ACC_HDFC",
            note = "SWIGGY*ORDER 8812",
            timestamp = baseTime + (3 * 60 * 1000L) // 3 minutes later
        )

        val insights = DuplicateDetector.detect(
            transactions = listOf(tx1, tx2),
            config = DetectorConfig(duplicateExactWindowMinutes = 10, duplicateMerchantSimilarity = 0.80),
            zone = zone
        )

        assertEquals(1, insights.size)
        val dup = insights.first()
        assertEquals(InsightType.DUPLICATE, dup.type)
        assertEquals(InsightSeverity.HIGH, dup.severity)
        assertEquals(listOf("TX_1", "TX_2"), dup.transactionUids)
    }

    @Test
    fun testDetect_ignoresDifferentAccountsOrAmounts() {
        val baseTime = LocalDate.of(2026, 10, 15).atTime(14, 30).atZone(zone).toInstant().toEpochMilli()

        val tx1 = TransactionEntity(
            uid = "TX_1",
            type = TransactionType.EXPENSE,
            accountAmount = 450.0,
            baseAmount = 450.0,
            currencyUid = "CURRENCY_INR",
            accountUid = "ACC_HDFC",
            note = "Swiggy",
            timestamp = baseTime
        )

        // Different account
        val txDifferentAccount = tx1.copy(uid = "TX_2", accountUid = "ACC_ICICI", timestamp = baseTime + 60000)
        // Different amount
        val txDifferentAmount = tx1.copy(uid = "TX_3", accountAmount = 480.0, baseAmount = 480.0, timestamp = baseTime + 60000)

        val insightsAcc = DuplicateDetector.detect(
            transactions = listOf(tx1, txDifferentAccount),
            config = DetectorConfig(),
            zone = zone
        )
        assertTrue(insightsAcc.isEmpty())

        val insightsAmt = DuplicateDetector.detect(
            transactions = listOf(tx1, txDifferentAmount),
            config = DetectorConfig(),
            zone = zone
        )
        assertTrue(insightsAmt.isEmpty())
    }

    @Test
    fun testDetect_mergesTripleChargesIntoSingleCluster() {
        val baseTime = LocalDate.of(2026, 10, 15).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

        val tx1 = TransactionEntity(uid = "TX_A", type = TransactionType.EXPENSE, accountAmount = 199.0, baseAmount = 199.0, currencyUid = "INR", accountUid = "ACC_1", note = "Uber", timestamp = baseTime)
        val tx2 = TransactionEntity(uid = "TX_B", type = TransactionType.EXPENSE, accountAmount = 199.0, baseAmount = 199.0, currencyUid = "INR", accountUid = "ACC_1", note = "Uber", timestamp = baseTime + 120000)
        val tx3 = TransactionEntity(uid = "TX_C", type = TransactionType.EXPENSE, accountAmount = 199.0, baseAmount = 199.0, currencyUid = "INR", accountUid = "ACC_1", note = "Uber", timestamp = baseTime + 240000)

        val insights = DuplicateDetector.detect(
            transactions = listOf(tx1, tx2, tx3),
            config = DetectorConfig(),
            zone = zone
        )

        assertEquals(1, insights.size)
        assertEquals(3, insights.first().transactionUids.size)
    }

    @Test
    fun testDetect_doesNotFlagRecurringWeeklyOrMonthlyCharges() {
        val t1 = LocalDate.of(2026, 8, 1).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val t2 = LocalDate.of(2026, 9, 1).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

        val tx1 = TransactionEntity(uid = "TX_1", type = TransactionType.EXPENSE, accountAmount = 649.0, baseAmount = 649.0, currencyUid = "INR", accountUid = "ACC_1", note = "Netflix", timestamp = t1)
        val tx2 = TransactionEntity(uid = "TX_2", type = TransactionType.EXPENSE, accountAmount = 649.0, baseAmount = 649.0, currencyUid = "INR", accountUid = "ACC_1", note = "Netflix", timestamp = t2)

        val insights = DuplicateDetector.detect(
            transactions = listOf(tx1, tx2),
            config = DetectorConfig(),
            zone = zone
        )

        // Occurring a month apart: definitely not a duplicate!
        assertTrue(insights.isEmpty())
    }
}
