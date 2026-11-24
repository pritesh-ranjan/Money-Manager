package com.moneymanager.app.data

import com.moneymanager.app.data.local.dao.InsightsDao
import com.moneymanager.app.data.local.dao.MoneyManagerDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.AccountTypeDefaults
import com.moneymanager.app.data.local.entity.AccountTypeEntity
import com.moneymanager.app.data.local.entity.AccountWithCurrency
import com.moneymanager.app.data.local.entity.resolvedAccountTypeUid
import com.moneymanager.app.data.local.entity.resolvedIsLiability
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryType
import com.moneymanager.app.data.local.entity.CategoryWithSubcategories
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.data.local.entity.InsightWithTransactions
import com.moneymanager.app.data.local.entity.InstitutionEntity
import com.moneymanager.app.data.local.entity.requiredInstitutionKind
import com.moneymanager.app.data.local.entity.RecurrenceDecision
import com.moneymanager.app.data.local.entity.RecurrenceDecisionEntity
import com.moneymanager.app.data.local.entity.TagEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.data.local.entity.TransactionWithDetails
import com.moneymanager.app.domain.CreditCardCycleSummary
import com.moneymanager.app.domain.CreditCardStatementEngine
import android.content.Context
import android.net.Uri
import com.moneymanager.app.domain.BackupRestoreResult
import com.moneymanager.app.domain.SqliteBackupManager
import com.moneymanager.app.domain.NetWorthCalculator
import com.moneymanager.app.domain.NetWorthSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import com.moneymanager.app.domain.DomainValidator
import com.moneymanager.app.domain.statement.core.StatementEntityMapper
import java.io.File
import java.time.LocalDate
import java.util.UUID

enum class AccountDeleteOutcome {
    DELETED,
    ARCHIVED
}

class MoneyManagerRepository(
    private val dao: MoneyManagerDao,
    private val insightsDao: InsightsDao? = null
) {

    // --- Currencies ---
    val allCurrencies: Flow<List<CurrencyEntity>> = dao.getAllCurrencies()

    suspend fun saveCurrency(currency: CurrencyEntity) {
        val existing = dao.getAllCurrenciesRaw()
        DomainValidator.validateCurrency(currency, existing)
        val isExisting = existing.any { it.uid == currency.uid }
        if (isExisting) {
            dao.updateCurrency(currency)
        } else {
            dao.insertCurrency(currency)
        }
    }

    suspend fun deleteCurrency(currency: CurrencyEntity) {
        require(!currency.isMainCurrency) { "Cannot delete primary base currency" }
        val count = dao.countAccountsUsingCurrency(currency.uid)
        require(count == 0) { "Cannot delete currency '${currency.name}' because it is in use by $count account(s)" }
        dao.deleteCurrency(currency)
    }

    // --- Account Types ---
    val allAccountTypes: Flow<List<AccountTypeEntity>> = dao.getAllAccountTypes()

    suspend fun ensureDefaultAccountTypes() {
        if (dao.getAllAccountTypesRaw().isEmpty()) {
            dao.insertAccountTypes(AccountTypeDefaults.defaultEntities())
        }
    }

    suspend fun saveAccountType(accountType: AccountTypeEntity) {
        val existing = dao.getAllAccountTypesRaw()
        val existingMatch = existing.firstOrNull { it.uid == accountType.uid }
        val sanitized = accountType.copy(
            name = accountType.name.trim(),
            isLiability = if (accountType.baseType == AccountType.CREDIT_CARD) true else accountType.isLiability,
            isSystemDefault = existingMatch?.isSystemDefault ?: accountType.isSystemDefault
        )
        DomainValidator.validateAccountType(sanitized, existing)

        if (existingMatch != null) {
            dao.updateAccountType(sanitized)
            dao.updateAccountBaseTypeForTypeUid(sanitized.uid, sanitized.baseType)
            if (sanitized.baseType != AccountType.CREDIT_CARD) {
                dao.clearCreditCardFieldsForTypeUid(sanitized.uid)
                dao.clearCardNetworkForTypeUid(sanitized.uid)
            }
            dao.clearMismatchedInstitutionsForTypeUid(sanitized.uid, sanitized.baseType.requiredInstitutionKind())
            if (sanitized.isLiability) {
                dao.getAllAccountsRaw()
                    .filter { it.resolvedAccountTypeUid == sanitized.uid }
                    .forEach { acc -> dao.clearSettlementAccountReferences(acc.uid) }
            }
        } else {
            val nextSortOrder = if (sanitized.sortOrder > 0) {
                sanitized.sortOrder
            } else {
                (existing.maxOfOrNull { it.sortOrder } ?: 0) + 1
            }
            dao.insertAccountType(sanitized.copy(sortOrder = nextSortOrder))
        }
    }

    suspend fun deleteAccountType(accountType: AccountTypeEntity) {
        require(accountType.uid != AccountType.CREDIT_CARD.name) {
            "Credit Card account type cannot be deleted"
        }
        val activeTypes = dao.getAllAccountTypesRaw().filter { !it.isDeleted }
        require(activeTypes.size > 1) {
            "At least one account type must remain"
        }
        val inUseCount = dao.countAccountsUsingAccountType(accountType.uid)
        require(inUseCount == 0) {
            "Cannot delete '${accountType.name}' because it is in use by $inUseCount active account(s)"
        }
        dao.softDeleteAccountType(accountType.uid)
    }

    // --- Institutions (Banks & Platforms) ---
    val allInstitutions: Flow<List<InstitutionEntity>> = dao.getAllInstitutions()

    suspend fun saveInstitution(institution: InstitutionEntity) {
        val existing = dao.getAllInstitutionsRaw()
        val existingMatch = existing.firstOrNull { it.uid == institution.uid }
        val sanitized = institution.copy(
            name = institution.name.trim(),
            kind = existingMatch?.kind ?: institution.kind,
            isDeleted = false
        )
        DomainValidator.validateInstitution(sanitized, existing)

        if (existingMatch != null) {
            dao.updateInstitution(sanitized)
        } else {
            val nextSortOrder = if (sanitized.sortOrder > 0) {
                sanitized.sortOrder
            } else {
                (existing.maxOfOrNull { it.sortOrder } ?: 0) + 1
            }
            dao.insertInstitution(sanitized.copy(sortOrder = nextSortOrder))
        }
    }

    suspend fun deleteInstitution(institution: InstitutionEntity) {
        val inUseCount = dao.countAccountsUsingInstitution(institution.uid)
        require(inUseCount == 0) {
            "Cannot delete '${institution.name}' because it is in use by $inUseCount active account(s)"
        }
        dao.softDeleteInstitution(institution.uid)
    }

    // --- Accounts ---
    val accountsWithCurrency: Flow<List<AccountWithCurrency>> = dao.getAllAccountsWithCurrency()

    suspend fun saveAccount(account: AccountEntity) {
        val existing = dao.getAllAccountsRaw()
        val currency = dao.getCurrencyById(account.currencyUid)
        val accountTypesMap = dao.getAllAccountTypesRaw().associateBy { it.uid }
        val institutionsMap = dao.getAllInstitutionsRaw().associateBy { it.uid }
        val resolvedTypeUid = account.accountTypeUid?.takeIf { it.isNotBlank() } ?: account.accountType.name
        val resolvedBaseType = accountTypesMap[resolvedTypeUid]?.baseType ?: account.accountType
        val cleanedNumber = account.accountNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            if (resolvedBaseType == AccountType.CREDIT_CARD) {
                StatementEntityMapper.sanitizeCardMask(raw)
            } else {
                raw
            }
        }
        val normalizedAccount = account.copy(
            accountType = resolvedBaseType,
            accountTypeUid = resolvedTypeUid,
            accountNumber = cleanedNumber,
            institutionUid = account.institutionUid?.takeIf { it.isNotBlank() }
        )
        DomainValidator.validateAccount(
            account = normalizedAccount,
            existingAccounts = existing,
            currencyExists = currency != null,
            accountTypesMap = accountTypesMap,
            institutionsMap = institutionsMap
        )

        val sanitized = if (normalizedAccount.accountType != AccountType.CREDIT_CARD) {
            normalizedAccount.copy(
                statementDay = null,
                paymentDueDay = null,
                creditLimit = null,
                settlementAccountUid = null,
                cardNetwork = null
            )
        } else normalizedAccount

        val isExisting = existing.any { it.uid == sanitized.uid }
        if (isExisting) {
            dao.updateAccount(sanitized)
        } else {
            dao.insertAccount(sanitized)
        }
        if (sanitized.resolvedIsLiability(accountTypesMap)) {
            dao.clearSettlementAccountReferences(sanitized.uid)
        }
    }

    suspend fun deleteAccount(account: AccountEntity): AccountDeleteOutcome {
        dao.clearSettlementAccountReferences(account.uid)
        dao.clearLinkedParentAccountReferences(account.uid)

        val txCount = dao.countTransactionsReferencingAccount(account.uid)
        return if (txCount > 0) {
            dao.archiveAccount(account.uid)
            AccountDeleteOutcome.ARCHIVED
        } else {
            dao.deleteAccount(account)
            AccountDeleteOutcome.DELETED
        }
    }

    // --- Categories & Subcategories ---
    fun getCategoriesWithSubcategories(type: CategoryType): Flow<List<CategoryWithSubcategories>> {
        return dao.getCategoriesWithSubcategories(type)
    }

    val allCategories: Flow<List<CategoryEntity>> = dao.getAllCategories()

    suspend fun saveCategory(category: CategoryEntity) {
        val existing = dao.getAllCategoriesRaw()
        DomainValidator.validateCategory(category, existing)
        val isExisting = existing.any { it.uid == category.uid }
        if (isExisting) {
            dao.updateCategory(category)
        } else {
            dao.insertCategory(category)
        }
    }

    suspend fun deleteCategory(uid: String) {
        val existing = dao.getCategoryById(uid)
        require(existing != null && !existing.isDeleted) { "Category not found or already deleted" }
        dao.softDeleteCategory(uid)
    }

    // --- Tags ---
    val allTags: Flow<List<TagEntity>> = dao.getAllTags()

    suspend fun saveTag(tag: TagEntity) {
        val existing = dao.getAllTagsRaw()
        val cleanName = DomainValidator.validateTag(tag.name, existing)
        dao.insertTag(tag.copy(name = cleanName))
    }

    suspend fun deleteTag(tag: TagEntity) {
        dao.deleteCrossRefsForTag(tag.uid)
        dao.deleteTag(tag)
    }

    // --- Transactions ---
    val allTransactions: Flow<List<TransactionWithDetails>> = dao.getAllTransactions()

    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<TransactionWithDetails>> {
        return dao.getTransactionsByDateRange(startTime, endTime)
    }

    suspend fun saveTransaction(transaction: TransactionEntity, tags: List<TagEntity> = emptyList()) {
        val preSanitized = when (transaction.type) {
            TransactionType.EXPENSE, TransactionType.INCOME -> {
                transaction.copy(
                    toAccountUid = null,
                    toAccountAmount = null
                )
            }
            TransactionType.ADJUSTMENT -> {
                transaction.copy(
                    toAccountUid = null,
                    toAccountAmount = null,
                    categoryUid = null
                )
            }
            TransactionType.TRANSFER -> {
                transaction.copy(
                    categoryUid = null
                )
            }
        }

        val sourceAccountWithCurr = dao.getAccountWithCurrencyById(preSanitized.accountUid)
        val destAccountWithCurr = preSanitized.toAccountUid?.let { dao.getAccountWithCurrencyById(it) }
        val category = preSanitized.categoryUid?.let { dao.getCategoryById(it) }
        val existingTx = dao.getTransactionById(preSanitized.uid)?.transaction

        DomainValidator.validateTransaction(
            transaction = preSanitized,
            sourceAccount = sourceAccountWithCurr?.account,
            destAccount = destAccountWithCurr?.account,
            category = category,
            existingTransaction = existingTx
        )

        val finalTx = when (preSanitized.type) {
            TransactionType.TRANSFER -> {
                val sourceCurr = sourceAccountWithCurr!!.currency
                val destCurr = destAccountWithCurr!!.currency
                val baseAmt = preSanitized.accountAmount * sourceCurr.rateToBase
                val destAmt = when {
                    sourceCurr.uid == destCurr.uid -> preSanitized.toAccountAmount ?: preSanitized.accountAmount
                    preSanitized.toAccountAmount == null -> baseAmt / destCurr.rateToBase
                    Math.abs(preSanitized.toAccountAmount - preSanitized.accountAmount) < 0.0001 &&
                        Math.abs(sourceCurr.rateToBase - destCurr.rateToBase) > 0.0001 -> baseAmt / destCurr.rateToBase
                    else -> preSanitized.toAccountAmount
                }
                preSanitized.copy(
                    baseAmount = baseAmt,
                    exchangeRate = sourceCurr.rateToBase,
                    currencyUid = sourceCurr.uid,
                    toAccountAmount = destAmt
                )
            }
            TransactionType.ADJUSTMENT, TransactionType.EXPENSE, TransactionType.INCOME -> {
                val rate = sourceAccountWithCurr?.currency?.rateToBase ?: preSanitized.exchangeRate
                preSanitized.copy(
                    baseAmount = preSanitized.accountAmount * rate,
                    exchangeRate = rate,
                    currencyUid = sourceAccountWithCurr?.currency?.uid ?: preSanitized.currencyUid
                )
            }
        }

        dao.saveTransactionWithTags(finalTx, tags)
    }

    suspend fun deleteTransaction(uid: String) {
        val existing = dao.getTransactionById(uid)
        require(existing != null && !existing.transaction.isDeleted) { "Transaction not found or already deleted" }
        dao.softDeleteTransaction(uid)
        dao.deleteTagsForTransaction(uid)
    }

    // --- Approach A: Net Worth Calculation Flow ---
    val netWorthSummary: Flow<NetWorthSummary?> = combine(
        dao.getAllAccountsWithCurrency(),
        dao.getAllTransactions(),
        dao.getAllCurrencies(),
        dao.getAllAccountTypes()
    ) { accounts, transactionsWithDetails, currencies, accountTypes ->
        val mainCurrency = currencies.firstOrNull { it.isMainCurrency } ?: currencies.firstOrNull()
        if (mainCurrency == null) {
            null
        } else {
            val rawTxs = transactionsWithDetails.map { it.transaction }
            val typesMap = accountTypes.associateBy { it.uid }
            NetWorthCalculator.calculate(accounts, rawTxs, mainCurrency, typesMap)
        }
    }

    // --- Credit Card Engine Flow ---
    val creditCardSummaries: Flow<List<CreditCardCycleSummary>> = combine(
        dao.getAllAccountsWithCurrency(),
        dao.getAllTransactions()
    ) { accounts, transactionsWithDetails ->
        val creditCards = accounts.filter { it.account.accountType == AccountType.CREDIT_CARD }
        val rawTxs = transactionsWithDetails.map { it.transaction }

        creditCards.map { cardWithCurr ->
            val card = cardWithCurr.account
            val cardTxs = rawTxs.filter { it.accountUid == card.uid || it.toAccountUid == card.uid }
            CreditCardStatementEngine.calculateStatement(card, cardTxs, LocalDate.now())
        }
    }

    // --- Approach A: Static Revaluation (Difference Adjustment) ---
    suspend fun adjustAccountBalance(
        account: AccountEntity,
        currency: CurrencyEntity,
        currentBalance: Double,
        targetBalance: Double,
        note: String = "Balance Adjustment"
    ) {
        val typeEntity = dao.getAccountTypeById(account.resolvedAccountTypeUid)
        val diff = NetWorthCalculator.computeAdjustmentDifference(
            account = account,
            currentNativeBalance = currentBalance,
            targetNativeBalance = targetBalance,
            isLiabilityOverride = typeEntity?.isLiability
        )
        if (Math.abs(diff) < 0.0001) return

        val baseDiff = diff * currency.rateToBase
        val tx = TransactionEntity(
            uid = UUID.randomUUID().toString(),
            type = TransactionType.ADJUSTMENT,
            accountAmount = diff,
            baseAmount = baseDiff,
            exchangeRate = currency.rateToBase,
            currencyUid = currency.uid,
            accountUid = account.uid,
            note = note,
            description = "Adjusted from ${String.format("%.2f", currentBalance)} to ${String.format("%.2f", targetBalance)}",
            timestamp = System.currentTimeMillis()
        )
        dao.insertTransaction(tx)
    }

    // --- 1-Tap Credit Card Bill Payment ---
    suspend fun payCreditCardBill(
        fromAccountUid: String,
        creditCardUid: String,
        amount: Double,
        currencyUid: String,
        rateToBase: Double = 1.0,
        note: String = "Credit Card Bill Payment"
    ) {
        require(amount.isFinite() && amount > 0.0) { "Payment amount must be greater than zero" }
        require(fromAccountUid.isNotBlank()) { "Payment source account must be specified" }
        require(creditCardUid.isNotBlank()) { "Target credit card must be specified" }
        require(fromAccountUid != creditCardUid) { "Cannot pay credit card from itself" }

        val fromAccount = dao.getAccountWithCurrencyById(fromAccountUid)
        require(fromAccount != null && !fromAccount.account.isArchived) { "Source account does not exist or is archived" }
        val cardAccount = dao.getAccountWithCurrencyById(creditCardUid)
        require(cardAccount != null && !cardAccount.account.isArchived) { "Credit card does not exist or is archived" }

        val srcRate = fromAccount.currency.rateToBase
        val destRate = cardAccount.currency.rateToBase
        val baseAmt = amount * srcRate
        val destAmt = if (fromAccount.currency.uid == cardAccount.currency.uid) amount else (baseAmt / destRate)

        val tx = TransactionEntity(
            uid = UUID.randomUUID().toString(),
            type = TransactionType.TRANSFER,
            accountAmount = amount,
            baseAmount = baseAmt,
            exchangeRate = srcRate,
            currencyUid = fromAccount.currency.uid,
            accountUid = fromAccountUid,
            toAccountUid = creditCardUid,
            toAccountAmount = destAmt,
            note = note,
            description = "Statement Bill Payment",
            timestamp = System.currentTimeMillis()
        )
        dao.insertTransaction(tx)
    }

    // --- SQLite Database Backup & Restore ---
    suspend fun exportDatabase(context: Context, destinationUri: Uri): BackupRestoreResult {
        return SqliteBackupManager.exportDatabase(context, destinationUri)
    }

    suspend fun restoreDatabase(context: Context, sourceUri: Uri): BackupRestoreResult {
        val result = SqliteBackupManager.restoreFromSqliteFile(context, sourceUri, dao)
        if (result.success) {
            insightsDao?.clearAllInsights()
            insightsDao?.clearInsightTransactionCrossRefs()
        }
        return result
    }

    // --- Insights & Recurrence ---
    val activeInsights: Flow<List<InsightWithTransactions>> =
        insightsDao?.observeActiveInsights() ?: flowOf(emptyList())

    suspend fun saveRecurrenceDecision(targetKey: String, decision: RecurrenceDecision) {
        insightsDao?.saveRecurrenceDecision(
            RecurrenceDecisionEntity(
                targetKey = targetKey,
                decision = decision,
                decidedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteRecurrenceDecision(targetKey: String) {
        insightsDao?.deleteRecurrenceDecision(targetKey)
    }
}
