package com.moneymanager.app.domain

import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.AccountWithCurrency
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType

data class AccountBalanceItem(
    val account: AccountEntity,
    val currency: CurrencyEntity,
    val nativeBalance: Double,
    val baseBalance: Double
)

data class NetWorthSummary(
    val totalAssetsBase: Double,
    val totalLiabilitiesBase: Double,
    val netWorthBase: Double,
    val mainCurrency: CurrencyEntity,
    val accountBalances: List<AccountBalanceItem>
)

object NetWorthCalculator {

    fun calculate(
        accounts: List<AccountWithCurrency>,
        transactions: List<TransactionEntity>,
        mainCurrency: CurrencyEntity
    ): NetWorthSummary {
        // Pre-calculate raw native balance per account
        val balanceMap = mutableMapOf<String, Double>()
        for (acc in accounts) {
            val initial = if (acc.account.accountType.isLiability) {
                -acc.account.openingBalance
            } else {
                acc.account.openingBalance
            }
            balanceMap[acc.account.uid] = initial
        }

        for (tx in transactions) {
            if (tx.isDeleted) continue

            when (tx.type) {
                TransactionType.INCOME -> {
                    val current = balanceMap[tx.accountUid] ?: 0.0
                    balanceMap[tx.accountUid] = current + tx.accountAmount
                }
                TransactionType.EXPENSE -> {
                    val current = balanceMap[tx.accountUid] ?: 0.0
                    balanceMap[tx.accountUid] = current - tx.accountAmount
                }
                TransactionType.TRANSFER -> {
                    val fromBalance = balanceMap[tx.accountUid] ?: 0.0
                    balanceMap[tx.accountUid] = fromBalance - tx.accountAmount

                    val toUid = tx.toAccountUid
                    if (toUid != null) {
                        val toBalance = balanceMap[toUid] ?: 0.0
                        val toAmount = tx.toAccountAmount ?: tx.accountAmount
                        balanceMap[toUid] = toBalance + toAmount
                    }
                }
                TransactionType.ADJUSTMENT -> {
                    val current = balanceMap[tx.accountUid] ?: 0.0
                    balanceMap[tx.accountUid] = current + tx.accountAmount
                }
            }
        }

        var totalAssets = 0.0
        var totalLiabilities = 0.0
        val items = mutableListOf<AccountBalanceItem>()

        for (accWithCurr in accounts) {
            val acc = accWithCurr.account
            val curr = accWithCurr.currency
            val rawBalance = balanceMap[acc.uid] ?: 0.0

            val nativeBalance = if (acc.accountType.isLiability) {
                // For credit cards/loans, balance is reported as positive liability/debt
                -rawBalance
            } else {
                rawBalance
            }

            val baseBalance = nativeBalance * curr.rateToBase

            if (acc.includeInNetWorth && !acc.isArchived) {
                if (acc.accountType.isLiability) {
                    totalLiabilities += maxOf(0.0, baseBalance)
                } else {
                    totalAssets += baseBalance
                }
            }

            items.add(
                AccountBalanceItem(
                    account = acc,
                    currency = curr,
                    nativeBalance = nativeBalance,
                    baseBalance = baseBalance
                )
            )
        }

        return NetWorthSummary(
            totalAssetsBase = totalAssets,
            totalLiabilitiesBase = totalLiabilities,
            netWorthBase = totalAssets - totalLiabilities,
            mainCurrency = mainCurrency,
            accountBalances = items
        )
    }

    /**
     * Approach A Static Revaluation Helper:
     * Computes the adjustment amount needed to set the account to [targetBalance].
     */
    fun computeAdjustmentDifference(
        account: AccountEntity,
        currentNativeBalance: Double,
        targetNativeBalance: Double
    ): Double {
        return if (account.accountType.isLiability) {
            // For credit card/loan, if current debt is 5000 and target debt is 4000,
            // ledger balance changes from -5000 to -4000, which is +1000 difference
            -(targetNativeBalance - currentNativeBalance)
        } else {
            targetNativeBalance - currentNativeBalance
        }
    }
}
