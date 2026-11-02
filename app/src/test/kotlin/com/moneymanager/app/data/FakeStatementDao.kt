package com.moneymanager.app.data

import com.moneymanager.app.data.local.dao.StatementDao
import com.moneymanager.app.data.local.entity.CardStatementSummaryRecord
import com.moneymanager.app.data.local.entity.CardStatementTransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeStatementDao : StatementDao {

    val transactionsMap = mutableMapOf<String, CardStatementTransactionEntity>()
    private val _transactionsFlow = MutableStateFlow<List<CardStatementTransactionEntity>>(emptyList())

    private fun emitFlow() {
        _transactionsFlow.value = transactionsMap.values.toList()
    }

    override suspend fun insertStatementTransactions(transactions: List<CardStatementTransactionEntity>) {
        transactions.forEach { transactionsMap[it.uid] = it }
        emitFlow()
    }

    override fun getTransactionsByStatementId(statementId: String): Flow<List<CardStatementTransactionEntity>> {
        return _transactionsFlow.map { list ->
            list.filter { it.statementId == statementId }.sortedBy { it.serialNumber }
        }
    }

    override suspend fun getTransactionsByStatementIdRaw(statementId: String): List<CardStatementTransactionEntity> {
        return transactionsMap.values.filter { it.statementId == statementId }.sortedBy { it.serialNumber }
    }

    override fun getTransactionsByCardAndMonth(cardMasked: String, yearMonth: String): Flow<List<CardStatementTransactionEntity>> {
        return _transactionsFlow.map { list ->
            list.filter { it.accountOrCardNumberMasked == cardMasked && it.statementYearMonth == yearMonth }
                .sortedBy { it.serialNumber }
        }
    }

    override suspend fun getTransactionsByCardAndMonthRaw(cardMasked: String, yearMonth: String): List<CardStatementTransactionEntity> {
        return transactionsMap.values
            .filter { it.accountOrCardNumberMasked == cardMasked && it.statementYearMonth == yearMonth }
            .sortedBy { it.serialNumber }
    }

    override fun getAllStatementSummaries(): Flow<List<CardStatementSummaryRecord>> {
        return _transactionsFlow.map { computeSummaries() }
    }

    override suspend fun getAllStatementSummariesRaw(): List<CardStatementSummaryRecord> {
        return computeSummaries()
    }

    private fun computeSummaries(): List<CardStatementSummaryRecord> {
        return transactionsMap.values
            .groupBy { it.statementId }
            .map { (stmtId, txns) ->
                val first = txns.first()
                val debits = txns.filter { !it.isCredit }.sumOf { it.amount }
                val credits = txns.filter { it.isCredit }.sumOf { it.amount }
                CardStatementSummaryRecord(
                    statementId = stmtId,
                    bankId = first.bankId,
                    bankName = first.bankName,
                    accountOrCardNumberMasked = first.accountOrCardNumberMasked,
                    accountHolderName = first.accountHolderName,
                    statementYearMonth = first.statementYearMonth,
                    statementDateEpochDay = first.statementDateEpochDay,
                    paymentDueDateEpochDay = first.paymentDueDateEpochDay,
                    billingPeriodStartEpochDay = first.billingPeriodStartEpochDay,
                    billingPeriodEndEpochDay = first.billingPeriodEndEpochDay,
                    statementTotalDue = first.statementTotalDue,
                    statementMinDue = first.statementMinDue,
                    isReconciled = first.isReconciled,
                    transactionCount = txns.size,
                    totalDebits = debits,
                    totalCredits = credits
                )
            }
            .sortedByDescending { it.statementDateEpochDay }
    }

    override suspend fun hasStatement(statementId: String): Boolean {
        return transactionsMap.values.any { it.statementId == statementId }
    }

    override suspend fun hasStatementForMonth(cardMasked: String, yearMonth: String): Boolean {
        return transactionsMap.values.any { it.accountOrCardNumberMasked == cardMasked && it.statementYearMonth == yearMonth }
    }

    override suspend fun updateStatementPaidStatus(statementId: String, isPaid: Boolean) {
        transactionsMap.values.filter { it.statementId == statementId }.forEach {
            transactionsMap[it.uid] = it.copy(isReconciled = isPaid)
        }
        emitFlow()
    }

    override fun getRecentSpends(limit: Int): Flow<List<CardStatementTransactionEntity>> {
        return _transactionsFlow.map { list ->
            list.sortedByDescending { it.transactionDateEpochDay }.take(limit)
        }
    }

    override suspend fun deleteStatementById(statementId: String) {
        transactionsMap.entries.removeIf { it.value.statementId == statementId }
        emitFlow()
    }

    override suspend fun deleteStatementByCardAndMonth(cardMasked: String, yearMonth: String) {
        transactionsMap.entries.removeIf { it.value.accountOrCardNumberMasked == cardMasked && it.value.statementYearMonth == yearMonth }
        emitFlow()
    }
}
