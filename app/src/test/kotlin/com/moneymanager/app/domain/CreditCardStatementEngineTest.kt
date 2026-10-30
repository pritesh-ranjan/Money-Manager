package com.moneymanager.app.domain

import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CreditCardStatementEngineTest {

    private val zoneId = ZoneId.of("Asia/Kolkata")

    @Test
    fun testSameMonthDueDateCycle_ICICI_Apay() {
        // ICICI Apay: Statement on 12th, Due on 30th (dueDay > statementDay)
        val card = AccountEntity(
            uid = "icici_apay",
            name = "ICICI Apay Card",
            accountType = AccountType.CREDIT_CARD,
            currencyUid = "INR",
            statementDay = 12,
            paymentDueDay = 30,
            creditLimit = 100000.0
        )

        val currentDate = LocalDate.of(2026, 10, 15) // Today is 15 Oct

        val statementCutoffDate = LocalDate.of(2026, 10, 12)
        val prevCutoffDate = LocalDate.of(2026, 9, 12)

        val txInCycleMillis = LocalDate.of(2026, 10, 5)
            .atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()
        val txUnbilledMillis = LocalDate.of(2026, 10, 14)
            .atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()

        val transactions = listOf(
            TransactionEntity(
                uid = "tx1",
                type = TransactionType.EXPENSE,
                accountAmount = 2500.0,
                baseAmount = 2500.0,
                currencyUid = "INR",
                accountUid = card.uid,
                timestamp = txInCycleMillis
            ),
            TransactionEntity(
                uid = "tx2",
                type = TransactionType.EXPENSE,
                accountAmount = 1200.0,
                baseAmount = 1200.0,
                currencyUid = "INR",
                accountUid = card.uid,
                timestamp = txUnbilledMillis
            )
        )

        val summary = CreditCardStatementEngine.calculateStatement(card, transactions, currentDate, zoneId)

        assertEquals(statementCutoffDate, summary.lastStatementDate)
        assertEquals(LocalDate.of(2026, 10, 30), summary.paymentDueDate)
        assertEquals(2500.0, summary.statementBilledAmount, 0.001)
        assertEquals(2500.0, summary.remainingStatementDue, 0.001)
        assertEquals(1200.0, summary.unbilledAmount, 0.001)
        assertEquals(3700.0, summary.totalOutstanding, 0.001)
        assertEquals(15L, summary.daysUntilDue) // 30 Oct - 15 Oct = 15 days
        assertEquals(StatementStatus.UNPAID, summary.status)
    }

    @Test
    fun testCrossMonthDueDateCycle_HDFC_Swiggy() {
        // HDFC Swiggy: Statement on 14th, Due on 3rd of NEXT month (dueDay <= statementDay)
        val card = AccountEntity(
            uid = "hdfc_swiggy",
            name = "HDFC Swiggy Card",
            accountType = AccountType.CREDIT_CARD,
            currencyUid = "INR",
            statementDay = 14,
            paymentDueDay = 3,
            creditLimit = 150000.0
        )

        val currentDate = LocalDate.of(2026, 10, 20) // Today is 20 Oct

        val txInCycleMillis = LocalDate.of(2026, 10, 1)
            .atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()

        val transactions = listOf(
            TransactionEntity(
                uid = "tx1",
                type = TransactionType.EXPENSE,
                accountAmount = 6500.0,
                baseAmount = 6500.0,
                currencyUid = "INR",
                accountUid = card.uid,
                timestamp = txInCycleMillis
            )
        )

        val summary = CreditCardStatementEngine.calculateStatement(card, transactions, currentDate, zoneId)

        assertEquals(LocalDate.of(2026, 10, 14), summary.lastStatementDate)
        // Due date must be 03 November 2026!
        assertEquals(LocalDate.of(2026, 11, 3), summary.paymentDueDate)
        assertEquals(6500.0, summary.statementBilledAmount, 0.001)
        assertEquals(14L, summary.daysUntilDue) // 3 Nov - 20 Oct = 14 days
    }

    @Test
    fun testPaymentDeductionFromBilledAmount() {
        val card = AccountEntity(
            uid = "card1",
            name = "Test Card",
            accountType = AccountType.CREDIT_CARD,
            currencyUid = "INR",
            statementDay = 14,
            paymentDueDay = 3,
            creditLimit = 50000.0
        )

        val currentDate = LocalDate.of(2026, 10, 25)

        val txInCycle = LocalDate.of(2026, 10, 10)
            .atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()
        val paymentAfterStatement = LocalDate.of(2026, 10, 18)
            .atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()
        val newSpendsAfterStatement = LocalDate.of(2026, 10, 22)
            .atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()

        val transactions = listOf(
            // Billed spend
            TransactionEntity(
                uid = "tx1",
                type = TransactionType.EXPENSE,
                accountAmount = 5000.0,
                baseAmount = 5000.0,
                currencyUid = "INR",
                accountUid = card.uid,
                timestamp = txInCycle
            ),
            // Partial payment of 3000
            TransactionEntity(
                uid = "txPay",
                type = TransactionType.TRANSFER,
                accountAmount = 3000.0,
                baseAmount = 3000.0,
                currencyUid = "INR",
                accountUid = "bank_acc",
                toAccountUid = card.uid,
                toAccountAmount = 3000.0,
                timestamp = paymentAfterStatement
            ),
            // New unbilled spend
            TransactionEntity(
                uid = "txNew",
                type = TransactionType.EXPENSE,
                accountAmount = 1500.0,
                baseAmount = 1500.0,
                currencyUid = "INR",
                accountUid = card.uid,
                timestamp = newSpendsAfterStatement
            )
        )

        val summary = CreditCardStatementEngine.calculateStatement(card, transactions, currentDate, zoneId)

        assertEquals(5000.0, summary.statementBilledAmount, 0.001)
        assertEquals(3000.0, summary.paymentsSinceStatement, 0.001)
        assertEquals(2000.0, summary.remainingStatementDue, 0.001) // 5000 - 3000 = 2000 remaining
        assertEquals(1500.0, summary.unbilledAmount, 0.001)
        assertEquals(3500.0, summary.totalOutstanding, 0.001) // 2000 remaining + 1500 unbilled
    }

    @Test
    fun testFullPaymentMarksStatusPaid() {
        val card = AccountEntity(
            uid = "card1",
            name = "Test Card",
            accountType = AccountType.CREDIT_CARD,
            currencyUid = "INR",
            statementDay = 14,
            paymentDueDay = 3
        )

        val currentDate = LocalDate.of(2026, 10, 25)
        val txInCycle = LocalDate.of(2026, 10, 10).atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()
        val paymentAfterStatement = LocalDate.of(2026, 10, 18).atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()

        val transactions = listOf(
            TransactionEntity(uid = "tx1", type = TransactionType.EXPENSE, accountAmount = 4000.0, baseAmount = 4000.0, currencyUid = "INR", accountUid = card.uid, timestamp = txInCycle),
            TransactionEntity(uid = "txPay", type = TransactionType.TRANSFER, accountAmount = 4000.0, baseAmount = 4000.0, currencyUid = "INR", accountUid = "bank", toAccountUid = card.uid, toAccountAmount = 4000.0, timestamp = paymentAfterStatement)
        )

        val summary = CreditCardStatementEngine.calculateStatement(card, transactions, currentDate, zoneId)

        assertEquals(0.0, summary.remainingStatementDue, 0.001)
        assertEquals(StatementStatus.PAID, summary.status)
    }
}
