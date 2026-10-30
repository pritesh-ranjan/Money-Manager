package com.moneymanager.app.domain

import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryType
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.data.local.entity.TagEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType

object DomainValidator {

    fun validateTransaction(
        transaction: TransactionEntity,
        sourceAccount: AccountEntity?,
        destAccount: AccountEntity? = null,
        category: CategoryEntity? = null
    ) {
        require(transaction.uid.isNotBlank()) { "Transaction UID cannot be blank" }
        require(sourceAccount != null) { "Source account does not exist" }
        require(!sourceAccount.isArchived) { "Cannot create or modify transaction for an archived account" }
        require(transaction.exchangeRate.isFinite() && transaction.exchangeRate > 0.0) {
            "Exchange rate must be positive and finite"
        }

        when (transaction.type) {
            TransactionType.EXPENSE, TransactionType.INCOME -> {
                require(transaction.accountAmount.isFinite() && transaction.accountAmount > 0.0) {
                    "Amount must be strictly greater than zero"
                }
                require(transaction.toAccountUid == null && transaction.toAccountAmount == null) {
                    "Destination account is only applicable for transfers"
                }
                if (category != null) {
                    require(!category.isDeleted) { "Cannot assign a deleted category" }
                    val expectedCategoryType = if (transaction.type == TransactionType.EXPENSE) {
                        CategoryType.EXPENSE
                    } else {
                        CategoryType.INCOME
                    }
                    require(category.type == expectedCategoryType) {
                        "Category '${category.name}' (${category.type}) does not match transaction type ${transaction.type}"
                    }
                }
            }
            TransactionType.TRANSFER -> {
                require(transaction.accountAmount.isFinite() && transaction.accountAmount > 0.0) {
                    "Transfer amount must be strictly greater than zero"
                }
                require(!transaction.toAccountUid.isNullExcludingBlank()) {
                    "Transfer requires a valid destination account"
                }
                val toUid = transaction.toAccountUid!!
                require(toUid != transaction.accountUid) {
                    "Cannot transfer funds to the same account"
                }
                require(destAccount != null) { "Destination account does not exist" }
                require(!destAccount.isArchived) { "Cannot transfer funds to an archived account" }
                require(transaction.categoryUid == null) {
                    "Transfers cannot have an expense/income category assigned"
                }
            }
            TransactionType.ADJUSTMENT -> {
                require(transaction.accountAmount.isFinite() && Math.abs(transaction.accountAmount) >= 0.0001) {
                    "Adjustment amount cannot be zero"
                }
                require(transaction.toAccountUid == null && transaction.toAccountAmount == null) {
                    "Adjustments cannot have a destination account"
                }
                require(transaction.categoryUid == null) {
                    "Adjustments cannot have a category assigned"
                }
            }
        }
    }

    fun validateAccount(
        account: AccountEntity,
        existingAccounts: List<AccountEntity>,
        currencyExists: Boolean
    ) {
        val trimmedName = account.name.trim()
        require(trimmedName.isNotEmpty()) { "Account name cannot be empty" }
        require(account.uid.isNotBlank()) { "Account UID cannot be blank" }
        require(currencyExists) { "Selected currency does not exist" }
        require(account.openingBalance.isFinite()) { "Opening balance must be finite" }

        val duplicateName = existingAccounts.any {
            it.uid != account.uid && !it.isArchived && it.name.trim().equals(trimmedName, ignoreCase = true)
        }
        require(!duplicateName) { "An active account with name '$trimmedName' already exists" }

        if (account.accountType == AccountType.CREDIT_CARD) {
            account.statementDay?.let {
                require(it in 1..31) { "Statement day must be between 1 and 31 (got $it)" }
            }
            account.paymentDueDay?.let {
                require(it in 1..31) { "Payment due day must be between 1 and 31 (got $it)" }
            }
            account.creditLimit?.let {
                require(it.isFinite() && it >= 0.0) { "Credit limit must be non-negative (got $it)" }
            }
            account.settlementAccountUid?.let { settlementUid ->
                require(settlementUid != account.uid) { "Credit card cannot settle from itself" }
                val settlementAcc = existingAccounts.firstOrNull { it.uid == settlementUid }
                settlementAcc?.let {
                    require(!it.accountType.isLiability) {
                        "Settlement account cannot be a liability (got ${it.accountType})"
                    }
                }
            }
        }

        account.linkedParentAccountUid?.let { parentUid ->
            require(parentUid != account.uid) { "Account cannot link to itself as parent" }
        }
    }

    fun validateCategory(
        category: CategoryEntity,
        existingCategories: List<CategoryEntity>
    ) {
        val trimmedName = category.name.trim()
        require(trimmedName.isNotEmpty()) { "Category name cannot be empty" }
        require(category.uid.isNotBlank()) { "Category UID cannot be blank" }

        val duplicate = existingCategories.any {
            it.uid != category.uid &&
            !it.isDeleted &&
            it.type == category.type &&
            it.parentUid == category.parentUid &&
            it.name.trim().equals(trimmedName, ignoreCase = true)
        }
        require(!duplicate) { "Category '$trimmedName' already exists in this section" }

        if (category.parentUid != null) {
            val parent = existingCategories.firstOrNull { it.uid == category.parentUid }
            require(parent != null && !parent.isDeleted) { "Parent category does not exist" }
            require(parent.parentUid == null) { "Nested subcategories beyond 2 tiers are not supported" }
            require(parent.type == category.type) {
                "Subcategory type (${category.type}) must match parent category type (${parent.type})"
            }
        }
    }

    fun validateCurrency(
        currency: CurrencyEntity,
        existingCurrencies: List<CurrencyEntity>
    ) {
        require(currency.uid.isNotBlank()) { "Currency UID cannot be blank" }
        require(currency.name.trim().isNotEmpty()) { "Currency name cannot be empty" }
        require(currency.isoCode.trim().isNotEmpty()) { "Currency code cannot be empty" }
        require(currency.symbol.trim().isNotEmpty()) { "Currency symbol cannot be empty" }
        require(currency.rateToBase.isFinite() && currency.rateToBase > 0.0) {
            "Currency exchange rate must be strictly greater than zero"
        }
        require(currency.decimalPlaces in 0..4) { "Decimal places must be between 0 and 4" }

        val duplicate = existingCurrencies.any {
            it.uid != currency.uid && it.isoCode.trim().equals(currency.isoCode.trim(), ignoreCase = true)
        }
        require(!duplicate) { "A currency with code '${currency.isoCode}' already exists" }
    }

    fun validateTag(
        name: String,
        existingTags: List<TagEntity>
    ): String {
        val cleanName = if (name.startsWith("#")) name.substring(1).trim() else name.trim()
        require(cleanName.isNotEmpty()) { "Tag name cannot be empty" }
        val duplicate = existingTags.any { it.name.equals(cleanName, ignoreCase = true) }
        require(!duplicate) { "Tag '#$cleanName' already exists" }
        return cleanName
    }

    private fun String?.isNullExcludingBlank(): Boolean = this == null || this.trim().isEmpty()
}
