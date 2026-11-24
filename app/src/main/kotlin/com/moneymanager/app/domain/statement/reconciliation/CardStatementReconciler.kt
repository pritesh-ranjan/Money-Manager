package com.moneymanager.app.domain.statement.reconciliation

import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.CardStatementTransactionEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.data.local.entity.TransactionWithDetails
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToLong

val TransactionEntity.dateEpochDay: Long
    get() = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toEpochDay()

val TransactionEntity.amount: Double
    get() = accountAmount

data class ReconciledPair(
    val statementTx: CardStatementTransactionEntity,
    val manualTx: TransactionWithDetails
)

data class StatementReconciliationReport(
    val statementId: String,
    val cardMasked: String,
    val billingPeriodStart: LocalDate?,
    val billingPeriodEnd: LocalDate?,
    val statementTotalDebits: Double,
    val manualTotalDebits: Double,
    val statementTotalCredits: Double,
    val manualTotalCredits: Double,
    val varianceAmount: Double, // statementTotalDebits - manualTotalDebits
    val isFullyReconciled: Boolean,
    val matchedTransactions: List<ReconciledPair>,
    val unrecordedStatementTransactions: List<CardStatementTransactionEntity>,
    val unmatchedManualTransactions: List<TransactionWithDetails>
) {
    val unrecordedDebitsCount: Int get() = unrecordedStatementTransactions.count { !it.isCredit }
    val unrecordedCreditsCount: Int get() = unrecordedStatementTransactions.count { it.isCredit }
    val hasDiscrepancy: Boolean get() = !isFullyReconciled
}

object CardStatementReconciler {

    private const val AMOUNT_TOLERANCE = 0.01
    private const val DATE_TOLERANCE_DAYS = 3L

    fun reconcile(
        statementId: String,
        cardMasked: String,
        statementTransactions: List<CardStatementTransactionEntity>,
        manualTransactions: List<TransactionWithDetails>,
        periodStartEpochDay: Long? = null,
        periodEndEpochDay: Long? = null,
        cardAccountUid: String? = null
    ): StatementReconciliationReport {
        // Filter candidate manual transactions around the billing period (with settlement delay buffer)
        val candidateManualTxs = manualTransactions.filter { tx ->
            if (periodStartEpochDay != null && periodEndEpochDay != null) {
                val txDay = tx.transaction.dateEpochDay
                txDay >= (periodStartEpochDay - DATE_TOLERANCE_DAYS) &&
                    txDay <= (periodEndEpochDay + DATE_TOLERANCE_DAYS)
            } else {
                true
            }
        }.toMutableList()

        val matchedPairs = mutableListOf<ReconciledPair>()
        val unrecordedStatementTxs = mutableListOf<CardStatementTransactionEntity>()

        val statementDebits = statementTransactions.filter { !it.isCredit }
        val statementCredits = statementTransactions.filter { it.isCredit }

        // Bipartite greedy matching for debits (expenses or outgoing card transfers)
        for (stmtTx in statementDebits) {
            val matchIndex = candidateManualTxs.indexOfFirst { manual ->
                isManualDebitOnCard(manual, cardAccountUid) &&
                    abs(manual.transaction.amount - stmtTx.amount) < AMOUNT_TOLERANCE &&
                    abs(manual.transaction.dateEpochDay - stmtTx.transactionDateEpochDay) <= DATE_TOLERANCE_DAYS
            }

            if (matchIndex != -1) {
                val matchedManual = candidateManualTxs.removeAt(matchIndex)
                matchedPairs.add(ReconciledPair(stmtTx, matchedManual))
            } else {
                unrecordedStatementTxs.add(stmtTx)
            }
        }

        // Bipartite greedy matching for credits (refunds, cashbacks, incoming bill-payment transfers)
        for (stmtTx in statementCredits) {
            val matchIndex = candidateManualTxs.indexOfFirst { manual ->
                isManualCreditOnCard(manual, cardAccountUid) &&
                    abs(manualCreditAmountOnCard(manual) - stmtTx.amount) < AMOUNT_TOLERANCE &&
                    abs(manual.transaction.dateEpochDay - stmtTx.transactionDateEpochDay) <= DATE_TOLERANCE_DAYS
            }

            if (matchIndex != -1) {
                val matchedManual = candidateManualTxs.removeAt(matchIndex)
                matchedPairs.add(ReconciledPair(stmtTx, matchedManual))
            } else {
                unrecordedStatementTxs.add(stmtTx)
            }
        }

        // Leftover candidate transactions that fall within the strict period are unmatched manual entries
        val unmatchedManualTxs = candidateManualTxs.filter { manual ->
            if (periodStartEpochDay != null && periodEndEpochDay != null) {
                val day = manual.transaction.dateEpochDay
                day in periodStartEpochDay..periodEndEpochDay
            } else {
                true
            }
        }

        val stmtTotalDebits = statementDebits.sumOf { it.amount }.round2Decimals()
        val stmtTotalCredits = statementCredits.sumOf { it.amount }.round2Decimals()

        val manualDebits = manualTransactions.filter {
            isManualDebitOnCard(it, cardAccountUid) &&
                (periodStartEpochDay == null || it.transaction.dateEpochDay in periodStartEpochDay..periodEndEpochDay!!)
        }.sumOf { it.transaction.amount }.round2Decimals()

        val manualCredits = manualTransactions.filter {
            isManualCreditOnCard(it, cardAccountUid) &&
                (periodStartEpochDay == null || it.transaction.dateEpochDay in periodStartEpochDay..periodEndEpochDay!!)
        }.sumOf { manualCreditAmountOnCard(it) }.round2Decimals()

        val variance = (stmtTotalDebits - manualDebits).round2Decimals()
        val isFullyReconciled = unrecordedStatementTxs.isEmpty() &&
            unmatchedManualTxs.isEmpty() &&
            abs(variance) < AMOUNT_TOLERANCE

        return StatementReconciliationReport(
            statementId = statementId,
            cardMasked = cardMasked,
            billingPeriodStart = periodStartEpochDay?.let { LocalDate.ofEpochDay(it) },
            billingPeriodEnd = periodEndEpochDay?.let { LocalDate.ofEpochDay(it) },
            statementTotalDebits = stmtTotalDebits,
            manualTotalDebits = manualDebits,
            statementTotalCredits = stmtTotalCredits,
            manualTotalCredits = manualCredits,
            varianceAmount = variance,
            isFullyReconciled = isFullyReconciled,
            matchedTransactions = matchedPairs,
            unrecordedStatementTransactions = unrecordedStatementTxs,
            unmatchedManualTransactions = unmatchedManualTxs
        )
    }

    private fun isManualDebitOnCard(manual: TransactionWithDetails, cardAccountUid: String?): Boolean {
        return when (manual.transaction.type) {
            TransactionType.EXPENSE -> cardAccountUid == null || manual.account.uid == cardAccountUid
            TransactionType.TRANSFER -> {
                (cardAccountUid != null && manual.account.uid == cardAccountUid) ||
                    (cardAccountUid == null && manual.account.accountType == AccountType.CREDIT_CARD &&
                        manual.toAccount?.accountType != AccountType.CREDIT_CARD)
            }
            else -> false
        }
    }

    private fun isManualCreditOnCard(manual: TransactionWithDetails, cardAccountUid: String?): Boolean {
        return when (manual.transaction.type) {
            TransactionType.INCOME -> cardAccountUid == null || manual.account.uid == cardAccountUid
            TransactionType.TRANSFER -> {
                (cardAccountUid != null && manual.toAccount?.uid == cardAccountUid) ||
                    (cardAccountUid == null && manual.toAccount?.accountType == AccountType.CREDIT_CARD)
            }
            else -> false
        }
    }

    private fun manualCreditAmountOnCard(manual: TransactionWithDetails): Double {
        return if (manual.transaction.type == TransactionType.TRANSFER) {
            manual.transaction.toAccountAmount ?: manual.transaction.accountAmount
        } else {
            manual.transaction.accountAmount
        }
    }

    private fun Double.round2Decimals(): Double {
        return (this * 100.0).roundToLong() / 100.0
    }
}
