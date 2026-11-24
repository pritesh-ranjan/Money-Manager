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

    @Test
    fun testOpeningBalancePaidDownByTransfers() {
        val card = AccountEntity(
            uid = "card_opening",
            name = "Opening Debt Card",
            accountType = AccountType.CREDIT_CARD,
            currencyUid = "INR",
            openingBalance = 5000.0,
            statementDay = 15,
            paymentDueDay = 5,
            creditLimit = 50000.0
        )

        val currentDate = LocalDate.of(2026, 10, 20)
        val paymentTime = LocalDate.of(2026, 10, 18).atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()

        // Partial payment of 3000 reduces totalOutstanding from 5000 to 2000
        val partialSummary = CreditCardStatementEngine.calculateStatement(
            card = card,
            transactions = listOf(
                TransactionEntity(
                    uid = "pay_partial",
                    type = TransactionType.TRANSFER,
                    accountAmount = 3000.0,
                    baseAmount = 3000.0,
                    currencyUid = "INR",
                    accountUid = "bank",
                    toAccountUid = card.uid,
                    toAccountAmount = 3000.0,
                    timestamp = paymentTime
                )
            ),
            currentDate = currentDate,
            zoneId = zoneId
        )
        assertEquals(2000.0, partialSummary.totalOutstanding, 0.001)

        // Full payment of 5000 clears totalOutstanding completely
        val fullSummary = CreditCardStatementEngine.calculateStatement(
            card = card,
            transactions = listOf(
                TransactionEntity(
                    uid = "pay_full",
                    type = TransactionType.TRANSFER,
                    accountAmount = 5000.0,
                    baseAmount = 5000.0,
                    currencyUid = "INR",
                    accountUid = "bank",
                    toAccountUid = card.uid,
                    toAccountAmount = 5000.0,
                    timestamp = paymentTime
                )
            ),
            currentDate = currentDate,
            zoneId = zoneId
        )
        assertEquals(0.0, fullSummary.totalOutstanding, 0.001)
        assertEquals(50000.0, fullSummary.availableCredit!!, 0.001)
        assertEquals(0, fullSummary.creditUtilizationPercent!!)
    }

    @Test
    fun testExcessPaymentReducesOpeningBalanceAndMatchesLedger() {
        val card = AccountEntity(
            uid = "card_excess",
            name = "Excess Pay Card",
            accountType = AccountType.CREDIT_CARD,
            currencyUid = "INR",
            openingBalance = 5000.0,
            statementDay = 15,
            paymentDueDay = 5,
            creditLimit = 50000.0
        )

        val currentDate = LocalDate.of(2026, 10, 20)
        val billedTime = LocalDate.of(2026, 10, 10).atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()
        val paymentTime = LocalDate.of(2026, 10, 17).atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()
        val unbilledTime = LocalDate.of(2026, 10, 18).atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()

        val summary = CreditCardStatementEngine.calculateStatement(
            card = card,
            transactions = listOf(
                TransactionEntity(
                    uid = "tx_billed",
                    type = TransactionType.EXPENSE,
                    accountAmount = 2000.0,
                    baseAmount = 2000.0,
                    currencyUid = "INR",
                    accountUid = card.uid,
                    timestamp = billedTime
                ),
                TransactionEntity(
                    uid = "tx_pay",
                    type = TransactionType.TRANSFER,
                    accountAmount = 6000.0,
                    baseAmount = 6000.0,
                    currencyUid = "INR",
                    accountUid = "bank",
                    toAccountUid = card.uid,
                    toAccountAmount = 6000.0,
                    timestamp = paymentTime
                ),
                TransactionEntity(
                    uid = "tx_unbilled",
                    type = TransactionType.EXPENSE,
                    accountAmount = 1000.0,
                    baseAmount = 1000.0,
                    currencyUid = "INR",
                    accountUid = card.uid,
                    timestamp = unbilledTime
                )
            ),
            currentDate = currentDate,
            zoneId = zoneId
        )

        assertEquals(7000.0, summary.statementBilledAmount, 0.001)
        assertEquals(1000.0, summary.remainingStatementDue, 0.001)
        assertEquals(2000.0, summary.totalOutstanding, 0.001)
    }

    @Test
    fun testOpeningBalanceAndPriorCycleCarryOverAndOutgoingTransferAreIncludedInStatementDue() {
        val card = AccountEntity(
            uid = "card_carryover",
            name = "Carryover Card",
            accountType = AccountType.CREDIT_CARD,
            currencyUid = "INR",
            openingBalance = 5000.0,
            statementDay = 15,
            paymentDueDay = 5,
            creditLimit = 50000.0
        )

        val currentDate = LocalDate.of(2026, 10, 20)
        val priorCycleExpenseTime = LocalDate.of(2026, 8, 25).atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()
        val outgoingTransferTime = LocalDate.of(2026, 10, 10).atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()
        val paymentTime = LocalDate.of(2026, 10, 18).atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()

        val summaryBeforePay = CreditCardStatementEngine.calculateStatement(
            card = card,
            transactions = listOf(
                TransactionEntity(
                    uid = "tx_prior",
                    type = TransactionType.EXPENSE,
                    accountAmount = 1500.0,
                    baseAmount = 1500.0,
                    currencyUid = "INR",
                    accountUid = card.uid,
                    timestamp = priorCycleExpenseTime
                ),
                TransactionEntity(
                    uid = "tx_out_trf",
                    type = TransactionType.TRANSFER,
                    accountAmount = 500.0,
                    baseAmount = 500.0,
                    currencyUid = "INR",
                    accountUid = card.uid,
                    toAccountUid = "wallet",
                    toAccountAmount = 500.0,
                    timestamp = outgoingTransferTime
                )
            ),
            currentDate = currentDate,
            zoneId = zoneId
        )

        assertEquals(7000.0, summaryBeforePay.statementBilledAmount, 0.001)
        assertEquals(7000.0, summaryBeforePay.remainingStatementDue, 0.001)
        assertEquals(7000.0, summaryBeforePay.totalOutstanding, 0.001)

        val summaryAfterPartialPay = CreditCardStatementEngine.calculateStatement(
            card = card,
            transactions = listOf(
                TransactionEntity(
                    uid = "tx_prior",
                    type = TransactionType.EXPENSE,
                    accountAmount = 1500.0,
                    baseAmount = 1500.0,
                    currencyUid = "INR",
                    accountUid = card.uid,
                    timestamp = priorCycleExpenseTime
                ),
                TransactionEntity(
                    uid = "tx_out_trf",
                    type = TransactionType.TRANSFER,
                    accountAmount = 500.0,
                    baseAmount = 500.0,
                    currencyUid = "INR",
                    accountUid = card.uid,
                    toAccountUid = "wallet",
                    toAccountAmount = 500.0,
                    timestamp = outgoingTransferTime
                ),
                TransactionEntity(
                    uid = "tx_pay",
                    type = TransactionType.TRANSFER,
                    accountAmount = 3000.0,
                    baseAmount = 3000.0,
                    currencyUid = "INR",
                    accountUid = "bank",
                    toAccountUid = card.uid,
                    toAccountAmount = 3000.0,
                    timestamp = paymentTime
                )
            ),
            currentDate = currentDate,
            zoneId = zoneId
        )

        assertEquals(7000.0, summaryAfterPartialPay.statementBilledAmount, 0.001)
        assertEquals(3000.0, summaryAfterPartialPay.paymentsSinceStatement, 0.001)
        assertEquals(4000.0, summaryAfterPartialPay.remainingStatementDue, 0.001)
        assertEquals(4000.0, summaryAfterPartialPay.totalOutstanding, 0.001)
    }

    @Test
    fun testOverpaymentClampsTotalOutstandingAndRemainingDueToZero() {
        val card = AccountEntity(
            uid = "card_overpay",
            name = "Overpaid Card",
            accountType = AccountType.CREDIT_CARD,
            currencyUid = "INR",
            statementDay = 15,
            paymentDueDay = 5,
            creditLimit = 50000.0
        )

        val currentDate = LocalDate.of(2026, 10, 20)
        val billedTime = LocalDate.of(2026, 10, 10).atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()
        val paymentTime = LocalDate.of(2026, 10, 18).atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()

        val summary = CreditCardStatementEngine.calculateStatement(
            card = card,
            transactions = listOf(
                TransactionEntity(
                    uid = "tx_spend",
                    type = TransactionType.EXPENSE,
                    accountAmount = 2000.0,
                    baseAmount = 2000.0,
                    currencyUid = "INR",
                    accountUid = card.uid,
                    timestamp = billedTime
                ),
                TransactionEntity(
                    uid = "tx_overpay",
                    type = TransactionType.TRANSFER,
                    accountAmount = 2500.0,
                    baseAmount = 2500.0,
                    currencyUid = "INR",
                    accountUid = "bank",
                    toAccountUid = card.uid,
                    toAccountAmount = 2500.0,
                    timestamp = paymentTime
                )
            ),
            currentDate = currentDate,
            zoneId = zoneId
        )

        assertEquals(0.0, summary.totalOutstanding, 0.001)
        assertEquals(0.0, summary.remainingStatementDue, 0.001)
        assertEquals(0, summary.creditUtilizationPercent!!)
    }

    @Test
    fun testAdjustmentSignMatchesNetWorthCalculator() {
        val card = AccountEntity(
            uid = "card_adj",
            name = "Adjusted Card",
            accountType = AccountType.CREDIT_CARD,
            currencyUid = "INR",
            openingBalance = 5000.0,
            statementDay = 15,
            paymentDueDay = 5,
            creditLimit = 50000.0
        )

        val currentDate = LocalDate.of(2026, 10, 20)
        val adjTime = LocalDate.of(2026, 10, 18).atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()

        // Positive ledger adjustment reduces liability debt from 5000 to 3000
        val summary = CreditCardStatementEngine.calculateStatement(
            card = card,
            transactions = listOf(
                TransactionEntity(
                    uid = "tx_adj",
                    type = TransactionType.ADJUSTMENT,
                    accountAmount = 2000.0,
                    baseAmount = 2000.0,
                    currencyUid = "INR",
                    accountUid = card.uid,
                    timestamp = adjTime
                )
            ),
            currentDate = currentDate,
            zoneId = zoneId
        )

        assertEquals(3000.0, summary.totalOutstanding, 0.001)
    }
}
