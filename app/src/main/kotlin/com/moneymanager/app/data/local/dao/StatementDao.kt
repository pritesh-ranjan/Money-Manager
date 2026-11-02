package com.moneymanager.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.moneymanager.app.data.local.entity.CardStatementSummaryRecord
import com.moneymanager.app.data.local.entity.CardStatementTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatementTransactions(transactions: List<CardStatementTransactionEntity>)

    @Query("SELECT * FROM card_statement_transactions WHERE statementId = :statementId ORDER BY serialNumber ASC")
    fun getTransactionsByStatementId(statementId: String): Flow<List<CardStatementTransactionEntity>>

    @Query("SELECT * FROM card_statement_transactions WHERE statementId = :statementId ORDER BY serialNumber ASC")
    suspend fun getTransactionsByStatementIdRaw(statementId: String): List<CardStatementTransactionEntity>

    @Query("SELECT * FROM card_statement_transactions WHERE accountOrCardNumberMasked = :cardMasked AND statementYearMonth = :yearMonth ORDER BY serialNumber ASC")
    fun getTransactionsByCardAndMonth(cardMasked: String, yearMonth: String): Flow<List<CardStatementTransactionEntity>>

    @Query("SELECT * FROM card_statement_transactions WHERE accountOrCardNumberMasked = :cardMasked AND statementYearMonth = :yearMonth ORDER BY serialNumber ASC")
    suspend fun getTransactionsByCardAndMonthRaw(cardMasked: String, yearMonth: String): List<CardStatementTransactionEntity>

    @Query("""
        SELECT 
            statementId,
            bankId,
            bankName,
            accountOrCardNumberMasked,
            accountHolderName,
            statementYearMonth,
            statementDateEpochDay,
            paymentDueDateEpochDay,
            MIN(billingPeriodStartEpochDay) as billingPeriodStartEpochDay,
            MAX(billingPeriodEndEpochDay) as billingPeriodEndEpochDay,
            statementTotalDue,
            statementMinDue,
            isReconciled,
            COUNT(*) as transactionCount,
            SUM(CASE WHEN isCredit = 0 THEN amount ELSE 0 END) as totalDebits,
            SUM(CASE WHEN isCredit = 1 THEN amount ELSE 0 END) as totalCredits
        FROM card_statement_transactions
        GROUP BY statementId
        ORDER BY statementDateEpochDay DESC
    """)
    fun getAllStatementSummaries(): Flow<List<CardStatementSummaryRecord>>

    @Query("""
        SELECT 
            statementId,
            bankId,
            bankName,
            accountOrCardNumberMasked,
            accountHolderName,
            statementYearMonth,
            statementDateEpochDay,
            paymentDueDateEpochDay,
            MIN(billingPeriodStartEpochDay) as billingPeriodStartEpochDay,
            MAX(billingPeriodEndEpochDay) as billingPeriodEndEpochDay,
            statementTotalDue,
            statementMinDue,
            isReconciled,
            COUNT(*) as transactionCount,
            SUM(CASE WHEN isCredit = 0 THEN amount ELSE 0 END) as totalDebits,
            SUM(CASE WHEN isCredit = 1 THEN amount ELSE 0 END) as totalCredits
        FROM card_statement_transactions
        GROUP BY statementId
        ORDER BY statementDateEpochDay DESC
    """)
    suspend fun getAllStatementSummariesRaw(): List<CardStatementSummaryRecord>

    @Query("SELECT EXISTS(SELECT 1 FROM card_statement_transactions WHERE statementId = :statementId)")
    suspend fun hasStatement(statementId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM card_statement_transactions WHERE accountOrCardNumberMasked = :cardMasked AND statementYearMonth = :yearMonth)")
    suspend fun hasStatementForMonth(cardMasked: String, yearMonth: String): Boolean

    @Query("DELETE FROM card_statement_transactions WHERE statementId = :statementId")
    suspend fun deleteStatementById(statementId: String)

    @Query("DELETE FROM card_statement_transactions WHERE accountOrCardNumberMasked = :cardMasked AND statementYearMonth = :yearMonth")
    suspend fun deleteStatementByCardAndMonth(cardMasked: String, yearMonth: String)

    @Query("UPDATE card_statement_transactions SET isReconciled = :isPaid WHERE statementId = :statementId")
    suspend fun updateStatementPaidStatus(statementId: String, isPaid: Boolean)

    @Query("SELECT * FROM card_statement_transactions ORDER BY transactionDateEpochDay DESC, serialNumber ASC LIMIT :limit")
    fun getRecentSpends(limit: Int = 200): Flow<List<CardStatementTransactionEntity>>

    @Transaction
    suspend fun replaceStatement(statementId: String, transactions: List<CardStatementTransactionEntity>) {
        deleteStatementById(statementId)
        insertStatementTransactions(transactions)
    }
}
