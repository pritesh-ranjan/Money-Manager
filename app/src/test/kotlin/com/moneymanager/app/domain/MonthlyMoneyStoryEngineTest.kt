package com.moneymanager.app.domain

import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryType
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.data.local.entity.TransactionWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MonthlyMoneyStoryEngineTest {

    private val defaultCurrency = CurrencyEntity("INR", "INR", "INR", "₹", 1.0, true)

    private val savingsAccount = AccountEntity(
        uid = "ACC_SAVINGS",
        name = "HDFC Bank",
        accountType = AccountType.SAVINGS,
        currencyUid = "INR"
    )

    private val cashAccount = AccountEntity(
        uid = "ACC_CASH",
        name = "Cash",
        accountType = AccountType.CASH,
        currencyUid = "INR"
    )

    private val creditCardAccount = AccountEntity(
        uid = "ACC_CC",
        name = "Infinia Card",
        accountType = AccountType.CREDIT_CARD,
        currencyUid = "INR"
    )

    private val mutualFundAccount = AccountEntity(
        uid = "ACC_MF",
        name = "Zerodha Coin MF",
        accountType = AccountType.MUTUAL_FUNDS,
        currencyUid = "INR"
    )

    private val equityAccount = AccountEntity(
        uid = "ACC_EQ",
        name = "Zerodha Kite",
        accountType = AccountType.EQUITY,
        currencyUid = "INR"
    )

    private val diningCategory = CategoryEntity(
        uid = "CAT_DINING",
        name = "Dining",
        type = CategoryType.EXPENSE,
        icon = "🍜"
    )

    private val shoppingCategory = CategoryEntity(
        uid = "CAT_SHOPPING",
        name = "Shopping",
        type = CategoryType.EXPENSE,
        icon = "🛒"
    )

    private val salaryCategory = CategoryEntity(
        uid = "CAT_SALARY",
        name = "Salary",
        type = CategoryType.INCOME,
        icon = "💰"
    )

    private val refundCategory = CategoryEntity(
        uid = "CAT_REFUND",
        name = "Refund",
        type = CategoryType.INCOME,
        icon = "↩️"
    )

    private val categoriesMap = mapOf(
        diningCategory.uid to diningCategory,
        shoppingCategory.uid to shoppingCategory,
        salaryCategory.uid to salaryCategory,
        refundCategory.uid to refundCategory
    )

    @Test
    fun testCalculatePercentageChange_normalPositive() {
        val pct = MonthlyMoneyStoryEngine.calculatePercentageChange(125.0, 100.0)
        assertNotNull(pct)
        assertEquals(25.0, pct!!, 0.001)
    }

    @Test
    fun testCalculatePercentageChange_normalNegative() {
        val pct = MonthlyMoneyStoryEngine.calculatePercentageChange(88.0, 100.0)
        assertNotNull(pct)
        assertEquals(-12.0, pct!!, 0.001)
    }

    @Test
    fun testCalculatePercentageChange_zeroBaselineSafeguards() {
        // Both zero: 0% change
        val bothZero = MonthlyMoneyStoryEngine.calculatePercentageChange(0.0, 0.0)
        assertEquals(0.0, bothZero!!, 0.001)

        // Previous zero, current positive: must return null to prevent misleading +Infinity%
        val prevZero = MonthlyMoneyStoryEngine.calculatePercentageChange(5000.0, 0.0)
        assertNull("Zero baseline must return null percentage to avoid misleading infinity", prevZero)

        // Previous positive, current zero: -100%
        val currZero = MonthlyMoneyStoryEngine.calculatePercentageChange(0.0, 2000.0)
        assertEquals(-100.0, currZero!!, 0.001)
    }

    @Test
    fun testCalculateSavingsRate_normalAndEdgeCases() {
        // 100k income, 68k expenses -> 32k saved = 32%
        val rate = MonthlyMoneyStoryEngine.calculateSavingsRate(100_000.0, 68_000.0)
        assertNotNull(rate)
        assertEquals(32.0, rate!!, 0.001)

        // Zero income: returns null to prevent divide by zero
        assertNull(MonthlyMoneyStoryEngine.calculateSavingsRate(0.0, 20_000.0))
        assertNull(MonthlyMoneyStoryEngine.calculateSavingsRate(-500.0, 20_000.0))

        // Negative savings (expenses > income)
        val deficitRate = MonthlyMoneyStoryEngine.calculateSavingsRate(50_000.0, 60_000.0)
        assertNotNull(deficitRate)
        assertEquals(-20.0, deficitRate!!, 0.001)
    }

    @Test
    fun testCalculateInvestmentRate_normalAndEdgeCases() {
        // 20k investments, 100k income -> 20%
        val rate = MonthlyMoneyStoryEngine.calculateInvestmentRate(20_000.0, 100_000.0)
        assertNotNull(rate)
        assertEquals(20.0, rate!!, 0.001)

        // Zero income: returns null
        assertNull(MonthlyMoneyStoryEngine.calculateInvestmentRate(5_000.0, 0.0))
    }

    @Test
    fun testCalculateWindows_completedMonthComparison() {
        val sep2026 = YearMonth.of(2026, 9)
        val aug2026 = YearMonth.of(2026, 8)
        val refDate = LocalDate.of(2026, 10, 29)

        val (targetWindow, compWindow) = MonthlyMoneyStoryEngine.calculateWindows(sep2026, aug2026, refDate)
        assertFalse(targetWindow.isPartial)
        assertFalse(compWindow.isPartial)
        assertEquals(30, targetWindow.dayLimit)
        assertEquals(31, compWindow.dayLimit)
    }

    @Test
    fun testCalculateWindows_incompleteCurrentMonthEquivalentComparison() {
        val oct2026 = YearMonth.of(2026, 10)
        val sep2026 = YearMonth.of(2026, 9)
        val refDate = LocalDate.of(2026, 10, 15) // Day 15 of October

        val (targetWindow, compWindow) = MonthlyMoneyStoryEngine.calculateWindows(oct2026, sep2026, refDate)
        assertTrue(targetWindow.isPartial)
        assertTrue(compWindow.isPartial)
        assertEquals(15, targetWindow.dayLimit)
        assertEquals(15, compWindow.dayLimit)
    }

    @Test
    fun testCreditCardRepayment_excludedFromExpenses() {
        val tx = createTx(
            uid = "tx_cc_pay",
            type = TransactionType.TRANSFER,
            amount = 15_000.0,
            fromAccount = savingsAccount,
            toAccount = creditCardAccount
        )
        assertTrue(MonthlyMoneyStoryEngine.isCreditCardRepayment(tx))
        assertFalse(MonthlyMoneyStoryEngine.isInternalLiquidTransfer(tx, categoriesMap))
        assertFalse(MonthlyMoneyStoryEngine.isInvestmentContribution(tx, categoriesMap))
    }

    @Test
    fun testInternalTransfer_excludedFromExpensesAndIncome() {
        val tx = createTx(
            uid = "tx_internal",
            type = TransactionType.TRANSFER,
            amount = 5_000.0,
            fromAccount = savingsAccount,
            toAccount = cashAccount
        )
        assertTrue(MonthlyMoneyStoryEngine.isInternalLiquidTransfer(tx, categoriesMap))
        assertFalse(MonthlyMoneyStoryEngine.isCreditCardRepayment(tx))
        assertFalse(MonthlyMoneyStoryEngine.isInvestmentContribution(tx, categoriesMap))
    }

    @Test
    fun testInvestmentContribution_excludesInterInvestmentTransfers() {
        // Transfer from Savings (liquid) to Mutual Funds: IS an investment contribution
        val contributionTx = createTx(
            uid = "tx_invest",
            type = TransactionType.TRANSFER,
            amount = 20_000.0,
            fromAccount = savingsAccount,
            toAccount = mutualFundAccount
        )
        assertTrue(MonthlyMoneyStoryEngine.isInvestmentContribution(contributionTx, categoriesMap))

        // Transfer between Mutual Funds and Equity: IS NOT a new contribution (prevents double counting!)
        val rebalanceTx = createTx(
            uid = "tx_rebalance",
            type = TransactionType.TRANSFER,
            amount = 10_000.0,
            fromAccount = equityAccount,
            toAccount = mutualFundAccount
        )
        assertFalse("Rebalancing between two investment accounts must not double-count",
            MonthlyMoneyStoryEngine.isInvestmentContribution(rebalanceTx, categoriesMap))
    }

    @Test
    fun testRefundAccounting_reducesExpenses() {
        val refundTx = createTx(
            uid = "tx_refund",
            type = TransactionType.INCOME,
            amount = 2_000.0,
            fromAccount = creditCardAccount,
            category = refundCategory,
            note = "Merchant Refund for returned shoes"
        )
        assertTrue(MonthlyMoneyStoryEngine.isRefund(refundTx))
    }

    @Test
    fun testGenerateStory_matchesUserMockupScenario() {
        // Scenario matching the user's mockup:
        // September 2026 (target):
        // Income: 1,00,000
        // Total Expenses: 68,000 (spent 24,000 less than August which was 92,000 -> 12% decrease: (92k - 24k) = 68k)
        // Shopping expenses: 10,000 (increased by 25% from August which was 8,000 -> spent 2,000 more)
        // Saved: 32,000 (32% of 1,00,000)
        // Invested: 20,000 (20% of 1,00,000)
        val zone = ZoneId.systemDefault()
        val sep15 = LocalDate.of(2026, 9, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        val aug15 = LocalDate.of(2026, 8, 15).atStartOfDay(zone).toInstant().toEpochMilli()

        val transactions = listOf(
            // September Target
            createTx("tx_sep_inc", TransactionType.INCOME, 100_000.0, savingsAccount, category = salaryCategory, timestamp = sep15),
            createTx("tx_sep_shop", TransactionType.EXPENSE, 10_000.0, creditCardAccount, category = shoppingCategory, timestamp = sep15),
            createTx("tx_sep_dining", TransactionType.EXPENSE, 58_000.0, creditCardAccount, category = diningCategory, timestamp = sep15),
            createTx("tx_sep_inv", TransactionType.TRANSFER, 20_000.0, savingsAccount, toAccount = mutualFundAccount, timestamp = sep15),

            // August Baseline
            createTx("tx_aug_inc", TransactionType.INCOME, 100_000.0, savingsAccount, category = salaryCategory, timestamp = aug15),
            createTx("tx_aug_shop", TransactionType.EXPENSE, 8_000.0, creditCardAccount, category = shoppingCategory, timestamp = aug15),
            createTx("tx_aug_dining", TransactionType.EXPENSE, 84_000.0, creditCardAccount, category = diningCategory, timestamp = aug15)
        )

        val story = MonthlyMoneyStoryEngine.generateStory(
            targetYm = YearMonth.of(2026, 9),
            comparisonYm = YearMonth.of(2026, 8),
            allTransactions = transactions,
            categoriesMap = categoriesMap,
            referenceDate = LocalDate.of(2026, 10, 29)
        )

        assertEquals("Your September Money Story", story.targetMonthLabel)
        assertEquals("Compared with August", story.comparisonSubtitle)
        assertTrue(story.cards.size in 3..5)

        // Verify Expense Trend Card
        val expenseCard = story.cards.firstOrNull { it.type == StoryInsightType.EXPENSE_TREND }
        assertNotNull(expenseCard)
        assertEquals("Your expenses decreased by 26%", expenseCard!!.title) // (68000 - 92000)/92000 = -26.08%
        assertTrue(expenseCard.subtitle.contains("less than last month"))

        // Verify Category Trend Card (Shopping)
        val shoppingCard = story.cards.firstOrNull { it.type == StoryInsightType.CATEGORY_TREND }
        assertNotNull(shoppingCard)
        assertEquals("Shopping expenses increased by 25%", shoppingCard!!.title)
        assertEquals("You spent ₹2,000 more on shopping.", shoppingCard.subtitle)

        // Verify Savings Rate Card
        val savingsCard = story.cards.firstOrNull { it.type == StoryInsightType.SAVINGS_RATE }
        assertNotNull(savingsCard)
        assertEquals("You saved 32% of your income", savingsCard!!.title)
        assertEquals("₹32,000 saved from ₹1,00,000 income.", savingsCard.subtitle)

        // Verify Investment Rate Card
        val investCard = story.cards.firstOrNull { it.type == StoryInsightType.INVESTMENT_RATE }
        assertNotNull(investCard)
        assertEquals("You invested 20% of your income", investCard!!.title)
        assertEquals("₹20,000 contributed to investments.", investCard.subtitle)
    }

    private fun createTx(
        uid: String,
        type: TransactionType,
        amount: Double,
        fromAccount: AccountEntity,
        toAccount: AccountEntity? = null,
        category: CategoryEntity? = null,
        note: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): TransactionWithDetails {
        return TransactionWithDetails(
            transaction = TransactionEntity(
                uid = uid,
                type = type,
                accountAmount = amount,
                baseAmount = amount,
                exchangeRate = 1.0,
                currencyUid = defaultCurrency.uid,
                accountUid = fromAccount.uid,
                toAccountUid = toAccount?.uid,
                toAccountAmount = if (toAccount != null) amount else null,
                categoryUid = category?.uid,
                note = note,
                timestamp = timestamp
            ),
            account = fromAccount,
            toAccount = toAccount,
            category = category,
            currency = defaultCurrency
        )
    }
}
