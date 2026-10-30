package com.moneymanager.app.domain

import com.moneymanager.app.data.AccountDeleteOutcome
import com.moneymanager.app.data.FakeMoneyManagerDao
import com.moneymanager.app.data.MoneyManagerRepository
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryType
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.data.local.entity.TagEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class AccountAndCardCrudValidationTest {

    private lateinit var dao: FakeMoneyManagerDao
    private lateinit var repository: MoneyManagerRepository

    private val inr = CurrencyEntity(
        uid = "CURR_INR",
        name = "Indian Rupee",
        isoCode = "INR",
        symbol = "₹",
        rateToBase = 1.0,
        isMainCurrency = true
    )

    private val usd = CurrencyEntity(
        uid = "CURR_USD",
        name = "US Dollar",
        isoCode = "USD",
        symbol = "$",
        rateToBase = 84.0,
        isMainCurrency = false
    )

    @Before
    fun setup() = runTest {
        dao = FakeMoneyManagerDao()
        repository = MoneyManagerRepository(dao)

        dao.insertCurrencies(listOf(inr, usd))
    }

    // =========================================================================
    // POSITIVE TEST CASES: ACCOUNTS & CREDIT CARDS CRUD
    // =========================================================================

    @Test
    fun testCreateAndReadAccounts_variousTypes() = runTest {
        val bank = AccountEntity(
            uid = "acc_bank",
            name = "Kotak Savings",
            accountType = AccountType.SAVINGS,
            currencyUid = inr.uid,
            openingBalance = 25000.0
        )
        val wallet = AccountEntity(
            uid = "acc_wallet",
            name = "Paytm Wallet",
            accountType = AccountType.WALLET,
            currencyUid = inr.uid,
            openingBalance = 1500.0
        )
        repository.saveAccount(bank)
        repository.saveAccount(wallet)

        val activeAccounts = repository.accountsWithCurrency.first()
        assertEquals(2, activeAccounts.size)
        assertTrue(activeAccounts.any { it.account.uid == "acc_bank" })
        assertTrue(activeAccounts.any { it.account.uid == "acc_wallet" })
    }

    @Test
    fun testCreateCreditCard_withCycleAndSettlementAccount() = runTest {
        val settlementBank = AccountEntity(
            uid = "acc_settle",
            name = "Salary Bank",
            accountType = AccountType.SAVINGS,
            currencyUid = inr.uid,
            openingBalance = 100000.0
        )
        repository.saveAccount(settlementBank)

        val creditCard = AccountEntity(
            uid = "acc_cc_test",
            name = "Tata Neu Card",
            accountType = AccountType.CREDIT_CARD,
            currencyUid = inr.uid,
            openingBalance = 0.0,
            statementDay = 18,
            paymentDueDay = 8,
            creditLimit = 150000.0,
            settlementAccountUid = settlementBank.uid
        )
        repository.saveAccount(creditCard)

        val retrieved = dao.getAccountWithCurrencyById("acc_cc_test")
        assertNotNull(retrieved)
        assertEquals(18, retrieved!!.account.statementDay)
        assertEquals(8, retrieved.account.paymentDueDay)
        assertEquals(150000.0, retrieved.account.creditLimit!!, 0.001)
        assertEquals(settlementBank.uid, retrieved.account.settlementAccountUid)
    }

    @Test
    fun testUpdateAccount_updatesAttributesCleanly() = runTest {
        val acc = AccountEntity(
            uid = "acc_to_edit",
            name = "Old Name",
            accountType = AccountType.SAVINGS,
            currencyUid = inr.uid,
            openingBalance = 10000.0
        )
        repository.saveAccount(acc)

        val updated = acc.copy(
            name = "New Account Name",
            openingBalance = 15000.0
        )
        repository.saveAccount(updated)

        val retrieved = dao.getAccountWithCurrencyById("acc_to_edit")
        assertNotNull(retrieved)
        assertEquals("New Account Name", retrieved!!.account.name)
        assertEquals(15000.0, retrieved.account.openingBalance, 0.001)
    }

    @Test
    fun testDeleteAccount_withZeroTransactions_hardDeletes() = runTest {
        val unusedAcc = AccountEntity(
            uid = "acc_unused",
            name = "Unused Bank",
            accountType = AccountType.SAVINGS,
            currencyUid = inr.uid
        )
        repository.saveAccount(unusedAcc)

        val outcome = repository.deleteAccount(unusedAcc)
        assertEquals(AccountDeleteOutcome.DELETED, outcome)

        // Completely removed from dao
        assertNull(dao.getAccountWithCurrencyById("acc_unused"))
        assertTrue(dao.getAllAccountsRaw().none { it.uid == "acc_unused" })
    }

    @Test
    fun testDeleteAccount_withTransactions_safelyArchives() = runTest {
        val activeAcc = AccountEntity(
            uid = "acc_with_txs",
            name = "Active Bank",
            accountType = AccountType.SAVINGS,
            currencyUid = inr.uid,
            openingBalance = 10000.0
        )
        repository.saveAccount(activeAcc)

        // Add a transaction on this account
        val tx = TransactionEntity(
            uid = "tx_on_acc",
            type = TransactionType.EXPENSE,
            accountAmount = 200.0,
            baseAmount = 200.0,
            currencyUid = inr.uid,
            accountUid = activeAcc.uid
        )
        repository.saveTransaction(tx)

        // Delete account
        val outcome = repository.deleteAccount(activeAcc)
        assertEquals(AccountDeleteOutcome.ARCHIVED, outcome)

        // Account should no longer appear in active accounts query
        val activeAccounts = repository.accountsWithCurrency.first()
        assertFalse(activeAccounts.any { it.account.uid == "acc_with_txs" })

        // Account is still in DB with isArchived = true to protect transaction history
        val rawAcc = dao.getAllAccountsRaw().firstOrNull { it.uid == "acc_with_txs" }
        assertNotNull(rawAcc)
        assertTrue(rawAcc!!.isArchived)

        // The transaction remains intact
        val savedTx = dao.getTransactionById("tx_on_acc")
        assertNotNull(savedTx)
    }

    @Test
    fun testDeleteAccount_clearsSettlementReferencesInOtherCards() = runTest {
        val settlementAcc = AccountEntity(
            uid = "acc_bank_settle",
            name = "Settlement Bank",
            accountType = AccountType.SAVINGS,
            currencyUid = inr.uid
        )
        val creditCard = AccountEntity(
            uid = "acc_card_with_settle",
            name = "Card With Settle",
            accountType = AccountType.CREDIT_CARD,
            currencyUid = inr.uid,
            settlementAccountUid = settlementAcc.uid
        )
        repository.saveAccount(settlementAcc)
        repository.saveAccount(creditCard)

        // Delete settlement account
        repository.deleteAccount(settlementAcc)

        // Card should have its settlementAccountUid cleared to null
        val updatedCard = dao.getAccountWithCurrencyById("acc_card_with_settle")
        assertNotNull(updatedCard)
        assertNull(updatedCard!!.account.settlementAccountUid)
    }

    // =========================================================================
    // LIABILITY OPENING BALANCE & CREDIT CARD CALCULATION TESTS
    // =========================================================================

    @Test
    fun testLiabilityOpeningBalance_treatedAsDebtInNetWorth() = runTest {
        // Credit card starting with 10,000 opening debt balance
        val cardWithDebt = AccountEntity(
            uid = "cc_initial_debt",
            name = "Debt Card",
            accountType = AccountType.CREDIT_CARD,
            currencyUid = inr.uid,
            openingBalance = 10000.0
        )
        val savings = AccountEntity(
            uid = "acc_savings_init",
            name = "Savings",
            accountType = AccountType.SAVINGS,
            currencyUid = inr.uid,
            openingBalance = 50000.0
        )
        repository.saveAccount(cardWithDebt)
        repository.saveAccount(savings)

        val nw = repository.netWorthSummary.first()
        assertNotNull(nw)

        // Total Assets = 50,000 INR
        assertEquals(50000.0, nw!!.totalAssetsBase, 0.001)

        // Total Liabilities = 10,000 INR
        assertEquals(10000.0, nw.totalLiabilitiesBase, 0.001)

        // Net Worth = 50,000 - 10,000 = 40,000 INR
        assertEquals(40000.0, nw.netWorthBase, 0.001)
    }

    @Test
    fun testCreditCardStatementEngine_includesOpeningBalanceInTotalOutstanding() {
        val card = AccountEntity(
            uid = "cc_engine_opening",
            name = "Card Opening",
            accountType = AccountType.CREDIT_CARD,
            currencyUid = inr.uid,
            openingBalance = 5000.0,
            statementDay = 15,
            paymentDueDay = 5,
            creditLimit = 50000.0
        )

        val summary = CreditCardStatementEngine.calculateStatement(
            card = card,
            transactions = emptyList(),
            currentDate = LocalDate.of(2026, 10, 20)
        )

        // Total outstanding must include the 5000 opening balance
        assertEquals(5000.0, summary.totalOutstanding, 0.001)
        assertEquals(45000.0, summary.availableCredit!!, 0.001)
        assertEquals(10, summary.creditUtilizationPercent!!)
    }

    // =========================================================================
    // NEGATIVE TEST CASES: STRICT ACCOUNT & CARD VALIDATION
    // =========================================================================

    @Test
    fun testSaveAccount_rejectsEmptyNameAndDuplicateNames() = runTest {
        // Empty name
        try {
            val emptyName = AccountEntity(
                uid = "acc_blank",
                name = "   ",
                accountType = AccountType.SAVINGS,
                currencyUid = inr.uid
            )
            repository.saveAccount(emptyName)
            fail("Expected IllegalArgumentException for blank name")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("cannot be empty", ignoreCase = true))
        }

        // Duplicate name
        val acc1 = AccountEntity("acc_1", "SBI Savings", AccountType.SAVINGS, inr.uid)
        repository.saveAccount(acc1)

        try {
            val acc2 = AccountEntity("acc_2", "sbi savings", AccountType.CASH, inr.uid)
            repository.saveAccount(acc2)
            fail("Expected IllegalArgumentException for duplicate account name")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("already exists", ignoreCase = true))
        }
    }

    @Test
    fun testSaveCreditCard_rejectsInvalidStatementAndDueDays() = runTest {
        // Statement day 0
        try {
            val badCard = AccountEntity(
                uid = "cc_bad_day",
                name = "Bad Day Card",
                accountType = AccountType.CREDIT_CARD,
                currencyUid = inr.uid,
                statementDay = 0
            )
            repository.saveAccount(badCard)
            fail("Expected IllegalArgumentException for statement day 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("between 1 and 31", ignoreCase = true))
        }

        // Statement day 32
        try {
            val badCard = AccountEntity(
                uid = "cc_bad_day_32",
                name = "Bad Day 32 Card",
                accountType = AccountType.CREDIT_CARD,
                currencyUid = inr.uid,
                statementDay = 32
            )
            repository.saveAccount(badCard)
            fail("Expected IllegalArgumentException for statement day 32")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("between 1 and 31", ignoreCase = true))
        }

        // Due day 35
        try {
            val badCard = AccountEntity(
                uid = "cc_bad_due",
                name = "Bad Due Card",
                accountType = AccountType.CREDIT_CARD,
                currencyUid = inr.uid,
                paymentDueDay = 35
            )
            repository.saveAccount(badCard)
            fail("Expected IllegalArgumentException for due day 35")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("between 1 and 31", ignoreCase = true))
        }
    }

    @Test
    fun testSaveCreditCard_rejectsNegativeCreditLimitAndSelfSettlement() = runTest {
        // Negative credit limit
        try {
            val badLimit = AccountEntity(
                uid = "cc_neg_limit",
                name = "Negative Limit Card",
                accountType = AccountType.CREDIT_CARD,
                currencyUid = inr.uid,
                creditLimit = -5000.0
            )
            repository.saveAccount(badLimit)
            fail("Expected IllegalArgumentException for negative limit")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("non-negative", ignoreCase = true))
        }

        // Settle from self
        try {
            val selfSettle = AccountEntity(
                uid = "cc_self_settle",
                name = "Self Settle Card",
                accountType = AccountType.CREDIT_CARD,
                currencyUid = inr.uid,
                settlementAccountUid = "cc_self_settle"
            )
            repository.saveAccount(selfSettle)
            fail("Expected IllegalArgumentException for self-settlement")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("cannot settle from itself", ignoreCase = true))
        }
    }

    @Test
    fun testNonCreditCard_automaticallyStripsCreditCardAttributes() = runTest {
        val savingsWithCardAttrs = AccountEntity(
            uid = "savings_strip",
            name = "Savings Strip",
            accountType = AccountType.SAVINGS,
            currencyUid = inr.uid,
            statementDay = 15,
            paymentDueDay = 5,
            creditLimit = 100000.0,
            settlementAccountUid = "some_acc"
        )
        repository.saveAccount(savingsWithCardAttrs)

        val retrieved = dao.getAccountWithCurrencyById("savings_strip")
        assertNotNull(retrieved)
        assertNull(retrieved!!.account.statementDay)
        assertNull(retrieved.account.paymentDueDay)
        assertNull(retrieved.account.creditLimit)
        assertNull(retrieved.account.settlementAccountUid)
    }

    // =========================================================================
    // CATEGORIES, CURRENCIES, AND TAGS CRUD & VALIDATION TESTS
    // =========================================================================

    @Test
    fun testCategoryCrud_validates2TierHierarchyAndTypeConsistency() = runTest {
        val rootExpense = CategoryEntity("cat_root_exp", "Shopping", CategoryType.EXPENSE)
        repository.saveCategory(rootExpense)

        val subExpense = CategoryEntity("cat_sub_exp", "Clothes", CategoryType.EXPENSE, parentUid = rootExpense.uid)
        repository.saveCategory(subExpense)

        // 3-tier nesting should fail
        try {
            val tier3 = CategoryEntity("cat_tier3", "Jeans", CategoryType.EXPENSE, parentUid = subExpense.uid)
            repository.saveCategory(tier3)
            fail("Expected IllegalArgumentException for 3rd tier category")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("2 tiers", ignoreCase = true))
        }

        // Parent type mismatch should fail
        try {
            val mismatchedChild = CategoryEntity("cat_mis_child", "Income Child", CategoryType.INCOME, parentUid = rootExpense.uid)
            repository.saveCategory(mismatchedChild)
            fail("Expected IllegalArgumentException for parent-child type mismatch")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("must match parent category type", ignoreCase = true))
        }
    }

    @Test
    fun testCurrencyValidation_rejectsNonPositiveRateAndProtectsMainCurrency() = runTest {
        // Zero rate
        try {
            val zeroRate = CurrencyEntity("curr_zero", "Zero", "ZER", "Z", 0.0)
            repository.saveCurrency(zeroRate)
            fail("Expected IllegalArgumentException for rate <= 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("greater than zero", ignoreCase = true))
        }

        // Cannot delete main currency
        try {
            repository.deleteCurrency(inr)
            fail("Expected IllegalArgumentException for deleting main currency")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("primary base currency", ignoreCase = true))
        }

        // Cannot delete currency in use by an active account
        val accUsd = AccountEntity("acc_usd_in_use", "USD Acc", AccountType.WALLET, usd.uid)
        dao.insertAccount(accUsd)

        try {
            repository.deleteCurrency(usd)
            fail("Expected IllegalArgumentException for deleting in-use currency")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("in use by", ignoreCase = true))
        }
    }

    @Test
    fun testTagCrud_normalizesNameAndCleansUpCrossRefsOnDelete() = runTest {
        val acc = AccountEntity("acc_tag_test", "Tag Test Acc", AccountType.SAVINGS, inr.uid)
        dao.insertAccount(acc)

        repository.saveTag(TagEntity("t1", "#Medical"))

        val allTags = repository.allTags.first()
        assertEquals(1, allTags.size)
        assertEquals("Medical", allTags.first().name) // Leading # stripped

        // Duplicate tag rejected
        try {
            repository.saveTag(TagEntity("t2", "medical"))
            fail("Expected IllegalArgumentException for duplicate tag")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("already exists", ignoreCase = true))
        }

        // Attach tag to a transaction
        val tx = TransactionEntity("tx_with_t1", TransactionType.EXPENSE, 50.0, 50.0, 1.0, inr.uid, acc.uid)
        repository.saveTransaction(tx, listOf(allTags.first()))
        assertTrue(dao.crossRefsFlow.value.any { it.tagUid == "t1" })

        // Delete tag
        repository.deleteTag(allTags.first())
        assertTrue(repository.allTags.first().isEmpty())

        // Cross refs must be cleaned up
        assertFalse(dao.crossRefsFlow.value.any { it.tagUid == "t1" })
    }
}
