package com.moneymanager.app.ui.components

import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.AccountWithCurrency

enum class AccountFilterGroup(val label: String, val types: Set<AccountType>) {
    BANK("Bank & Savings", setOf(AccountType.SAVINGS, AccountType.UPI_DEBIT)),
    CASH("Cash & Wallets", setOf(AccountType.CASH, AccountType.WALLET)),
    CARDS("Credit Cards", setOf(AccountType.CREDIT_CARD)),
    INVEST("Investments & Equity", setOf(AccountType.INVESTMENT, AccountType.MUTUAL_FUNDS, AccountType.EQUITY)),
    OTHER("Loans & Other", setOf(AccountType.LOAN, AccountType.OTHER))
}

object FilterSummaryFormatter {

    /**
     * Groups active (non-archived) accounts into standard One UI banking categories.
     * Archived accounts are completely hidden per user preference.
     */
    fun groupAccountsForFilter(
        accounts: List<AccountWithCurrency>
    ): Map<AccountFilterGroup, List<AccountWithCurrency>> {
        val nonArchived = accounts.filter { !it.account.isArchived }
        val result = LinkedHashMap<AccountFilterGroup, List<AccountWithCurrency>>()
        AccountFilterGroup.values().forEach { group ->
            val matching = nonArchived.filter { it.account.accountType in group.types }
            if (matching.isNotEmpty()) {
                result[group] = matching
            }
        }
        return result
    }

    /**
     * Generates a concise summary label for filter rows.
     * e.g., "All accounts" or "ICICI Savings, HDFC" or "ICICI Savings, HDFC +3"
     */
    fun summarizeSelection(
        names: List<String>,
        emptyLabel: String,
        maxNames: Int = 2
    ): String {
        if (names.isEmpty()) return emptyLabel
        return if (names.size <= maxNames) {
            names.joinToString(", ")
        } else {
            val shown = names.take(maxNames).joinToString(", ")
            "$shown +${names.size - maxNames}"
        }
    }

    /**
     * Compact single-chip label for active filter bar.
     * e.g., if 1 account selected: "ICICI Savings"
     * if multiple accounts selected: "4 accounts"
     */
    fun compactChipLabel(
        names: List<String>,
        singularNoun: String,
        pluralNoun: String
    ): String {
        return when {
            names.isEmpty() -> ""
            names.size == 1 -> names.first()
            else -> "${names.size} $pluralNoun"
        }
    }
}
