package com.moneymanager.app.data

import com.moneymanager.app.data.local.dao.StatementDao
import com.moneymanager.app.data.local.entity.CardStatementSummaryRecord
import com.moneymanager.app.data.local.entity.CardStatementTransactionEntity
import com.moneymanager.app.domain.statement.core.StatementEntityMapper
import com.moneymanager.app.domain.statement.core.StatementParseResult
import kotlinx.coroutines.flow.Flow

class StatementRepository(
    private val statementDao: StatementDao
) {

    /**
     * Idempotently imports a parsed bank statement.
     * Existing records with the same statementId (card + month-year) are replaced.
     * Returns the unique statementId.
     */
    suspend fun importStatement(result: StatementParseResult): String {
        val preliminary = StatementEntityMapper.toEntities(result)
        if (preliminary.isEmpty()) {
            return ""
        }
        val statementId = preliminary.first().statementId
        val existingPaid = statementDao.getTransactionsByStatementIdRaw(statementId).firstOrNull()?.isReconciled == true
        val entities = if (existingPaid) {
            preliminary.map { it.copy(isReconciled = true) }
        } else {
            preliminary
        }
        statementDao.replaceStatement(statementId, entities)
        return statementId
    }

    /**
     * Observes all imported statement summaries grouped by statement.
     */
    fun getStatementSummaries(): Flow<List<CardStatementSummaryRecord>> {
        return statementDao.getAllStatementSummaries()
    }

    suspend fun getStatementSummariesRaw(): List<CardStatementSummaryRecord> {
        return statementDao.getAllStatementSummariesRaw()
    }

    /**
     * Observes all parsed transactions for a specific statement.
     */
    fun getTransactionsByStatementId(statementId: String): Flow<List<CardStatementTransactionEntity>> {
        return statementDao.getTransactionsByStatementId(statementId)
    }

    suspend fun getTransactionsByStatementIdRaw(statementId: String): List<CardStatementTransactionEntity> {
        return statementDao.getTransactionsByStatementIdRaw(statementId)
    }

    /**
     * Observes all parsed transactions for a specific card and month-year (e.g. "418688******8158", "2026-10").
     */
    fun getTransactionsByCardAndMonth(cardMasked: String, yearMonth: String): Flow<List<CardStatementTransactionEntity>> {
        return statementDao.getTransactionsByCardAndMonth(cardMasked, yearMonth)
    }

    suspend fun getTransactionsByCardAndMonthRaw(cardMasked: String, yearMonth: String): List<CardStatementTransactionEntity> {
        return statementDao.getTransactionsByCardAndMonthRaw(cardMasked, yearMonth)
    }

    suspend fun hasStatement(statementId: String): Boolean {
        return statementDao.hasStatement(statementId)
    }

    suspend fun hasStatementForMonth(cardMasked: String, yearMonth: String): Boolean {
        return statementDao.hasStatementForMonth(cardMasked, yearMonth)
    }

    suspend fun setStatementPaidStatus(statementId: String, isPaid: Boolean) {
        statementDao.updateStatementPaidStatus(statementId, isPaid)
    }

    fun getRecentSpends(limit: Int = 200): Flow<List<CardStatementTransactionEntity>> {
        return statementDao.getRecentSpends(limit)
    }

    suspend fun deleteStatement(statementId: String) {
        statementDao.deleteStatementById(statementId)
    }

    suspend fun deleteStatementForMonth(cardMasked: String, yearMonth: String) {
        statementDao.deleteStatementByCardAndMonth(cardMasked, yearMonth)
    }
}
