package com.moneymanager.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountWithCurrency
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryType
import com.moneymanager.app.data.local.entity.CategoryWithSubcategories
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.data.local.entity.TagEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionTagCrossRef
import com.moneymanager.app.data.local.entity.TransactionWithDetails
import com.moneymanager.app.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow

data class CategorySpendingAggregate(
    val categoryUid: String?,
    val totalAmount: Double,
    val transactionCount: Int
)

data class TypeTotalAggregate(
    val type: TransactionType,
    val totalAmount: Double,
    val transactionCount: Int
)

@Dao
interface MoneyManagerDao {

    // --- Currencies ---
    @Query("SELECT * FROM currencies ORDER BY isMainCurrency DESC, name ASC")
    fun getAllCurrencies(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies WHERE uid = :uid LIMIT 1")
    suspend fun getCurrencyById(uid: String): CurrencyEntity?

    @Query("SELECT * FROM currencies WHERE isMainCurrency = 1 LIMIT 1")
    suspend fun getMainCurrency(): CurrencyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrency(currency: CurrencyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrencies(currencies: List<CurrencyEntity>)

    @Update
    suspend fun updateCurrency(currency: CurrencyEntity)

    @Delete
    suspend fun deleteCurrency(currency: CurrencyEntity)


    // --- Accounts ---
    @Transaction
    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY sortOrder ASC, name ASC")
    fun getAllAccountsWithCurrency(): Flow<List<AccountWithCurrency>>

    @Transaction
    @Query("SELECT * FROM accounts WHERE uid = :uid LIMIT 1")
    suspend fun getAccountWithCurrencyById(uid: String): AccountWithCurrency?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)


    // --- Categories & Subcategories ---
    @Transaction
    @Query("SELECT * FROM categories WHERE parentUid IS NULL AND type = :type AND isDeleted = 0 ORDER BY sortOrder ASC, name ASC")
    fun getCategoriesWithSubcategories(type: CategoryType): Flow<List<CategoryWithSubcategories>>

    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY sortOrder ASC, name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE uid = :uid LIMIT 1")
    suspend fun getCategoryById(uid: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("UPDATE categories SET isDeleted = 1 WHERE uid = :uid OR parentUid = :uid")
    suspend fun softDeleteCategory(uid: String)


    // --- Tags ---
    @Query("SELECT * FROM tags ORDER BY sortOrder ASC, name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Delete
    suspend fun deleteTag(tag: TagEntity)


    // --- Transactions ---
    @Transaction
    @Query("""
        SELECT * FROM transactions 
        WHERE isDeleted = 0 
        AND timestamp BETWEEN :startTime AND :endTime 
        ORDER BY timestamp DESC
    """)
    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<TransactionWithDetails>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionWithDetails>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND accountUid = :accountUid ORDER BY timestamp DESC")
    fun getTransactionsForAccount(accountUid: String): Flow<List<TransactionWithDetails>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE uid = :uid AND isDeleted = 0 LIMIT 1")
    suspend fun getTransactionById(uid: String): TransactionWithDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET isDeleted = 1 WHERE uid = :uid")
    suspend fun softDeleteTransaction(uid: String)

    // Transaction - Tag Cross Refs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionTagCrossRefs(crossRefs: List<TransactionTagCrossRef>)

    @Query("DELETE FROM transaction_tags WHERE transactionUid = :transactionUid")
    suspend fun deleteTagsForTransaction(transactionUid: String)

    @Transaction
    suspend fun saveTransactionWithTags(transaction: TransactionEntity, tags: List<TagEntity>) {
        insertTransaction(transaction)
        deleteTagsForTransaction(transaction.uid)
        if (tags.isNotEmpty()) {
            insertTags(tags)
            val refs = tags.map { TransactionTagCrossRef(transaction.uid, it.uid) }
            insertTransactionTagCrossRefs(refs)
        }
    }


    // --- Special Calculation Queries ---

    // Raw transactions for balance and credit card calculations
    @Query("SELECT * FROM transactions WHERE isDeleted = 0")
    suspend fun getAllRawTransactions(): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions 
        WHERE isDeleted = 0 
        AND accountUid = :accountUid 
        AND timestamp > :fromTime 
        AND timestamp <= :toTime
    """)
    suspend fun getAccountTransactionsInWindow(accountUid: String, fromTime: Long, toTime: Long): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions 
        WHERE isDeleted = 0 
        AND type = 'TRANSFER' 
        AND toAccountUid = :cardAccountUid 
        AND timestamp > :fromTime 
        AND timestamp <= :toTime
    """)
    suspend fun getPaymentsToCardInWindow(cardAccountUid: String, fromTime: Long, toTime: Long): List<TransactionEntity>

    // --- Referential Integrity and Safe Deletion Queries ---
    @Query("SELECT COUNT(*) FROM transactions WHERE accountUid = :accountUid OR toAccountUid = :accountUid")
    suspend fun countTransactionsReferencingAccount(accountUid: String): Int

    @Query("UPDATE accounts SET isArchived = 1 WHERE uid = :uid")
    suspend fun archiveAccount(uid: String)

    @Query("UPDATE accounts SET settlementAccountUid = NULL WHERE settlementAccountUid = :uid")
    suspend fun clearSettlementAccountReferences(uid: String)

    @Query("UPDATE accounts SET linkedParentAccountUid = NULL WHERE linkedParentAccountUid = :uid")
    suspend fun clearLinkedParentAccountReferences(uid: String)

    @Query("SELECT COUNT(*) FROM accounts WHERE currencyUid = :currencyUid AND isArchived = 0")
    suspend fun countAccountsUsingCurrency(currencyUid: String): Int

    @Query("DELETE FROM transaction_tags WHERE tagUid = :tagUid")
    suspend fun deleteCrossRefsForTag(tagUid: String)

    @Query("SELECT * FROM accounts")
    suspend fun getAllAccountsRaw(): List<AccountEntity>

    @Query("SELECT * FROM categories")
    suspend fun getAllCategoriesRaw(): List<CategoryEntity>

    @Query("SELECT * FROM currencies")
    suspend fun getAllCurrenciesRaw(): List<CurrencyEntity>

    @Query("SELECT * FROM tags")
    suspend fun getAllTagsRaw(): List<TagEntity>

    // --- Monthly Money Story Aggregations ---
    @Query("""
        SELECT categoryUid, SUM(baseAmount) AS totalAmount, COUNT(*) AS transactionCount
        FROM transactions
        WHERE isDeleted = 0 
          AND type = 'EXPENSE' 
          AND timestamp BETWEEN :startTime AND :endTime
        GROUP BY categoryUid
    """)
    suspend fun getCategoryExpensesInWindow(startTime: Long, endTime: Long): List<CategorySpendingAggregate>

    @Query("""
        SELECT type, SUM(baseAmount) AS totalAmount, COUNT(*) AS transactionCount
        FROM transactions
        WHERE isDeleted = 0 
          AND timestamp BETWEEN :startTime AND :endTime
        GROUP BY type
    """)
    suspend fun getTypeTotalsInWindow(startTime: Long, endTime: Long): List<TypeTotalAggregate>

    @Query("DELETE FROM transaction_tags")
    suspend fun clearAllTransactionTags()

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    @Query("DELETE FROM accounts")
    suspend fun clearAllAccounts()

    @Query("DELETE FROM categories")
    suspend fun clearAllCategories()

    @Query("DELETE FROM tags")
    suspend fun clearAllTags()

    @Query("DELETE FROM currencies")
    suspend fun clearAllCurrencies()
}
