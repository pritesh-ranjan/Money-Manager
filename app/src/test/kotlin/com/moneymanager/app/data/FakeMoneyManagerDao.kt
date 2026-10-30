package com.moneymanager.app.data

import com.moneymanager.app.data.local.dao.MoneyManagerDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountWithCurrency
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryType
import com.moneymanager.app.data.local.entity.CategoryWithSubcategories
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.data.local.entity.TagEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionTagCrossRef
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.data.local.entity.TransactionWithDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMoneyManagerDao : MoneyManagerDao {

    val currenciesFlow = MutableStateFlow<List<CurrencyEntity>>(emptyList())
    val accountsFlow = MutableStateFlow<List<AccountEntity>>(emptyList())
    val categoriesFlow = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val tagsFlow = MutableStateFlow<List<TagEntity>>(emptyList())
    val transactionsFlow = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val crossRefsFlow = MutableStateFlow<List<TransactionTagCrossRef>>(emptyList())

    // --- Currencies ---
    override fun getAllCurrencies(): Flow<List<CurrencyEntity>> = currenciesFlow

    override suspend fun getCurrencyById(uid: String): CurrencyEntity? {
        return currenciesFlow.value.firstOrNull { it.uid == uid }
    }

    override suspend fun getMainCurrency(): CurrencyEntity? {
        return currenciesFlow.value.firstOrNull { it.isMainCurrency }
    }

    override suspend fun insertCurrency(currency: CurrencyEntity) {
        val current = currenciesFlow.value.toMutableList()
        current.removeAll { it.uid == currency.uid }
        current.add(currency)
        currenciesFlow.value = current
    }

    override suspend fun insertCurrencies(currencies: List<CurrencyEntity>) {
        val current = currenciesFlow.value.toMutableList()
        for (c in currencies) {
            current.removeAll { it.uid == c.uid }
            current.add(c)
        }
        currenciesFlow.value = current
    }

    override suspend fun updateCurrency(currency: CurrencyEntity) {
        insertCurrency(currency)
    }

    override suspend fun deleteCurrency(currency: CurrencyEntity) {
        val current = currenciesFlow.value.toMutableList()
        current.removeAll { it.uid == currency.uid }
        currenciesFlow.value = current
    }

    // --- Accounts ---
    override fun getAllAccountsWithCurrency(): Flow<List<AccountWithCurrency>> {
        return accountsFlow.map { accs ->
            accs.filter { !it.isArchived }
                .sortedWith(compareBy({ it.sortOrder }, { it.name }))
                .map { acc -> buildAccountWithCurrency(acc) }
        }
    }

    override suspend fun getAccountWithCurrencyById(uid: String): AccountWithCurrency? {
        val acc = accountsFlow.value.firstOrNull { it.uid == uid } ?: return null
        return buildAccountWithCurrency(acc)
    }

    private fun buildAccountWithCurrency(acc: AccountEntity): AccountWithCurrency {
        val curr = currenciesFlow.value.firstOrNull { it.uid == acc.currencyUid }
            ?: CurrencyEntity(acc.currencyUid, "Default", "DEF", "¤", 1.0)
        val parent = acc.linkedParentAccountUid?.let { pUid ->
            accountsFlow.value.firstOrNull { it.uid == pUid }
        }
        return AccountWithCurrency(account = acc, currency = curr, parentAccount = parent)
    }

    override suspend fun insertAccount(account: AccountEntity) {
        val current = accountsFlow.value.toMutableList()
        current.removeAll { it.uid == account.uid }
        current.add(account)
        accountsFlow.value = current
    }

    override suspend fun insertAccounts(accounts: List<AccountEntity>) {
        val current = accountsFlow.value.toMutableList()
        for (a in accounts) {
            current.removeAll { it.uid == a.uid }
            current.add(a)
        }
        accountsFlow.value = current
    }

    override suspend fun updateAccount(account: AccountEntity) {
        insertAccount(account)
    }

    override suspend fun deleteAccount(account: AccountEntity) {
        val current = accountsFlow.value.toMutableList()
        current.removeAll { it.uid == account.uid }
        accountsFlow.value = current
    }

    // --- Categories & Subcategories ---
    override fun getCategoriesWithSubcategories(type: CategoryType): Flow<List<CategoryWithSubcategories>> {
        return categoriesFlow.map { cats ->
            val nonDeleted = cats.filter { !it.isDeleted && it.type == type }
            val parents = nonDeleted.filter { it.parentUid == null }
                .sortedWith(compareBy({ it.sortOrder }, { it.name }))
            parents.map { parent ->
                val subs = nonDeleted.filter { it.parentUid == parent.uid }
                    .sortedWith(compareBy({ it.sortOrder }, { it.name }))
                CategoryWithSubcategories(category = parent, subcategories = subs)
            }
        }
    }

    override fun getAllCategories(): Flow<List<CategoryEntity>> {
        return categoriesFlow.map { cats ->
            cats.filter { !it.isDeleted }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
        }
    }

    override suspend fun getCategoryById(uid: String): CategoryEntity? {
        return categoriesFlow.value.firstOrNull { it.uid == uid }
    }

    override suspend fun insertCategory(category: CategoryEntity) {
        val current = categoriesFlow.value.toMutableList()
        current.removeAll { it.uid == category.uid }
        current.add(category)
        categoriesFlow.value = current
    }

    override suspend fun insertCategories(categories: List<CategoryEntity>) {
        val current = categoriesFlow.value.toMutableList()
        for (c in categories) {
            current.removeAll { it.uid == c.uid }
            current.add(c)
        }
        categoriesFlow.value = current
    }

    override suspend fun updateCategory(category: CategoryEntity) {
        insertCategory(category)
    }

    override suspend fun softDeleteCategory(uid: String) {
        val current = categoriesFlow.value.map { cat ->
            if (cat.uid == uid || cat.parentUid == uid) {
                cat.copy(isDeleted = true)
            } else cat
        }
        categoriesFlow.value = current
    }

    // --- Tags ---
    override fun getAllTags(): Flow<List<TagEntity>> {
        return tagsFlow.map { list -> list.sortedWith(compareBy({ it.sortOrder }, { it.name })) }
    }

    override suspend fun insertTag(tag: TagEntity) {
        val current = tagsFlow.value.toMutableList()
        if (current.none { it.uid == tag.uid }) {
            current.add(tag)
            tagsFlow.value = current
        }
    }

    override suspend fun insertTags(tags: List<TagEntity>) {
        val current = tagsFlow.value.toMutableList()
        for (t in tags) {
            if (current.none { it.uid == t.uid }) {
                current.add(t)
            }
        }
        tagsFlow.value = current
    }

    override suspend fun updateTag(tag: TagEntity) {
        val current = tagsFlow.value.toMutableList()
        current.removeAll { it.uid == tag.uid }
        current.add(tag)
        tagsFlow.value = current
    }

    override suspend fun deleteTag(tag: TagEntity) {
        val current = tagsFlow.value.toMutableList()
        current.removeAll { it.uid == tag.uid }
        tagsFlow.value = current
    }

    // --- Transactions ---
    override fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<TransactionWithDetails>> {
        return transactionsFlow.map { txs ->
            txs.filter { !it.isDeleted && it.timestamp in startTime..endTime }
                .sortedByDescending { it.timestamp }
                .mapNotNull { buildTransactionWithDetails(it) }
        }
    }

    override fun getAllTransactions(): Flow<List<TransactionWithDetails>> {
        return transactionsFlow.map { txs ->
            txs.filter { !it.isDeleted }
                .sortedByDescending { it.timestamp }
                .mapNotNull { buildTransactionWithDetails(it) }
        }
    }

    override fun getTransactionsForAccount(accountUid: String): Flow<List<TransactionWithDetails>> {
        return transactionsFlow.map { txs ->
            txs.filter { !it.isDeleted && it.accountUid == accountUid }
                .sortedByDescending { it.timestamp }
                .mapNotNull { buildTransactionWithDetails(it) }
        }
    }

    override suspend fun getTransactionById(uid: String): TransactionWithDetails? {
        val tx = transactionsFlow.value.firstOrNull { it.uid == uid && !it.isDeleted } ?: return null
        return buildTransactionWithDetails(tx)
    }

    private fun buildTransactionWithDetails(tx: TransactionEntity): TransactionWithDetails? {
        val acc = accountsFlow.value.firstOrNull { it.uid == tx.accountUid } ?: return null
        val toAcc = tx.toAccountUid?.let { toUid -> accountsFlow.value.firstOrNull { it.uid == toUid } }
        val cat = tx.categoryUid?.let { catUid -> categoriesFlow.value.firstOrNull { it.uid == catUid } }
        val curr = currenciesFlow.value.firstOrNull { it.uid == tx.currencyUid }
            ?: CurrencyEntity(tx.currencyUid, "Default", "DEF", "¤", 1.0)
        val tagUids = crossRefsFlow.value.filter { it.transactionUid == tx.uid }.map { it.tagUid }.toSet()
        val tags = tagsFlow.value.filter { tagUids.contains(it.uid) }

        return TransactionWithDetails(
            transaction = tx,
            account = acc,
            toAccount = toAcc,
            category = cat,
            currency = curr,
            tags = tags
        )
    }

    override suspend fun insertTransaction(transaction: TransactionEntity) {
        val current = transactionsFlow.value.toMutableList()
        current.removeAll { it.uid == transaction.uid }
        current.add(transaction)
        transactionsFlow.value = current
    }

    override suspend fun insertTransactions(transactions: List<TransactionEntity>) {
        val current = transactionsFlow.value.toMutableList()
        for (tx in transactions) {
            current.removeAll { it.uid == tx.uid }
            current.add(tx)
        }
        transactionsFlow.value = current
    }

    override suspend fun updateTransaction(transaction: TransactionEntity) {
        insertTransaction(transaction)
    }

    override suspend fun softDeleteTransaction(uid: String) {
        val current = transactionsFlow.value.map { tx ->
            if (tx.uid == uid) tx.copy(isDeleted = true) else tx
        }
        transactionsFlow.value = current
    }

    override suspend fun insertTransactionTagCrossRefs(crossRefs: List<TransactionTagCrossRef>) {
        val current = crossRefsFlow.value.toMutableList()
        current.addAll(crossRefs)
        crossRefsFlow.value = current.distinct()
    }

    override suspend fun deleteTagsForTransaction(transactionUid: String) {
        val current = crossRefsFlow.value.toMutableList()
        current.removeAll { it.transactionUid == transactionUid }
        crossRefsFlow.value = current
    }

    override suspend fun saveTransactionWithTags(transaction: TransactionEntity, tags: List<TagEntity>) {
        insertTransaction(transaction)
        deleteTagsForTransaction(transaction.uid)
        if (tags.isNotEmpty()) {
            insertTags(tags)
            val refs = tags.map { TransactionTagCrossRef(transaction.uid, it.uid) }
            insertTransactionTagCrossRefs(refs)
        }
    }

    // --- Special Calculation Queries ---
    override suspend fun getAllRawTransactions(): List<TransactionEntity> {
        return transactionsFlow.value.filter { !it.isDeleted }
    }

    override suspend fun getAccountTransactionsInWindow(accountUid: String, fromTime: Long, toTime: Long): List<TransactionEntity> {
        return transactionsFlow.value.filter {
            !it.isDeleted && it.accountUid == accountUid && it.timestamp in (fromTime + 1)..toTime
        }
    }

    override suspend fun getPaymentsToCardInWindow(cardAccountUid: String, fromTime: Long, toTime: Long): List<TransactionEntity> {
        return transactionsFlow.value.filter {
            !it.isDeleted && it.type == com.moneymanager.app.data.local.entity.TransactionType.TRANSFER &&
            it.toAccountUid == cardAccountUid && it.timestamp in (fromTime + 1)..toTime
        }
    }

    // --- Referential Integrity Queries ---
    override suspend fun countTransactionsReferencingAccount(accountUid: String): Int {
        return transactionsFlow.value.count {
            it.accountUid == accountUid || it.toAccountUid == accountUid
        }
    }

    override suspend fun archiveAccount(uid: String) {
        val current = accountsFlow.value.map { acc ->
            if (acc.uid == uid) acc.copy(isArchived = true) else acc
        }
        accountsFlow.value = current
    }

    override suspend fun clearSettlementAccountReferences(uid: String) {
        val current = accountsFlow.value.map { acc ->
            if (acc.settlementAccountUid == uid) acc.copy(settlementAccountUid = null) else acc
        }
        accountsFlow.value = current
    }

    override suspend fun clearLinkedParentAccountReferences(uid: String) {
        val current = accountsFlow.value.map { acc ->
            if (acc.linkedParentAccountUid == uid) acc.copy(linkedParentAccountUid = null) else acc
        }
        accountsFlow.value = current
    }

    override suspend fun countAccountsUsingCurrency(currencyUid: String): Int {
        return accountsFlow.value.count { it.currencyUid == currencyUid && !it.isArchived }
    }

    override suspend fun deleteCrossRefsForTag(tagUid: String) {
        val current = crossRefsFlow.value.toMutableList()
        current.removeAll { it.tagUid == tagUid }
        crossRefsFlow.value = current
    }

    override suspend fun getAllAccountsRaw(): List<AccountEntity> = accountsFlow.value

    override suspend fun getAllCategoriesRaw(): List<CategoryEntity> = categoriesFlow.value

    override suspend fun getAllCurrenciesRaw(): List<CurrencyEntity> = currenciesFlow.value

    override suspend fun getAllTagsRaw(): List<TagEntity> = tagsFlow.value

    override suspend fun getCategoryExpensesInWindow(startTime: Long, endTime: Long): List<com.moneymanager.app.data.local.dao.CategorySpendingAggregate> {
        return transactionsFlow.value
            .filter { !it.isDeleted && it.type == TransactionType.EXPENSE && it.timestamp in startTime..endTime }
            .groupBy { it.categoryUid }
            .map { (catUid, list) ->
                com.moneymanager.app.data.local.dao.CategorySpendingAggregate(
                    categoryUid = catUid,
                    totalAmount = list.sumOf { it.baseAmount },
                    transactionCount = list.size
                )
            }
    }

    override suspend fun getTypeTotalsInWindow(startTime: Long, endTime: Long): List<com.moneymanager.app.data.local.dao.TypeTotalAggregate> {
        return transactionsFlow.value
            .filter { !it.isDeleted && it.timestamp in startTime..endTime }
            .groupBy { it.type }
            .map { (type, list) ->
                com.moneymanager.app.data.local.dao.TypeTotalAggregate(
                    type = type,
                    totalAmount = list.sumOf { it.baseAmount },
                    transactionCount = list.size
                )
            }
    }

    override suspend fun clearAllTransactionTags() { crossRefsFlow.value = emptyList() }
    override suspend fun clearAllTransactions() { transactionsFlow.value = emptyList() }
    override suspend fun clearAllAccounts() { accountsFlow.value = emptyList() }
    override suspend fun clearAllCategories() { categoriesFlow.value = emptyList() }
    override suspend fun clearAllTags() { tagsFlow.value = emptyList() }
    override suspend fun clearAllCurrencies() { currenciesFlow.value = emptyList() }
}
