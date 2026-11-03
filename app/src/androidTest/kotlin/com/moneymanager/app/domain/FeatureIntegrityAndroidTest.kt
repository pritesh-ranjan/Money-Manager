package com.moneymanager.app.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moneymanager.app.data.MoneyManagerRepository
import com.moneymanager.app.data.StatementRepository
import com.moneymanager.app.data.local.AppDatabase
import com.moneymanager.app.data.local.entity.*
import com.moneymanager.app.domain.statement.core.StatementParserRegistry
import com.moneymanager.app.domain.statement.reconciliation.CardStatementReconciler
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FeatureIntegrityAndroidTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: MoneyManagerRepository
    private lateinit var statementRepository: StatementRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        database = AppDatabase.getDatabase(context)
        repository = MoneyManagerRepository(database.moneyManagerDao(), database.insightsDao())
        statementRepository = StatementRepository(database.statementDao())
    }

    @Test
    fun testAccountsAndTransactionsDataIntegrity() = runBlocking {
        val currencyUid = "CURRENCY_INR"
        val existingCurr = database.moneyManagerDao().getCurrencyById(currencyUid)
        val inrCurrency = existingCurr ?: CurrencyEntity(
            uid = currencyUid,
            name = "Indian Rupee",
            isoCode = "INR",
            symbol = "₹",
            rateToBase = 1.0,
            isMainCurrency = true
        ).also { database.moneyManagerDao().insertCurrencies(listOf(it)) }

        // 1. Create Accounts: Savings, Cash, Credit Card, Investment
        val savingsAcc = AccountEntity(
            uid = "test_savings_" + UUID.randomUUID(),
            name = "Test HDFC Salary",
            accountType = AccountType.SAVINGS,
            currencyUid = currencyUid,
            openingBalance = 50000.0,
            includeInNetWorth = true
        )
        val cashAcc = AccountEntity(
            uid = "test_cash_" + UUID.randomUUID(),
            name = "Test Cash Wallet",
            accountType = AccountType.CASH,
            currencyUid = currencyUid,
            openingBalance = 5000.0,
            includeInNetWorth = true
        )
        val cardAcc = AccountEntity(
            uid = "test_card_" + UUID.randomUUID(),
            name = "Test Axis Airtel Card",
            accountType = AccountType.CREDIT_CARD,
            currencyUid = currencyUid,
            openingBalance = 0.0,
            statementDay = 15,
            paymentDueDay = 5,
            creditLimit = 150000.0,
            includeInNetWorth = true
        )
        val investAcc = AccountEntity(
            uid = "test_invest_" + UUID.randomUUID(),
            name = "Test PPF Investment",
            accountType = AccountType.INVESTMENT,
            currencyUid = currencyUid,
            openingBalance = 100000.0,
            includeInNetWorth = true
        )

        repository.saveAccount(savingsAcc)
        repository.saveAccount(cashAcc)
        repository.saveAccount(cardAcc)
        repository.saveAccount(investAcc)

        // Baseline Net Worth: 50,000 + 5,000 + 100,000 = 155,000
        var netWorth = repository.netWorthSummary.first()
        assertNotNull(netWorth)
        assertEquals(155000.0, netWorth!!.netWorthBase, 0.01)
        assertEquals(155000.0, netWorth.totalAssetsBase, 0.01)
        assertEquals(0.0, netWorth.totalLiabilitiesBase, 0.01)

        // 2. Add Transactions: Expense from Savings
        val expenseTx = TransactionEntity(
            uid = "tx_exp_" + UUID.randomUUID(),
            type = TransactionType.EXPENSE,
            accountAmount = 1250.0,
            baseAmount = 1250.0,
            currencyUid = currencyUid,
            accountUid = savingsAcc.uid,
            note = "Dinner test",
            timestamp = System.currentTimeMillis()
        )
        repository.saveTransaction(expenseTx)

        // Savings: 50,000 - 1,250 = 48,750
        netWorth = repository.netWorthSummary.first()
        assertEquals(153750.0, netWorth!!.netWorthBase, 0.01)

        // 3. Add Income to Savings
        val incomeTx = TransactionEntity(
            uid = "tx_inc_" + UUID.randomUUID(),
            type = TransactionType.INCOME,
            accountAmount = 75000.0,
            baseAmount = 75000.0,
            currencyUid = currencyUid,
            accountUid = savingsAcc.uid,
            note = "Salary test",
            timestamp = System.currentTimeMillis()
        )
        repository.saveTransaction(incomeTx)

        // Savings: 48,750 + 75,000 = 123,750. Net worth = 123,750 + 5,000 + 100,000 = 228,750
        netWorth = repository.netWorthSummary.first()
        assertEquals(228750.0, netWorth!!.netWorthBase, 0.01)

        // 4. Add Transfer from Savings to Cash: 10,000
        val transferTx = TransactionEntity(
            uid = "tx_trf_" + UUID.randomUUID(),
            type = TransactionType.TRANSFER,
            accountAmount = 10000.0,
            baseAmount = 10000.0,
            currencyUid = currencyUid,
            accountUid = savingsAcc.uid,
            toAccountUid = cashAcc.uid,
            toAccountAmount = 10000.0,
            note = "ATM Withdrawal",
            timestamp = System.currentTimeMillis()
        )
        repository.saveTransaction(transferTx)

        // Net Worth is preserved during internal transfer!
        netWorth = repository.netWorthSummary.first()
        assertEquals(228750.0, netWorth!!.netWorthBase, 0.01)

        // Savings: 123,750 - 10,000 = 113,750
        // Cash: 5,000 + 10,000 = 15,000
        val savingsBalanceItem = netWorth.accountBalances.first { it.account.uid == savingsAcc.uid }
        val cashBalanceItem = netWorth.accountBalances.first { it.account.uid == cashAcc.uid }
        assertEquals(113750.0, savingsBalanceItem.nativeBalance, 0.01)
        assertEquals(15000.0, cashBalanceItem.nativeBalance, 0.01)

        // 5. Balance Adjustment on Cash: Adjust to 14,500 (diff: -500)
        repository.adjustAccountBalance(
            account = cashAcc,
            currency = inrCurrency,
            currentBalance = 15000.0,
            targetBalance = 14500.0,
            note = "Wallet count"
        )

        netWorth = repository.netWorthSummary.first()
        val cashBalanceAfterAdj = netWorth!!.accountBalances.first { it.account.uid == cashAcc.uid }
        assertEquals(14500.0, cashBalanceAfterAdj.nativeBalance, 0.01)
        assertEquals(228250.0, netWorth.netWorthBase, 0.01)

        // 6. Edit Transaction: Change Expense from 1,250 to 1,500 (-250 change)
        val editedExpense = expenseTx.copy(accountAmount = 1500.0, baseAmount = 1500.0)
        repository.saveTransaction(editedExpense)

        netWorth = repository.netWorthSummary.first()
        val savingsAfterEdit = netWorth!!.accountBalances.first { it.account.uid == savingsAcc.uid }
        assertEquals(113500.0, savingsAfterEdit.nativeBalance, 0.01)
        assertEquals(228000.0, netWorth.netWorthBase, 0.01)

        // 7. Delete Income Transaction -> Balance rolls back cleanly
        repository.deleteTransaction(incomeTx.uid)

        netWorth = repository.netWorthSummary.first()
        val savingsAfterDelete = netWorth!!.accountBalances.first { it.account.uid == savingsAcc.uid }
        // 113,500 - 75,000 = 38,500
        assertEquals(38500.0, savingsAfterDelete.nativeBalance, 0.01)
        assertEquals(153000.0, netWorth.netWorthBase, 0.01)
    }

    @Test
    fun testPdfStatementParsingAndReconciliationIntegration() = runBlocking {
        val testAssetNames = listOf(
            "fixtures/axis_airtel_golden.pdf",
            "fixtures/axis_horizon_golden.pdf",
            "fixtures/icici_amazon_pay_statement_golden.pdf",
            "fixtures/second_statement_golden.pdf"
        )

        val parserRegistry = StatementParserRegistry()
        val testContext = InstrumentationRegistry.getInstrumentation().context

        for (assetPath in testAssetNames) {
            // Parse statement using password "prit1510"
            val parseResult = testContext.assets.open(assetPath).use { stream ->
                parserRegistry.parse(stream, password = "prit1510")
            }

            assertNotNull(parseResult)
            assertTrue("Bank name must not be blank", parseResult.metadata.bankName.isNotBlank())
            assertTrue("Card number must not be blank", parseResult.metadata.accountOrCardNumberMasked.isNotBlank())
            assertTrue("Statement total due must be >= 0", parseResult.summary.totalAmountDue >= 0.0)
            assertTrue("Must contain transactions", parseResult.transactions.isNotEmpty())

            // Import into StatementRepository
            val statementId = statementRepository.importStatement(parseResult)
            assertTrue("Imported statementId must not be empty", statementId.isNotBlank())

            // Verify statement summary persisted
            val summaries = statementRepository.getStatementSummariesRaw()
            val importedSummary = summaries.firstOrNull { it.statementId == statementId }
            assertNotNull("Imported summary must exist in database", importedSummary)
            assertEquals(parseResult.metadata.bankName, importedSummary!!.bankName)
            assertEquals(parseResult.summary.totalAmountDue, importedSummary.statementTotalDue, 0.01)

            // Verify statement transactions persisted
            val savedTxs = statementRepository.getTransactionsByStatementIdRaw(statementId)
            assertEquals(parseResult.transactions.size, savedTxs.size)

            // Test Reconciliation with manual ledger (currently 0 manual txs for this card -> discrepancy expected)
            val manualTxs = emptyList<TransactionWithDetails>()
            val reconReport = CardStatementReconciler.reconcile(
                statementId = statementId,
                cardMasked = importedSummary.accountOrCardNumberMasked,
                statementTransactions = savedTxs,
                manualTransactions = manualTxs
            )

            assertNotNull(reconReport)
            assertTrue("Should detect unrecorded statement transactions", reconReport.unrecordedStatementTransactions.isNotEmpty())
            assertTrue("Should report discrepancy", reconReport.hasDiscrepancy)

            // Simulate adding the first unrecorded transaction to manual ledger
            val unrecordedItem = reconReport.unrecordedStatementTransactions.first()
            val newManualTx = TransactionEntity(
                uid = "manual_from_stmt_" + UUID.randomUUID(),
                type = if (unrecordedItem.isCredit) TransactionType.INCOME else TransactionType.EXPENSE,
                accountAmount = unrecordedItem.amount,
                baseAmount = unrecordedItem.amount,
                currencyUid = "CURRENCY_INR",
                accountUid = "dummy_card_acc",
                note = unrecordedItem.cleanNarration,
                timestamp = LocalDate.ofEpochDay(unrecordedItem.transactionDateEpochDay)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            )

            val updatedManualList = listOf(
                TransactionWithDetails(
                    transaction = newManualTx,
                    account = AccountEntity(
                        uid = "dummy_card_acc",
                        name = importedSummary.bankName,
                        accountType = AccountType.CREDIT_CARD,
                        currencyUid = "CURRENCY_INR"
                    ),
                    currency = CurrencyEntity(
                        uid = "CURRENCY_INR",
                        name = "Indian Rupee",
                        isoCode = "INR",
                        symbol = "₹",
                        rateToBase = 1.0,
                        isMainCurrency = true
                    ),
                    toAccount = null,
                    category = null,
                    tags = emptyList()
                )
            )

            val recheckedReport = CardStatementReconciler.reconcile(
                statementId = statementId,
                cardMasked = importedSummary.accountOrCardNumberMasked,
                statementTransactions = savedTxs,
                manualTransactions = updatedManualList
            )

            // Unrecorded count should be decremented by 1
            assertEquals(reconReport.unrecordedStatementTransactions.size - 1, recheckedReport.unrecordedStatementTransactions.size)

            // Test Settling / Marking Paid
            statementRepository.setStatementPaidStatus(statementId, isPaid = true)
            val updatedSummary = statementRepository.getStatementSummariesRaw().first { it.statementId == statementId }
            assertTrue("Statement must be marked reconciled/paid", updatedSummary.isReconciled)
        }
    }
}
