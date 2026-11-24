package com.moneymanager.app.domain.statement

import com.moneymanager.app.data.local.entity.*
import com.moneymanager.app.domain.statement.reconciliation.CardStatementReconciler
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CardStatementReconcilerTest {

    private val testCurrency = CurrencyEntity("curr_inr", "Rupee", "INR", "₹", 1.0)
    private val testAccount = AccountEntity(
        uid = "acc_card_3004",
        name = "Amazon Pay ICICI Card",
        accountType = AccountType.CREDIT_CARD,
        currencyUid = "curr_inr"
    )
    private val testCategory = CategoryEntity(
        uid = "cat_shopping",
        name = "Shopping",
        type = CategoryType.EXPENSE
    )

    private fun createStatementTx(
        uid: String,
        amount: Double,
        date: LocalDate,
        cleanNarration: String,
        isCredit: Boolean = false
    ) = CardStatementTransactionEntity(
        uid = uid,
        statementId = "stmt_test_01",
        bankId = "ICICI_AMAZON_PAY_CC",
        bankName = "ICICI Bank",
        accountOrCardNumberMasked = "4315XXXXXXXX3004",
        accountHolderName = "Pritesh",
        statementYearMonth = "2026-10",
        statementDateEpochDay = LocalDate.of(2026, 10, 15).toEpochDay(),
        paymentDueDateEpochDay = LocalDate.of(2026, 11, 5).toEpochDay(),
        billingPeriodStartEpochDay = LocalDate.of(2026, 9, 16).toEpochDay(),
        billingPeriodEndEpochDay = LocalDate.of(2026, 10, 15).toEpochDay(),
        creditLimit = 150000.0,
        availableCredit = 120000.0,
        cashLimit = null,
        previousBalance = 0.0,
        statementPurchasesOrDebits = 2000.0,
        statementPaymentsOrCredits = 0.0,
        statementTotalDue = 2000.0,
        statementMinDue = 200.0,
        isReconciled = false,
        rewardType = null,
        rewardEarned = null,
        rewardRedeemedOrCredited = null,
        rewardClosingBalance = null,
        rewardProgramName = null,
        serialNumber = 1,
        transactionDateEpochDay = date.toEpochDay(),
        referenceNumber = "REF123",
        rawNarration = cleanNarration,
        cleanNarration = cleanNarration,
        amount = amount,
        isCredit = isCredit,
        debit = if (!isCredit) amount else null,
        credit = if (isCredit) amount else null,
        rewardPoints = null,
        pageNumber = 1
    )

    private fun createManualTx(
        uid: String,
        amount: Double,
        date: LocalDate,
        type: TransactionType = TransactionType.EXPENSE,
        notes: String = "Test expense"
    ): TransactionWithDetails {
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return TransactionWithDetails(
            transaction = TransactionEntity(
                uid = uid,
                type = type,
                accountAmount = amount,
                baseAmount = amount,
                currencyUid = testCurrency.uid,
                accountUid = testAccount.uid,
                categoryUid = testCategory.uid,
                note = notes,
                description = "",
                timestamp = millis
            ),
            account = testAccount,
            currency = testCurrency,
            category = testCategory,
            toAccount = null,
            tags = emptyList()
        )
    }

    @Test
    fun testReconciliation_PerfectMatch() {
        val date1 = LocalDate.of(2026, 9, 20)
        val date2 = LocalDate.of(2026, 10, 5)

        val statementTxs = listOf(
            createStatementTx("st_1", 450.0, date1, "Swiggy"),
            createStatementTx("st_2", 1550.0, date2, "Amazon Retail")
        )

        // Manual transactions match with slight date deviation (within 2 days for settlement)
        val manualTxs = listOf(
            createManualTx("m_1", 450.0, date1.plusDays(1)),
            createManualTx("m_2", 1550.0, date2)
        )

        val report = CardStatementReconciler.reconcile(
            statementId = "stmt_test_01",
            cardMasked = "•• 3004",
            statementTransactions = statementTxs,
            manualTransactions = manualTxs,
            periodStartEpochDay = LocalDate.of(2026, 9, 16).toEpochDay(),
            periodEndEpochDay = LocalDate.of(2026, 10, 15).toEpochDay()
        )

        assertTrue("Report must be fully reconciled", report.isFullyReconciled)
        assertFalse(report.hasDiscrepancy)
        assertEquals(0.0, report.varianceAmount, 0.001)
        assertEquals(2, report.matchedTransactions.size)
        assertTrue(report.unrecordedStatementTransactions.isEmpty())
        assertTrue(report.unmatchedManualTransactions.isEmpty())
    }

    @Test
    fun testReconciliation_Discrepancy_UnrecordedStatementExpense() {
        val date1 = LocalDate.of(2026, 9, 20)
        val date2 = LocalDate.of(2026, 10, 5)

        val statementTxs = listOf(
            createStatementTx("st_1", 450.0, date1, "Swiggy"),
            createStatementTx("st_2", 649.0, date2, "Netflix Auto-Debit") // User forgot to enter this in app
        )

        val manualTxs = listOf(
            createManualTx("m_1", 450.0, date1)
        )

        val report = CardStatementReconciler.reconcile(
            statementId = "stmt_test_01",
            cardMasked = "•• 3004",
            statementTransactions = statementTxs,
            manualTransactions = manualTxs,
            periodStartEpochDay = LocalDate.of(2026, 9, 16).toEpochDay(),
            periodEndEpochDay = LocalDate.of(2026, 10, 15).toEpochDay()
        )

        assertFalse("Report must detect discrepancy", report.isFullyReconciled)
        assertTrue(report.hasDiscrepancy)
        assertEquals(649.0, report.varianceAmount, 0.001) // Billed 1099, tracked 450 -> variance +649
        assertEquals(1, report.matchedTransactions.size)
        assertEquals(1, report.unrecordedStatementTransactions.size)
        assertEquals("Netflix Auto-Debit", report.unrecordedStatementTransactions.first().cleanNarration)
        assertTrue(report.unmatchedManualTransactions.isEmpty())
    }

    @Test
    fun testReconciliation_Discrepancy_UnmatchedManualExpense() {
        val date1 = LocalDate.of(2026, 9, 20)

        val statementTxs = listOf(
            createStatementTx("st_1", 500.0, date1, "Cafe Coffee Day")
        )

        val manualTxs = listOf(
            createManualTx("m_1", 500.0, date1),
            createManualTx("m_2", 1200.0, LocalDate.of(2026, 9, 25)) // Manual entry never billed on card
        )

        val report = CardStatementReconciler.reconcile(
            statementId = "stmt_test_01",
            cardMasked = "•• 3004",
            statementTransactions = statementTxs,
            manualTransactions = manualTxs,
            periodStartEpochDay = LocalDate.of(2026, 9, 16).toEpochDay(),
            periodEndEpochDay = LocalDate.of(2026, 10, 15).toEpochDay()
        )

        assertFalse("Report must flag discrepancy", report.isFullyReconciled)
        assertTrue(report.hasDiscrepancy)
        assertEquals(-1200.0, report.varianceAmount, 0.001) // Statement debits 500, manual 1700 -> variance -1200
        assertEquals(1, report.matchedTransactions.size)
        assertTrue(report.unrecordedStatementTransactions.isEmpty())
        assertEquals(1, report.unmatchedManualTransactions.size)
        assertEquals(1200.0, report.unmatchedManualTransactions.first().transaction.accountAmount, 0.001)
    }

    @Test
    fun testReconciliation_MatchesBillPaymentTransferToCardAndTreatsOutgoingTransferAsDebit() {
        val dateDebit = LocalDate.of(2026, 9, 22)
        val datePayment = LocalDate.of(2026, 10, 2)

        val savingsAccount = AccountEntity(
            uid = "acc_savings",
            name = "HDFC Savings",
            accountType = AccountType.SAVINGS,
            currencyUid = "curr_inr"
        )
        val walletAccount = AccountEntity(
            uid = "acc_wallet",
            name = "Paytm Wallet",
            accountType = AccountType.WALLET,
            currencyUid = "curr_inr"
        )

        val statementTxs = listOf(
            createStatementTx("st_debit", 1200.0, dateDebit, "Paytm Wallet Load", isCredit = false),
            createStatementTx("st_payment", 2000.0, datePayment, "BBPS Payment Received", isCredit = true)
        )

        val outgoingTransferFromCard = TransactionWithDetails(
            transaction = TransactionEntity(
                uid = "m_out_trf",
                type = TransactionType.TRANSFER,
                accountAmount = 1200.0,
                baseAmount = 1200.0,
                currencyUid = testCurrency.uid,
                accountUid = testAccount.uid,
                toAccountUid = walletAccount.uid,
                toAccountAmount = 1200.0,
                note = "Wallet load",
                timestamp = dateDebit.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            ),
            account = testAccount,
            toAccount = walletAccount,
            currency = testCurrency
        )

        val billPaymentToCard = TransactionWithDetails(
            transaction = TransactionEntity(
                uid = "m_bill_pay",
                type = TransactionType.TRANSFER,
                accountAmount = 2000.0,
                baseAmount = 2000.0,
                currencyUid = testCurrency.uid,
                accountUid = savingsAccount.uid,
                toAccountUid = testAccount.uid,
                toAccountAmount = 2000.0,
                note = "Credit Card Bill Payment",
                timestamp = datePayment.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            ),
            account = savingsAccount,
            toAccount = testAccount,
            currency = testCurrency
        )

        val report = CardStatementReconciler.reconcile(
            statementId = "stmt_test_01",
            cardMasked = "•• 3004",
            statementTransactions = statementTxs,
            manualTransactions = listOf(outgoingTransferFromCard, billPaymentToCard),
            periodStartEpochDay = LocalDate.of(2026, 9, 16).toEpochDay(),
            periodEndEpochDay = LocalDate.of(2026, 10, 15).toEpochDay(),
            cardAccountUid = testAccount.uid
        )

        assertTrue("Bill payment transfer and outgoing transfer must reconcile cleanly", report.isFullyReconciled)
        assertFalse(report.hasDiscrepancy)
        assertEquals(2, report.matchedTransactions.size)
        assertEquals(1200.0, report.statementTotalDebits, 0.001)
        assertEquals(1200.0, report.manualTotalDebits, 0.001)
        assertEquals(2000.0, report.statementTotalCredits, 0.001)
        assertEquals(2000.0, report.manualTotalCredits, 0.001)
        assertEquals(0.0, report.varianceAmount, 0.001)
    }
}
