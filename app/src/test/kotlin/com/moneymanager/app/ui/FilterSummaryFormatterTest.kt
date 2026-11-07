package com.moneymanager.app.ui

import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.AccountWithCurrency
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.ui.components.AccountFilterGroup
import com.moneymanager.app.ui.components.FilterSummaryFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterSummaryFormatterTest {

    private val inrCurrency = CurrencyEntity(
        uid = "curr_inr",
        name = "Indian Rupee",
        isoCode = "INR",
        symbol = "₹",
        rateToBase = 1.0,
        isMainCurrency = true
    )

    private fun createAccount(
        uid: String,
        name: String,
        type: AccountType,
        isArchived: Boolean = false
    ): AccountWithCurrency {
        return AccountWithCurrency(
            account = AccountEntity(
                uid = uid,
                name = name,
                accountType = type,
                currencyUid = "curr_inr",
                isArchived = isArchived
            ),
            currency = inrCurrency
        )
    }

    @Test
    fun testAllAccountTypesAreCoveredInGroups() {
        val allTypes = AccountType.values().toSet()
        val mappedTypes = AccountFilterGroup.values().flatMap { it.types }.toSet()
        assertEquals(allTypes, mappedTypes)
    }

    @Test
    fun testGroupAccountsExcludesArchivedAccounts() {
        val accounts = listOf(
            createAccount("1", "ICICI Bank", AccountType.SAVINGS, isArchived = false),
            createAccount("2", "Old Bank", AccountType.SAVINGS, isArchived = true),
            createAccount("3", "Credit Card", AccountType.CREDIT_CARD, isArchived = false),
            createAccount("4", "Closed Card", AccountType.CREDIT_CARD, isArchived = true),
            createAccount("5", "Wallet", AccountType.WALLET, isArchived = false)
        )

        val grouped = FilterSummaryFormatter.groupAccountsForFilter(accounts)

        val bankGroup = grouped[AccountFilterGroup.BANK]
        assertEquals(1, bankGroup?.size)
        assertEquals("ICICI Bank", bankGroup?.first()?.account?.name)

        val cardsGroup = grouped[AccountFilterGroup.CARDS]
        assertEquals(1, cardsGroup?.size)
        assertEquals("Credit Card", cardsGroup?.first()?.account?.name)

        // Make sure no archived accounts exist anywhere in the grouped output
        val allGroupedAccounts = grouped.values.flatten()
        assertFalse(allGroupedAccounts.any { it.account.isArchived })
        assertEquals(3, allGroupedAccounts.size)
    }

    @Test
    fun testSummarizeSelection() {
        assertEquals("All accounts", FilterSummaryFormatter.summarizeSelection(emptyList(), "All accounts"))
        assertEquals("ICICI Savings", FilterSummaryFormatter.summarizeSelection(listOf("ICICI Savings"), "All accounts"))
        assertEquals("ICICI Savings, HDFC", FilterSummaryFormatter.summarizeSelection(listOf("ICICI Savings", "HDFC"), "All accounts"))
        assertEquals("ICICI Savings, HDFC +1", FilterSummaryFormatter.summarizeSelection(listOf("ICICI Savings", "HDFC", "SBI"), "All accounts"))
        assertEquals("A, B +3", FilterSummaryFormatter.summarizeSelection(listOf("A", "B", "C", "D", "E"), "All accounts"))
    }

    @Test
    fun testCompactChipLabel() {
        assertEquals("", FilterSummaryFormatter.compactChipLabel(emptyList(), "account", "accounts"))
        assertEquals("ICICI Savings", FilterSummaryFormatter.compactChipLabel(listOf("ICICI Savings"), "account", "accounts"))
        assertEquals("2 accounts", FilterSummaryFormatter.compactChipLabel(listOf("ICICI Savings", "HDFC"), "account", "accounts"))
        assertEquals("5 accounts", FilterSummaryFormatter.compactChipLabel(listOf("A", "B", "C", "D", "E"), "account", "accounts"))
    }
}
