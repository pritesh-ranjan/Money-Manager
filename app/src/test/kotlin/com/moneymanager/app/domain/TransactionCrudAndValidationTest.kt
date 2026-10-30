package com.moneymanager.app.domain

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
import java.util.UUID

class TransactionCrudAndValidationTest {

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

    private val scapia = CurrencyEntity(
        uid = "CURR_SCAPIA",
        name = "Scapia Coins",
        isoCode = "SCAPIA",
        symbol = "🪙",
        rateToBase = 0.20,
        isCustom = true,
        decimalPlaces = 0
    )

    private val savingsAccount = AccountEntity(
        uid = "acc_savings",
        name = "HDFC Savings",
        accountType = AccountType.SAVINGS,
        currencyUid = inr.uid,
        openingBalance = 50000.0
    )

    private val usdAccount = AccountEntity(
        uid = "acc_usd",
        name = "USD Travel Card",
        accountType = AccountType.WALLET,
        currencyUid = usd.uid,
        openingBalance = 0.0
    )

    private val creditCard = AccountEntity(
        uid = "acc_cc",
        name = "Infinia Card",
        accountType = AccountType.CREDIT_CARD,
        currencyUid = inr.uid,
        openingBalance = 0.0,
        statementDay = 15,
        paymentDueDay = 5,
        creditLimit = 300000.0
    )

    private val foodCategory = CategoryEntity(
        uid = "cat_food",
        name = "Food & Dining",
        type = CategoryType.EXPENSE,
        icon = "🍜"
    )

    private val salaryCategory = CategoryEntity(
        uid = "cat_salary",
        name = "Salary",
        type = CategoryType.INCOME,
        icon = "💰"
    )

    @Before
    fun setup() = runTest {
        dao = FakeMoneyManagerDao()
        repository = MoneyManagerRepository(dao)

        dao.insertCurrencies(listOf(inr, usd, scapia))
        dao.insertAccounts(listOf(savingsAccount, usdAccount, creditCard))
        dao.insertCategories(listOf(foodCategory, salaryCategory))
    }

    // =========================================================================
    // POSITIVE TEST CASES: ADDING, UPDATING, DELETING TRANSACTIONS
    // =========================================================================

    @Test
    fun testAddExpenseTransaction_calculatesBaseAmountAndAttachesTags() = runTest {
        val tagFood = TagEntity("tag_food", "Dining")
        val tx = TransactionEntity(
            uid = "tx_exp_1",
            type = TransactionType.EXPENSE,
            accountAmount = 1500.0,
            baseAmount = 1500.0,
            exchangeRate = 1.0,
            currencyUid = inr.uid,
            accountUid = savingsAccount.uid,
            categoryUid = foodCategory.uid,
            note = "Dinner"
        )

        repository.saveTransaction(tx, listOf(tagFood))

        val retrieved = repository.allTransactions.first().firstOrNull { it.transaction.uid == "tx_exp_1" }
        assertNotNull(retrieved)
        assertEquals(TransactionType.EXPENSE, retrieved!!.transaction.type)
        assertEquals(1500.0, retrieved.transaction.accountAmount, 0.001)
        assertEquals(1500.0, retrieved.transaction.baseAmount, 0.001)
        assertEquals("Dinner", retrieved.transaction.note)
        assertEquals(foodCategory.uid, retrieved.category?.uid)
        assertEquals(1, retrieved.tags.size)
        assertEquals("Dining", retrieved.tags.first().name)

        // Net worth should decrease by 1,500 from initial 50,000 to 48,500
        val nw = repository.netWorthSummary.first()
        assertNotNull(nw)
        assertEquals(48500.0, nw!!.netWorthBase, 0.001)
    }

    @Test
    fun testAddForeignCurrencyIncome_convertsToBaseAmountCorrectly() = runTest {
        // Earn $100 in USD account @ rate 84.0 -> Base Amount = 8,400 INR
        val tx = TransactionEntity(
            uid = "tx_inc_usd",
            type = TransactionType.INCOME,
            accountAmount = 100.0,
            baseAmount = 8400.0,
            exchangeRate = 84.0,
            currencyUid = usd.uid,
            accountUid = usdAccount.uid,
            categoryUid = salaryCategory.uid,
            note = "Freelance"
        )

        repository.saveTransaction(tx)

        val retrieved = dao.getTransactionById("tx_inc_usd")
        assertNotNull(retrieved)
        assertEquals(100.0, retrieved!!.transaction.accountAmount, 0.001)
        assertEquals(8400.0, retrieved.transaction.baseAmount, 0.001)
        assertEquals(usd.uid, retrieved.currency.uid)

        // Net worth: 50,000 INR + 8,400 INR = 58,400 INR
        val nw = repository.netWorthSummary.first()
        assertEquals(58400.0, nw!!.netWorthBase, 0.001)
    }

    @Test
    fun testCrossCurrencyTransfer_autoCalculatesToAccountAmount() = runTest {
        // Transfer 8,400 INR from Savings to USD Account (Rate = 84.0)
        // Destination amount should automatically compute to $100.0 USD
        val transferTx = TransactionEntity(
            uid = "tx_xfer_cross",
            type = TransactionType.TRANSFER,
            accountAmount = 8400.0,
            baseAmount = 8400.0,
            exchangeRate = 1.0,
            currencyUid = inr.uid,
            accountUid = savingsAccount.uid,
            toAccountUid = usdAccount.uid,
            toAccountAmount = null // Trigger automatic rate conversion
        )

        repository.saveTransaction(transferTx)

        val saved = dao.getTransactionById("tx_xfer_cross")
        assertNotNull(saved)
        assertEquals(8400.0, saved!!.transaction.accountAmount, 0.001)
        assertEquals(8400.0, saved.transaction.baseAmount, 0.001)
        assertEquals(100.0, saved.transaction.toAccountAmount!!, 0.001) // $100 received
        assertNull(saved.transaction.categoryUid)

        // Net worth should remain 50,000 INR (no loss/drift on transfer)
        val nw = repository.netWorthSummary.first()
        assertEquals(50000.0, nw!!.netWorthBase, 0.001)
    }

    @Test
    fun testUpdateTransaction_modifiesValuesAndReplacesTagsCleanly() = runTest {
        val initialTag = TagEntity("tag_1", "Initial")
        val tx = TransactionEntity(
            uid = "tx_update_me",
            type = TransactionType.EXPENSE,
            accountAmount = 500.0,
            baseAmount = 500.0,
            currencyUid = inr.uid,
            accountUid = savingsAccount.uid,
            note = "Old Note"
        )
        repository.saveTransaction(tx, listOf(initialTag))

        val updatedTag1 = TagEntity("tag_2", "UpdatedA")
        val updatedTag2 = TagEntity("tag_3", "UpdatedB")
        val updatedTx = tx.copy(
            accountAmount = 750.0,
            note = "New Note"
        )
        repository.saveTransaction(updatedTx, listOf(updatedTag1, updatedTag2))

        val retrieved = dao.getTransactionById("tx_update_me")
        assertNotNull(retrieved)
        assertEquals(750.0, retrieved!!.transaction.accountAmount, 0.001)
        assertEquals(750.0, retrieved.transaction.baseAmount, 0.001)
        assertEquals("New Note", retrieved.transaction.note)

        // Ensure old tag is gone and replaced
        assertEquals(2, retrieved.tags.size)
        val tagNames = retrieved.tags.map { it.name }.toSet()
        assertTrue(tagNames.contains("UpdatedA"))
        assertTrue(tagNames.contains("UpdatedB"))
        assertFalse(tagNames.contains("Initial"))
    }

    @Test
    fun testUpdateTransactionType_fromTransferToExpense_clearsToAccount() = runTest {
        val transferTx = TransactionEntity(
            uid = "tx_type_switch",
            type = TransactionType.TRANSFER,
            accountAmount = 2000.0,
            baseAmount = 2000.0,
            currencyUid = inr.uid,
            accountUid = savingsAccount.uid,
            toAccountUid = creditCard.uid,
            toAccountAmount = 2000.0
        )
        repository.saveTransaction(transferTx)

        // Switch to Expense
        val convertedExpense = transferTx.copy(
            type = TransactionType.EXPENSE,
            categoryUid = foodCategory.uid
        )
        repository.saveTransaction(convertedExpense)

        val retrieved = dao.getTransactionById("tx_type_switch")
        assertNotNull(retrieved)
        assertEquals(TransactionType.EXPENSE, retrieved!!.transaction.type)
        assertNull(retrieved.transaction.toAccountUid)
        assertNull(retrieved.transaction.toAccountAmount)
        assertEquals(foodCategory.uid, retrieved.category?.uid)
    }

    @Test
    fun testDeleteTransaction_softDeletesAndRemovesTags() = runTest {
        val tag = TagEntity("tag_temp", "Temporary")
        val tx = TransactionEntity(
            uid = "tx_to_delete",
            type = TransactionType.EXPENSE,
            accountAmount = 1000.0,
            baseAmount = 1000.0,
            currencyUid = inr.uid,
            accountUid = savingsAccount.uid
        )
        repository.saveTransaction(tx, listOf(tag))

        // Pre-condition: active
        assertNotNull(dao.getTransactionById("tx_to_delete"))

        // Delete
        repository.deleteTransaction("tx_to_delete")

        // Active lookup should return null
        assertNull(dao.getTransactionById("tx_to_delete"))
        val allActive = repository.allTransactions.first()
        assertTrue(allActive.none { it.transaction.uid == "tx_to_delete" })

        // Tag cross refs for deleted tx must be cleaned up
        val remainingRefs = dao.crossRefsFlow.value.filter { it.transactionUid == "tx_to_delete" }
        assertTrue(remainingRefs.isEmpty())

        // Net worth should not subtract deleted expense
        val nw = repository.netWorthSummary.first()
        assertEquals(50000.0, nw!!.netWorthBase, 0.001)
    }

    // =========================================================================
    // NEGATIVE TEST CASES: STRICT VALIDATION REJECTING AMBIGUOUS INPUTS
    // =========================================================================

    @Test
    fun testSaveTransaction_rejectsZeroAndNegativeAmounts() = runTest {
        // Zero amount
        try {
            val txZero = TransactionEntity(
                uid = UUID.randomUUID().toString(),
                type = TransactionType.EXPENSE,
                accountAmount = 0.0,
                baseAmount = 0.0,
                currencyUid = inr.uid,
                accountUid = savingsAccount.uid
            )
            repository.saveTransaction(txZero)
            fail("Expected IllegalArgumentException for 0 amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("greater than zero", ignoreCase = true))
        }

        // Negative amount on Expense
        try {
            val txNeg = TransactionEntity(
                uid = UUID.randomUUID().toString(),
                type = TransactionType.EXPENSE,
                accountAmount = -50.0,
                baseAmount = -50.0,
                currencyUid = inr.uid,
                accountUid = savingsAccount.uid
            )
            repository.saveTransaction(txNeg)
            fail("Expected IllegalArgumentException for negative amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("greater than zero", ignoreCase = true))
        }
    }

    @Test
    fun testSaveTransaction_rejectsNaNAndInfiniteAmounts() = runTest {
        try {
            val txNan = TransactionEntity(
                uid = UUID.randomUUID().toString(),
                type = TransactionType.EXPENSE,
                accountAmount = Double.NaN,
                baseAmount = 100.0,
                currencyUid = inr.uid,
                accountUid = savingsAccount.uid
            )
            repository.saveTransaction(txNan)
            fail("Expected IllegalArgumentException for NaN amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("greater than zero", ignoreCase = true))
        }

        try {
            val txInf = TransactionEntity(
                uid = UUID.randomUUID().toString(),
                type = TransactionType.INCOME,
                accountAmount = Double.POSITIVE_INFINITY,
                baseAmount = 100.0,
                currencyUid = inr.uid,
                accountUid = savingsAccount.uid
            )
            repository.saveTransaction(txInf)
            fail("Expected IllegalArgumentException for Infinite amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("greater than zero", ignoreCase = true))
        }
    }

    @Test
    fun testSaveTransaction_rejectsTransferToSameAccount() = runTest {
        try {
            val selfTransfer = TransactionEntity(
                uid = UUID.randomUUID().toString(),
                type = TransactionType.TRANSFER,
                accountAmount = 500.0,
                baseAmount = 500.0,
                currencyUid = inr.uid,
                accountUid = savingsAccount.uid,
                toAccountUid = savingsAccount.uid // Same account!
            )
            repository.saveTransaction(selfTransfer)
            fail("Expected IllegalArgumentException for self-transfer")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("same account", ignoreCase = true))
        }
    }

    @Test
    fun testSaveTransaction_rejectsTransferWithMissingOrInvalidDestinationAccount() = runTest {
        // Missing toAccountUid
        try {
            val missingTo = TransactionEntity(
                uid = UUID.randomUUID().toString(),
                type = TransactionType.TRANSFER,
                accountAmount = 500.0,
                baseAmount = 500.0,
                currencyUid = inr.uid,
                accountUid = savingsAccount.uid,
                toAccountUid = null
            )
            repository.saveTransaction(missingTo)
            fail("Expected IllegalArgumentException for missing destination account")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("destination account", ignoreCase = true))
        }

        // Non-existent toAccountUid
        try {
            val nonExistentTo = TransactionEntity(
                uid = UUID.randomUUID().toString(),
                type = TransactionType.TRANSFER,
                accountAmount = 500.0,
                baseAmount = 500.0,
                currencyUid = inr.uid,
                accountUid = savingsAccount.uid,
                toAccountUid = "non_existent_account_123"
            )
            repository.saveTransaction(nonExistentTo)
            fail("Expected IllegalArgumentException for non-existent destination account")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Destination account does not exist", ignoreCase = true))
        }
    }

    @Test
    fun testSaveTransaction_rejectsMismatchedCategoryType() = runTest {
        // Try attaching food category (EXPENSE) to an INCOME transaction
        try {
            val txIncomeWithExpenseCat = TransactionEntity(
                uid = UUID.randomUUID().toString(),
                type = TransactionType.INCOME,
                accountAmount = 2000.0,
                baseAmount = 2000.0,
                currencyUid = inr.uid,
                accountUid = savingsAccount.uid,
                categoryUid = foodCategory.uid // Mismatched!
            )
            repository.saveTransaction(txIncomeWithExpenseCat)
            fail("Expected IllegalArgumentException for category type mismatch")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("does not match transaction type", ignoreCase = true))
        }

        // Try attaching salary category (INCOME) to an EXPENSE transaction
        try {
            val txExpenseWithIncomeCat = TransactionEntity(
                uid = UUID.randomUUID().toString(),
                type = TransactionType.EXPENSE,
                accountAmount = 200.0,
                baseAmount = 200.0,
                currencyUid = inr.uid,
                accountUid = savingsAccount.uid,
                categoryUid = salaryCategory.uid // Mismatched!
            )
            repository.saveTransaction(txExpenseWithIncomeCat)
            fail("Expected IllegalArgumentException for category type mismatch")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("does not match transaction type", ignoreCase = true))
        }
    }

    @Test
    fun testSaveTransaction_rejectsDeletedCategory() = runTest {
        val deletedCat = CategoryEntity("cat_deleted", "Old Cat", CategoryType.EXPENSE, isDeleted = true)
        dao.insertCategory(deletedCat)

        try {
            val tx = TransactionEntity(
                uid = UUID.randomUUID().toString(),
                type = TransactionType.EXPENSE,
                accountAmount = 100.0,
                baseAmount = 100.0,
                currencyUid = inr.uid,
                accountUid = savingsAccount.uid,
                categoryUid = deletedCat.uid
            )
            repository.saveTransaction(tx)
            fail("Expected IllegalArgumentException for deleted category")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("deleted category", ignoreCase = true))
        }
    }

    @Test
    fun testSaveTransaction_rejectsArchivedAccount() = runTest {
        val archivedAcc = AccountEntity("acc_archived", "Old Bank", AccountType.SAVINGS, inr.uid, isArchived = true)
        dao.insertAccount(archivedAcc)

        try {
            val tx = TransactionEntity(
                uid = UUID.randomUUID().toString(),
                type = TransactionType.EXPENSE,
                accountAmount = 100.0,
                baseAmount = 100.0,
                currencyUid = inr.uid,
                accountUid = archivedAcc.uid
            )
            repository.saveTransaction(tx)
            fail("Expected IllegalArgumentException for archived account")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("archived account", ignoreCase = true))
        }
    }

    @Test
    fun testDeleteTransaction_rejectsNonExistentOrAlreadyDeletedTransaction() = runTest {
        // Non-existent
        try {
            repository.deleteTransaction("non_existent_tx_xyz")
            fail("Expected IllegalArgumentException for deleting non-existent tx")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("not found or already deleted", ignoreCase = true))
        }

        // Already deleted
        val tx = TransactionEntity(
            uid = "tx_delete_twice",
            type = TransactionType.EXPENSE,
            accountAmount = 250.0,
            baseAmount = 250.0,
            currencyUid = inr.uid,
            accountUid = savingsAccount.uid
        )
        repository.saveTransaction(tx)
        repository.deleteTransaction("tx_delete_twice")

        try {
            repository.deleteTransaction("tx_delete_twice")
            fail("Expected IllegalArgumentException for deleting already-deleted tx")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("not found or already deleted", ignoreCase = true))
        }
    }
}
