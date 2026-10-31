package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.InsightType
import com.moneymanager.app.data.local.entity.RecurrenceDecision
import com.moneymanager.app.data.local.entity.RecurrenceFrequency
import com.moneymanager.app.data.local.entity.RecurrentTransferKind
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RecurringPaymentDetectorTest {

    private val zone = ZoneId.systemDefault()

    private val savingsAccount = AccountEntity(
        uid = "ACC_SAVINGS",
        name = "HDFC Savings",
        accountType = AccountType.SAVINGS,
        currencyUid = "INR"
    )

    private val mutualFundAccount = AccountEntity(
        uid = "ACC_MF",
        name = "Zerodha Coin Mutual Funds",
        accountType = AccountType.MUTUAL_FUNDS,
        currencyUid = "INR"
    )

    private val rdAccount = AccountEntity(
        uid = "ACC_RD",
        name = "HDFC Recurring Deposit",
        accountType = AccountType.INVESTMENT,
        currencyUid = "INR"
    )

    private val accountsMap = mapOf(
        savingsAccount.uid to savingsAccount,
        mutualFundAccount.uid to mutualFundAccount,
        rdAccount.uid to rdAccount
    )

    @Test
    fun testDetect_monthlySubscriptionExpense() {
        // 4 consecutive monthly charges: 5 July, 5 Aug, 5 Sep, 5 Oct
        val txs = listOf(
            createExpense(649.0, LocalDate.of(2026, 7, 5), "Netflix.com"),
            createExpense(649.0, LocalDate.of(2026, 8, 5), "UPI-NETFLIX-998"),
            createExpense(649.0, LocalDate.of(2026, 9, 5), "Netflix India"),
            createExpense(649.0, LocalDate.of(2026, 10, 5), "Netflix")
        )

        val insights = RecurringPaymentDetector.detect(
            transactions = txs,
            accountsMap = accountsMap,
            categoriesMap = emptyMap(),
            decisionsMap = emptyMap(),
            referenceDate = LocalDate.of(2026, 10, 20),
            config = DetectorConfig(),
            zone = zone
        )

        assertEquals(1, insights.size)
        val sub = insights.first()
        assertTrue(sub.type in setOf(InsightType.SUBSCRIPTION, InsightType.FORGOTTEN_SUBSCRIPTION))
        assertEquals(RecurrenceFrequency.MONTHLY, sub.frequency)
        assertEquals("Subscription", sub.autoAssignedTag)
        assertTrue("Confidence should be high", sub.confidence!! >= 0.80)
        assertNotNull(sub.expectedNextDate)

        // Next expected date should be 5 November 2026
        val expectedNext = LocalDate.of(2026, 11, 5).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expectedNext, sub.expectedNextDate!!)
    }

    @Test
    fun testDetect_monthlyRecurringTransfer_SIP() {
        // Monthly SIP transfer from Savings to Mutual Funds: 10 July, 10 Aug, 10 Sep, 10 Oct
        val txs = listOf(
            createTransfer(10000.0, LocalDate.of(2026, 7, 10), "Parag Parikh Flexi Cap SIP"),
            createTransfer(10000.0, LocalDate.of(2026, 8, 10), "Parag Parikh Flexi Cap SIP"),
            createTransfer(10000.0, LocalDate.of(2026, 9, 10), "Parag Parikh Flexi Cap SIP"),
            createTransfer(10000.0, LocalDate.of(2026, 10, 10), "Parag Parikh Flexi Cap SIP")
        )

        val insights = RecurringPaymentDetector.detect(
            transactions = txs,
            accountsMap = accountsMap,
            categoriesMap = emptyMap(),
            decisionsMap = emptyMap(),
            referenceDate = LocalDate.of(2026, 10, 20),
            config = DetectorConfig(),
            zone = zone
        )

        assertEquals(1, insights.size)
        val transfer = insights.first()
        assertEquals(InsightType.RECURRING_TRANSFER, transfer.type)
        assertEquals(RecurrentTransferKind.SIP_INVESTMENT, transfer.transferKind)
        assertEquals("SIP", transfer.autoAssignedTag)
        assertEquals(RecurrenceFrequency.MONTHLY, transfer.frequency)
        assertTrue(transfer.confidence!! >= 0.80)
    }

    @Test
    fun testDetect_monthlyRecurringTransfer_RD() {
        // Monthly RD transfer: 1 July, 1 Aug, 1 Sep, 1 Oct
        val txs = listOf(
            createTransfer(5000.0, LocalDate.of(2026, 7, 1), "HDFC RD Deposit", destAccountUid = "ACC_RD"),
            createTransfer(5000.0, LocalDate.of(2026, 8, 1), "HDFC RD Deposit", destAccountUid = "ACC_RD"),
            createTransfer(5000.0, LocalDate.of(2026, 9, 1), "HDFC RD Deposit", destAccountUid = "ACC_RD"),
            createTransfer(5000.0, LocalDate.of(2026, 10, 1), "HDFC RD Deposit", destAccountUid = "ACC_RD")
        )

        val insights = RecurringPaymentDetector.detect(
            transactions = txs,
            accountsMap = accountsMap,
            categoriesMap = emptyMap(),
            decisionsMap = emptyMap(),
            referenceDate = LocalDate.of(2026, 10, 20),
            config = DetectorConfig(),
            zone = zone
        )

        assertEquals(1, insights.size)
        val rd = insights.first()
        assertEquals(InsightType.RECURRING_TRANSFER, rd.type)
        assertEquals(RecurrentTransferKind.RECURRING_DEPOSIT, rd.transferKind)
        assertEquals("Recurring Deposit", rd.autoAssignedTag)
    }

    @Test
    fun testDetect_priceIncreaseAlert() {
        // Prior charges ₹499, latest charge ₹649 (>10% variation)
        val txs = listOf(
            createExpense(499.0, LocalDate.of(2026, 7, 1), "Disney Hotstar"),
            createExpense(499.0, LocalDate.of(2026, 8, 1), "Disney Hotstar"),
            createExpense(499.0, LocalDate.of(2026, 9, 1), "Disney Hotstar"),
            createExpense(649.0, LocalDate.of(2026, 10, 1), "Disney Hotstar")
        )

        val insights = RecurringPaymentDetector.detect(
            transactions = txs,
            accountsMap = accountsMap,
            categoriesMap = emptyMap(),
            decisionsMap = emptyMap(),
            config = DetectorConfig(),
            zone = zone
        )

        assertEquals(1, insights.size)
        val item = insights.first()
        assertEquals(InsightType.PRICE_INCREASE, item.type)
    }

    @Test
    fun testDetect_suppressesRejectedRecurrence() {
        val txs = listOf(
            createExpense(649.0, LocalDate.of(2026, 7, 5), "Netflix"),
            createExpense(649.0, LocalDate.of(2026, 8, 5), "Netflix"),
            createExpense(649.0, LocalDate.of(2026, 9, 5), "Netflix"),
            createExpense(649.0, LocalDate.of(2026, 10, 5), "Netflix")
        )

        val decisions = mapOf("netflix" to RecurrenceDecision.REJECTED)

        val insights = RecurringPaymentDetector.detect(
            transactions = txs,
            accountsMap = accountsMap,
            categoriesMap = emptyMap(),
            decisionsMap = decisions,
            config = DetectorConfig(),
            zone = zone
        )

        // Must be suppressed because user rejected it
        assertTrue(insights.isEmpty())
    }

    private fun createExpense(amount: Double, date: LocalDate, note: String): TransactionEntity {
        return TransactionEntity(
            uid = java.util.UUID.randomUUID().toString(),
            type = TransactionType.EXPENSE,
            accountAmount = amount,
            baseAmount = amount,
            currencyUid = "INR",
            accountUid = "ACC_SAVINGS",
            note = note,
            timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }

    private fun createTransfer(
        amount: Double,
        date: LocalDate,
        note: String,
        destAccountUid: String = "ACC_MF"
    ): TransactionEntity {
        return TransactionEntity(
            uid = java.util.UUID.randomUUID().toString(),
            type = TransactionType.TRANSFER,
            accountAmount = amount,
            baseAmount = amount,
            currencyUid = "INR",
            accountUid = "ACC_SAVINGS",
            toAccountUid = destAccountUid,
            toAccountAmount = amount,
            note = note,
            timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }
}
