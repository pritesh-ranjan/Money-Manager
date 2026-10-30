package com.moneymanager.app.domain

import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.data.local.entity.TransactionWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class NoteSuggestionEngineTest {

    private val inr = CurrencyEntity(
        uid = "CURR_INR",
        name = "Indian Rupee",
        isoCode = "INR",
        symbol = "₹",
        rateToBase = 1.0,
        isMainCurrency = true
    )

    private val account = AccountEntity(
        uid = "acc_1",
        name = "Savings",
        accountType = AccountType.SAVINGS,
        currencyUid = inr.uid
    )

    private fun createTx(
        note: String,
        categoryUid: String? = null,
        type: TransactionType = TransactionType.EXPENSE,
        timestamp: Long = 1000L,
        isDeleted: Boolean = false
    ): TransactionWithDetails {
        val tx = TransactionEntity(
            uid = UUID.randomUUID().toString(),
            type = type,
            accountAmount = 100.0,
            baseAmount = 100.0,
            exchangeRate = 1.0,
            currencyUid = inr.uid,
            accountUid = account.uid,
            categoryUid = categoryUid,
            note = note,
            timestamp = timestamp,
            isDeleted = isDeleted
        )
        return TransactionWithDetails(
            transaction = tx,
            account = account,
            currency = inr
        )
    }

    @Test
    fun `getSuggestions ranks prefix matches before substring matches`() {
        val txs = listOf(
            createTx("Dark room cafe", timestamp = 5000L),
            createTx("Corn cap", timestamp = 4000L),
            createTx("Cake", timestamp = 1000L),
            createTx("Cafe coffee day", timestamp = 2000L),
            createTx("Irctc cancellation fee", timestamp = 3000L),
            createTx("Carpe diem", timestamp = 1500L)
        )

        val suggestions = NoteSuggestionEngine.getSuggestions(txs, "Ca")

        // First 3 should start with "Ca"
        assertEquals(6, suggestions.size)
        assertTrue(suggestions[0].note.startsWith("Ca", ignoreCase = true))
        assertTrue(suggestions[1].note.startsWith("Ca", ignoreCase = true))
        assertTrue(suggestions[2].note.startsWith("Ca", ignoreCase = true))

        // Prefix group: Cafe coffee day (2000L), Carpe diem (1500L), Cake (1000L)
        val prefixNotes = suggestions.take(3).map { it.note }
        assertTrue(prefixNotes.contains("Cafe coffee day"))
        assertTrue(prefixNotes.contains("Carpe diem"))
        assertTrue(prefixNotes.contains("Cake"))

        // Substring group: Dark room cafe, Corn cap, Irctc cancellation fee
        val nonPrefixNotes = suggestions.drop(3).map { it.note }
        assertTrue(nonPrefixNotes.contains("Dark room cafe"))
        assertTrue(nonPrefixNotes.contains("Corn cap"))
        assertTrue(nonPrefixNotes.contains("Irctc cancellation fee"))
    }

    @Test
    fun `getSuggestions excludes exact match and deleted transactions`() {
        val txs = listOf(
            createTx("Swiggy", timestamp = 1000L),
            createTx("Swiggy Instamart", timestamp = 2000L),
            createTx("Swiggy Dineout", timestamp = 3000L, isDeleted = true)
        )

        val suggestions = NoteSuggestionEngine.getSuggestions(txs, "swiggy")

        // "Swiggy" is exact match -> excluded. "Swiggy Dineout" is deleted -> excluded.
        assertEquals(1, suggestions.size)
        assertEquals("Swiggy Instamart", suggestions.first().note)
    }

    @Test
    fun `getSuggestions returns empty list for blank query`() {
        val txs = listOf(createTx("Zomato"))
        assertEquals(0, NoteSuggestionEngine.getSuggestions(txs, "").size)
        assertEquals(0, NoteSuggestionEngine.getSuggestions(txs, "   ").size)
    }

    @Test
    fun `getSuggestions remembers last used category uid per transaction type`() {
        val txs = listOf(
            createTx("Uber", categoryUid = "cat_transport", type = TransactionType.EXPENSE, timestamp = 2000L),
            createTx("Uber", categoryUid = "cat_old_transport", type = TransactionType.EXPENSE, timestamp = 1000L),
            createTx("Salary", categoryUid = "cat_salary", type = TransactionType.INCOME, timestamp = 3000L)
        )

        val suggestions = NoteSuggestionEngine.getSuggestions(txs, "Ub")
        assertEquals(1, suggestions.size)
        val uber = suggestions.first()
        assertEquals("Uber", uber.note)
        assertEquals(2, uber.usageCount)
        assertEquals("cat_transport", uber.lastExpenseCategoryUid)
    }
}
