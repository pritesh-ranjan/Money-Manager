package com.moneymanager.app.domain.statement.core

import kotlin.math.abs
import kotlin.math.roundToLong

object StatementReconciliationEngine {

    private const val EPSILON = 0.01

    fun reconcile(
        summary: StatementFinancialSummary,
        transactions: List<ParsedTransaction>
    ): ReconciliationResult {
        val calculatedDebits = transactions
            .filter { !it.isCredit }
            .sumOf { it.amount }
            .round2Decimals()

        val calculatedCredits = transactions
            .filter { it.isCredit }
            .sumOf { it.amount }
            .round2Decimals()

        val debitsMatch = abs(calculatedDebits - summary.purchasesOrDebits) < EPSILON
        val creditsMatch = abs(calculatedCredits - summary.paymentsOrCredits) < EPSILON

        // Compute closing balance
        var calculatedClosing = summary.previousBalance + calculatedDebits - calculatedCredits + summary.cashAdvances

        // If in-statement cashback directly impacts payable amount and was not already included in transaction credits
        val rewardSummary = summary.rewardSummary
        val cashbackAlreadyInCredits = transactions.any {
            it.isCredit && (it.cleanNarration.contains("cashback", ignoreCase = true) ||
                it.cleanNarration.contains("cash back", ignoreCase = true))
        }

        if (rewardSummary != null &&
            rewardSummary.rewardType == StatementRewardType.IN_STATEMENT_CASHBACK &&
            rewardSummary.impactsPayableAmount &&
            !cashbackAlreadyInCredits
        ) {
            calculatedClosing -= rewardSummary.earned
        }
        calculatedClosing = calculatedClosing.round2Decimals()

        // Tolerance: allows up to 1.0 unit for banks that round Total Amount Due to nearest whole rupee
        val closingMatch = abs(calculatedClosing - summary.totalAmountDue) <= 1.0

        // Reward points / cashback reconciliation
        var rewardMatches = true
        var calculatedReward: Double? = null
        var expectedReward: Double? = null

        if (rewardSummary != null) {
            if (rewardSummary.rewardType == StatementRewardType.IN_STATEMENT_CASHBACK) {
                val cashbackTxSum = transactions
                    .filter {
                        it.isCredit && (it.cleanNarration.contains("cashback", ignoreCase = true) ||
                            it.cleanNarration.contains("cash back", ignoreCase = true))
                    }
                    .sumOf { it.amount }
                    .round2Decimals()
                calculatedReward = cashbackTxSum
                val targetCashback = rewardSummary.redeemedOrTransferred ?: rewardSummary.earned
                expectedReward = targetCashback
                rewardMatches = abs(cashbackTxSum - targetCashback) < 0.1
            } else if (rewardSummary.rewardType == StatementRewardType.LOYALTY_POINTS) {
                val hasPerTxnPoints = transactions.any { it.rewardPoints != null }
                if (hasPerTxnPoints) {
                    val totalPoints = transactions.sumOf { (it.rewardPoints ?: 0).toDouble() }
                    calculatedReward = totalPoints
                    expectedReward = rewardSummary.earned
                    rewardMatches = abs(totalPoints - rewardSummary.earned) < EPSILON
                } else {
                    val points = rewardSummary.closingBalance ?: rewardSummary.earned
                    calculatedReward = points
                    expectedReward = points
                    rewardMatches = true
                }
            } else {
                // EXTERNAL_WALLET_CASHBACK or other reward types
                val totalPoints = transactions.sumOf { (it.rewardPoints ?: 0).toDouble() }
                calculatedReward = totalPoints
                expectedReward = rewardSummary.earned
                rewardMatches = abs(totalPoints - rewardSummary.earned) < EPSILON
            }
        }

        val discrepancies = mutableListOf<String>()
        if (!debitsMatch) {
            discrepancies.add(
                "Debits mismatch: extracted sum ₹$calculatedDebits != summary purchases ₹${summary.purchasesOrDebits}"
            )
        }
        if (!creditsMatch) {
            discrepancies.add(
                "Credits mismatch: extracted sum ₹$calculatedCredits != summary payments ₹${summary.paymentsOrCredits}"
            )
        }
        if (!closingMatch) {
            discrepancies.add(
                "Closing balance mismatch: calculated ₹$calculatedClosing != statement Total Due ₹${summary.totalAmountDue}"
            )
        }
        if (!rewardMatches && rewardSummary != null) {
            discrepancies.add(
                "Reward points mismatch: extracted total $calculatedReward != statement earned ${rewardSummary.earned}"
            )
        }

        val isReconciled = debitsMatch && creditsMatch && closingMatch && rewardMatches

        return ReconciliationResult(
            isReconciled = isReconciled,
            openingBalance = summary.previousBalance,
            calculatedDebits = calculatedDebits,
            expectedDebits = summary.purchasesOrDebits,
            debitsMatch = debitsMatch,
            calculatedCredits = calculatedCredits,
            expectedCredits = summary.paymentsOrCredits,
            creditsMatch = creditsMatch,
            calculatedClosingBalance = calculatedClosing,
            expectedClosingBalance = summary.totalAmountDue,
            closingBalanceMatch = closingMatch,
            calculatedRewardTotal = calculatedReward,
            expectedRewardTotal = expectedReward,
            rewardMatches = rewardMatches,
            discrepancies = discrepancies
        )
    }

    private fun Double.round2Decimals(): Double {
        return (this * 100.0).roundToLong() / 100.0
    }
}
