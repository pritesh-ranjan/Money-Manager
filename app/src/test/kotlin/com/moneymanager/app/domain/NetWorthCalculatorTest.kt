package com.moneymanager.app.domain

import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.AccountWithCurrency
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class NetWorthCalculatorTest {

    private val inr = CurrencyEntity(
        uid = "INR",
        name = "INR",
        isoCode = "INR",
        symbol = "₹",
        rateToBase = 1.0,
        isMainCurrency = true
    )

    private val scapiaCoins = CurrencyEntity(
        uid = "SCAPIA",
        name = "Scapia Coins",
        isoCode = "SCAPIA",
        symbol = "🪙",
        rateToBase = 0.20, // 1 coin = 0.20 INR
        isCustom = true
    )

    @Test
    fun testMultiCurrencyAndCustomPointsCalculation() {
        val bankAccount = AccountEntity(
            uid = "bank_1",
            name = "ICICI Savings",
            accountType = AccountType.SAVINGS,
            currencyUid = inr.uid,
            openingBalance = 50000.0
        )

        val scapiaWallet = AccountEntity(
            uid = "scapia_wallet",
            name = "Scapia Coins Wallet",
            accountType = AccountType.WALLET,
            currencyUid = scapiaCoins.uid,
            openingBalance = 10000.0 // 10,000 coins @ 0.2 = ₹2,000
        )

        val creditCard = AccountEntity(
            uid = "cc_1",
            name = "Credit Card",
            accountType = AccountType.CREDIT_CARD,
            currencyUid = inr.uid,
            openingBalance = 0.0
        )

        val accounts = listOf(
            AccountWithCurrency(bankAccount, inr),
            AccountWithCurrency(scapiaWallet, scapiaCoins),
            AccountWithCurrency(creditCard, inr)
        )

        val transactions = listOf(
            // Spend ₹5,000 on credit card
            TransactionEntity(
                uid = "tx1",
                type = TransactionType.EXPENSE,
                accountAmount = 5000.0,
                baseAmount = 5000.0,
                currencyUid = inr.uid,
                accountUid = creditCard.uid
            ),
            // Earn 5,000 Scapia Coins in wallet
            TransactionEntity(
                uid = "tx2",
                type = TransactionType.INCOME,
                accountAmount = 5000.0,
                baseAmount = 1000.0, // 5000 * 0.2 = 1000
                currencyUid = scapiaCoins.uid,
                accountUid = scapiaWallet.uid
            )
        )

        val summary = NetWorthCalculator.calculate(accounts, transactions, inr)

        // Bank balance = 50,000
        // Scapia coins balance = 10,000 + 5,000 = 15,000 coins. Converted to INR @ 0.2 = ₹3,000
        // Total Assets = 50,000 + 3,000 = ₹53,000
        assertEquals(53000.0, summary.totalAssetsBase, 0.001)

        // Credit Card liability = ₹5,000
        assertEquals(5000.0, summary.totalLiabilitiesBase, 0.001)

        // Net Worth = 53,000 - 5,000 = ₹48,000
        assertEquals(48000.0, summary.netWorthBase, 0.001)
    }

    @Test
    fun testApproachA_StaticRevaluationAdjustment() {
        val mutualFunds = AccountEntity(
            uid = "mf_1",
            name = "Mutual Funds",
            accountType = AccountType.MUTUAL_FUNDS,
            currencyUid = inr.uid,
            openingBalance = 500000.0
        )

        // Suppose current balance is 5,00,000 and the user revalues the portfolio to 5,25,000
        val diff = NetWorthCalculator.computeAdjustmentDifference(mutualFunds, 500000.0, 525000.0)
        assertEquals(25000.0, diff, 0.001)

        // Suppose market drops and user revalues to 4,90,000
        val dropDiff = NetWorthCalculator.computeAdjustmentDifference(mutualFunds, 500000.0, 490000.0)
        assertEquals(-10000.0, dropDiff, 0.001)
    }
}
