package com.moneymanager.app.domain

import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.data.local.entity.TransactionWithDetails
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class StoryInsightType {
    EXPENSE_TREND,
    CATEGORY_TREND,
    INCOME_GROWTH,
    SAVINGS_RATE,
    INVESTMENT_RATE
}

enum class StoryTrendDirection {
    POSITIVE, // e.g., expenses down, income up, high savings rate (Emerald Green)
    NEGATIVE, // e.g., expenses up, income down, deficit (Coral / Warning)
    NEUTRAL   // Steady or informational
}

data class StoryInsightCard(
    val type: StoryInsightType,
    val title: String,
    val subtitle: String,
    val percentValue: Double?, // null if baseline was zero or not applicable
    val rawAmountDiff: Double,
    val trendDirection: StoryTrendDirection,
    val iconEmoji: String? = null,
    val categoryName: String? = null,
    val priorityScore: Double = 0.0,
    val underlyingTransactions: List<TransactionWithDetails> = emptyList()
)

data class MonthlyMoneyStory(
    val targetMonthLabel: String,         // e.g. "Your September Money Story"
    val comparisonSubtitle: String,       // e.g. "Compared with August" or "Compared with Aug 1–15 (same period)"
    val isEquivalentPeriod: Boolean,      // true if partial month comparison
    val cards: List<StoryInsightCard>
)

data class PeriodWindow(
    val startMillis: Long,
    val endMillis: Long,
    val isPartial: Boolean,
    val dayLimit: Int
)

object MonthlyMoneyStoryEngine {

    val INVESTMENT_ACCOUNT_TYPES = setOf(
        AccountType.INVESTMENT,
        AccountType.EQUITY,
        AccountType.MUTUAL_FUNDS
    )

    /**
     * Calculates percentage change: ((current - previous) / previous) * 100.
     * Safely handles zero baselines:
     * - Returns 0.0 if both are 0.0
     * - Returns null if previous is 0.0 and current > 0.0 (prevents misleading +Infinity%)
     * - Returns -100.0 if previous > 0.0 and current is 0.0
     */
    fun calculatePercentageChange(current: Double, previous: Double): Double? {
        if (previous == 0.0 && current == 0.0) return 0.0
        if (previous == 0.0) return null
        if (current == 0.0) return -100.0
        return ((current - previous) / previous) * 100.0
    }

    /**
     * Calculates savings rate: ((income - expenses) / income) * 100.
     * Returns null if income <= 0.0 to prevent division by zero or nonsensical percentages.
     */
    fun calculateSavingsRate(income: Double, expenses: Double): Double? {
        if (income <= 0.0) return null
        return ((income - expenses) / income) * 100.0
    }

    /**
     * Calculates investment rate: (investmentContributions / income) * 100.
     * Returns null if income <= 0.0.
     */
    fun calculateInvestmentRate(investmentContributions: Double, income: Double): Double? {
        if (income <= 0.0) return null
        return (investmentContributions / income) * 100.0
    }

    fun formatStoryAmount(amount: Double): String {
        val decimals = if (abs(amount % 1.0) < 0.001) 0 else 2
        return CurrencyFormatter.formatIndianNumber(amount, decimals = decimals)
    }

    /**
     * Computes the period windows for target and comparison months.
     * For in-progress months, compares equivalent days (Day 1..D vs Day 1..min(D, compDays)).
     */
    fun calculateWindows(
        targetYm: YearMonth,
        comparisonYm: YearMonth,
        referenceDate: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Pair<PeriodWindow, PeriodWindow> {
        val isTargetCurrentMonth = targetYm == YearMonth.from(referenceDate)

        if (isTargetCurrentMonth) {
            val dayElapsed = referenceDate.dayOfMonth
            val targetStart = targetYm.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val targetEnd = targetYm.atDay(dayElapsed).atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()

            val compMaxDay = comparisonYm.lengthOfMonth()
            val compDay = minOf(dayElapsed, compMaxDay)
            val compStart = comparisonYm.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val compEnd = comparisonYm.atDay(compDay).atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()

            return Pair(
                PeriodWindow(targetStart, targetEnd, isPartial = true, dayLimit = dayElapsed),
                PeriodWindow(compStart, compEnd, isPartial = true, dayLimit = compDay)
            )
        } else {
            val targetStart = targetYm.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val targetEnd = targetYm.atEndOfMonth().atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()

            val compStart = comparisonYm.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val compEnd = comparisonYm.atEndOfMonth().atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()

            return Pair(
                PeriodWindow(targetStart, targetEnd, isPartial = false, dayLimit = targetYm.lengthOfMonth()),
                PeriodWindow(compStart, compEnd, isPartial = false, dayLimit = comparisonYm.lengthOfMonth())
            )
        }
    }

    /**
     * Determines whether a transaction is a credit card bill settlement/payment.
     */
    fun isCreditCardRepayment(item: TransactionWithDetails): Boolean {
        val tx = item.transaction
        return tx.type == TransactionType.TRANSFER && item.toAccount?.accountType == AccountType.CREDIT_CARD
    }

    /**
     * Determines whether a transaction represents an investment contribution.
     * Prevents double-counting by ensuring transfers between two investment accounts are excluded.
     */
    fun isInvestmentContribution(item: TransactionWithDetails, categoriesMap: Map<String, CategoryEntity>): Boolean {
        val tx = item.transaction
        if (tx.isDeleted) return false

        // Transfer from liquid/bank account to an investment account
        if (tx.type == TransactionType.TRANSFER) {
            val destIsInvest = item.toAccount?.accountType in INVESTMENT_ACCOUNT_TYPES
            val srcIsInvest = item.account.accountType in INVESTMENT_ACCOUNT_TYPES
            return destIsInvest && !srcIsInvest
        }

        // Expense categorized under investments or executed directly on an investment account
        if (tx.type == TransactionType.EXPENSE) {
            if (item.account.accountType in INVESTMENT_ACCOUNT_TYPES) return true
            val cat = item.category
            val parentCat = cat?.parentUid?.let { categoriesMap[it] }
            val catName = cat?.name ?: ""
            val parentName = parentCat?.name ?: ""
            if (catName.contains("invest", ignoreCase = true) || parentName.contains("invest", ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    /**
     * Checks if transaction is an internal transfer between personal liquid accounts.
     */
    fun isInternalLiquidTransfer(item: TransactionWithDetails, categoriesMap: Map<String, CategoryEntity>): Boolean {
        val tx = item.transaction
        if (tx.type != TransactionType.TRANSFER) return false
        if (isCreditCardRepayment(item)) return false
        if (isInvestmentContribution(item, categoriesMap)) return false
        return true
    }

    /**
     * Checks if transaction is a refund/credit.
     * Refunds on credit cards, negative expense records, or transactions tagged/noted as refund.
     */
    fun isRefund(item: TransactionWithDetails): Boolean {
        val tx = item.transaction
        if (tx.isDeleted) return false
        if (tx.type == TransactionType.EXPENSE && tx.baseAmount < 0) return true
        if (tx.type == TransactionType.INCOME) {
            if (item.account.accountType == AccountType.CREDIT_CARD) return true
            if (tx.note.contains("refund", ignoreCase = true)) return true
            if (item.category?.name?.contains("refund", ignoreCase = true) == true) return true
        }
        return false
    }

    /**
     * Builds the complete Monthly Money Story for the specified target month.
     */
    fun generateStory(
        targetYm: YearMonth,
        comparisonYm: YearMonth = targetYm.minusMonths(1),
        allTransactions: List<TransactionWithDetails>,
        categoriesMap: Map<String, CategoryEntity>,
        referenceDate: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): MonthlyMoneyStory {
        val (targetWindow, compWindow) = calculateWindows(targetYm, comparisonYm, referenceDate, zone)

        val targetTxs = allTransactions.filter {
            !it.transaction.isDeleted && it.transaction.timestamp in targetWindow.startMillis..targetWindow.endMillis
        }
        val compTxs = allTransactions.filter {
            !it.transaction.isDeleted && it.transaction.timestamp in compWindow.startMillis..compWindow.endMillis
        }

        // 1. Process Target Month Financial Metrics
        val targetInvestmentTxs = targetTxs.filter { isInvestmentContribution(it, categoriesMap) }
        val targetInvestments = targetInvestmentTxs.sumOf { abs(it.transaction.baseAmount) }

        val targetRefundTxs = targetTxs.filter { isRefund(it) }
        val targetRefundTotal = targetRefundTxs.sumOf { abs(it.transaction.baseAmount) }

        val targetLivingExpenseTxs = targetTxs.filter {
            it.transaction.type == TransactionType.EXPENSE &&
                    !isCreditCardRepayment(it) &&
                    !isInternalLiquidTransfer(it, categoriesMap) &&
                    !isInvestmentContribution(it, categoriesMap) &&
                    !isRefund(it)
        }
        val grossTargetExpense = targetLivingExpenseTxs.sumOf { it.transaction.baseAmount }
        val netTargetExpense = (grossTargetExpense - targetRefundTotal).coerceAtLeast(0.0)

        val targetIncomeTxs = targetTxs.filter {
            it.transaction.type == TransactionType.INCOME &&
                    !isRefund(it) &&
                    !isInternalLiquidTransfer(it, categoriesMap)
        }
        val targetIncome = targetIncomeTxs.sumOf { it.transaction.baseAmount }

        // 2. Process Comparison Month Financial Metrics
        val compInvestmentTxs = compTxs.filter { isInvestmentContribution(it, categoriesMap) }
        val compInvestments = compInvestmentTxs.sumOf { abs(it.transaction.baseAmount) }

        val compRefundTxs = compTxs.filter { isRefund(it) }
        val compRefundTotal = compRefundTxs.sumOf { abs(it.transaction.baseAmount) }

        val compLivingExpenseTxs = compTxs.filter {
            it.transaction.type == TransactionType.EXPENSE &&
                    !isCreditCardRepayment(it) &&
                    !isInternalLiquidTransfer(it, categoriesMap) &&
                    !isInvestmentContribution(it, categoriesMap) &&
                    !isRefund(it)
        }
        val grossCompExpense = compLivingExpenseTxs.sumOf { it.transaction.baseAmount }
        val netCompExpense = (grossCompExpense - compRefundTotal).coerceAtLeast(0.0)

        val compIncomeTxs = compTxs.filter {
            it.transaction.type == TransactionType.INCOME &&
                    !isRefund(it) &&
                    !isInternalLiquidTransfer(it, categoriesMap)
        }
        val compIncome = compIncomeTxs.sumOf { it.transaction.baseAmount }

        // Comparison Subtitle formatting
        val targetMonthName = targetYm.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
        val compMonthName = comparisonYm.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
        val targetMonthLabel = "Your $targetMonthName Money Story"
        val comparisonSubtitle = if (targetWindow.isPartial) {
            "Compared with $compMonthName 1–${compWindow.dayLimit} (same period)"
        } else {
            "Compared with $compMonthName"
        }

        val cards = mutableListOf<StoryInsightCard>()

        // -------------------------------------------------------------
        // Insight 1: Overall Expense Trend
        // -------------------------------------------------------------
        val expenseDiff = netTargetExpense - netCompExpense
        val expensePct = calculatePercentageChange(netTargetExpense, netCompExpense)

        val expenseCard = if (netTargetExpense == 0.0 && netCompExpense == 0.0) {
            StoryInsightCard(
                type = StoryInsightType.EXPENSE_TREND,
                title = "No expenses recorded",
                subtitle = "You spent ₹0 in this period.",
                percentValue = 0.0,
                rawAmountDiff = 0.0,
                trendDirection = StoryTrendDirection.NEUTRAL,
                priorityScore = 1.0,
                underlyingTransactions = targetLivingExpenseTxs
            )
        } else if (expensePct != null) {
            val roundedPct = abs(expensePct.roundToInt())
            if (expensePct < -0.5) {
                StoryInsightCard(
                    type = StoryInsightType.EXPENSE_TREND,
                    title = "Your expenses decreased by $roundedPct%",
                    subtitle = "You spent ₹${formatStoryAmount(abs(expenseDiff))} less than last month.",
                    percentValue = expensePct,
                    rawAmountDiff = expenseDiff,
                    trendDirection = StoryTrendDirection.POSITIVE,
                    priorityScore = 10.0 + roundedPct,
                    underlyingTransactions = targetLivingExpenseTxs
                )
            } else if (expensePct > 0.5) {
                StoryInsightCard(
                    type = StoryInsightType.EXPENSE_TREND,
                    title = "Your expenses increased by $roundedPct%",
                    subtitle = "You spent ₹${formatStoryAmount(expenseDiff)} more than last month.",
                    percentValue = expensePct,
                    rawAmountDiff = expenseDiff,
                    trendDirection = StoryTrendDirection.NEGATIVE,
                    priorityScore = 10.0 + roundedPct,
                    underlyingTransactions = targetLivingExpenseTxs
                )
            } else {
                StoryInsightCard(
                    type = StoryInsightType.EXPENSE_TREND,
                    title = "Your expenses remained steady",
                    subtitle = "You spent ₹${formatStoryAmount(netTargetExpense)}, matching last month.",
                    percentValue = 0.0,
                    rawAmountDiff = expenseDiff,
                    trendDirection = StoryTrendDirection.NEUTRAL,
                    priorityScore = 5.0,
                    underlyingTransactions = targetLivingExpenseTxs
                )
            }
        } else {
            // Previous was 0, current > 0
            StoryInsightCard(
                type = StoryInsightType.EXPENSE_TREND,
                title = "Expenses recorded: ₹${formatStoryAmount(netTargetExpense)}",
                subtitle = "No expenses were logged in the previous period.",
                percentValue = null,
                rawAmountDiff = netTargetExpense,
                trendDirection = StoryTrendDirection.NEUTRAL,
                priorityScore = 8.0,
                underlyingTransactions = targetLivingExpenseTxs
            )
        }
        cards.add(expenseCard)

        // -------------------------------------------------------------
        // Insight 2: Category-wise Spending Insight
        // -------------------------------------------------------------
        val targetCatGroups = targetLivingExpenseTxs.groupBy { item ->
            val cat = item.category
            val parent = cat?.parentUid?.let { categoriesMap[it] }
            parent ?: cat
        }
        val compCatGroups = compLivingExpenseTxs.groupBy { item ->
            val cat = item.category
            val parent = cat?.parentUid?.let { categoriesMap[it] }
            parent ?: cat
        }

        val allCategoryKeys = (targetCatGroups.keys + compCatGroups.keys).filterNotNull().distinctBy { it.uid }

        val categoryEvaluations = allCategoryKeys.map { cat ->
            val targetList = targetCatGroups.entries.firstOrNull { it.key?.uid == cat.uid }?.value ?: emptyList()
            val compList = compCatGroups.entries.firstOrNull { it.key?.uid == cat.uid }?.value ?: emptyList()

            val targetSum = targetList.sumOf { it.transaction.baseAmount }
            val compSum = compList.sumOf { it.transaction.baseAmount }
            val diff = targetSum - compSum
            val pct = calculatePercentageChange(targetSum, compSum)

            CategoryEvaluation(
                category = cat,
                targetAmount = targetSum,
                compAmount = compSum,
                diff = diff,
                percentChange = pct,
                transactions = targetList
            )
        }

        // Prioritize significant increase first, or notable decrease, or top spend
        val topCategoryInsight = categoryEvaluations
            .filter { it.targetAmount > 0 || it.compAmount > 0 }
            .sortedWith(
                compareByDescending<CategoryEvaluation> { it.diff > 0 }
                    .thenByDescending { abs(it.diff) }
                    .thenByDescending { it.targetAmount }
            )
            .firstOrNull()

        if (topCategoryInsight != null) {
            val cat = topCategoryInsight.category
            val diff = topCategoryInsight.diff
            val pct = topCategoryInsight.percentChange
            val targetAmount = topCategoryInsight.targetAmount

            val card = if (pct != null && pct > 0.5) {
                val roundedPct = pct.roundToInt()
                StoryInsightCard(
                    type = StoryInsightType.CATEGORY_TREND,
                    title = "${cat.name} expenses increased by $roundedPct%",
                    subtitle = "You spent ₹${formatStoryAmount(diff)} more on ${cat.name.lowercase()}.",
                    percentValue = pct,
                    rawAmountDiff = diff,
                    trendDirection = StoryTrendDirection.NEGATIVE,
                    iconEmoji = cat.icon,
                    categoryName = cat.name,
                    priorityScore = 9.0 + roundedPct,
                    underlyingTransactions = topCategoryInsight.transactions
                )
            } else if (pct != null && pct < -0.5) {
                val roundedPct = abs(pct.roundToInt())
                StoryInsightCard(
                    type = StoryInsightType.CATEGORY_TREND,
                    title = "${cat.name} expenses decreased by $roundedPct%",
                    subtitle = "You spent ₹${formatStoryAmount(abs(diff))} less on ${cat.name.lowercase()}.",
                    percentValue = pct,
                    rawAmountDiff = diff,
                    trendDirection = StoryTrendDirection.POSITIVE,
                    iconEmoji = cat.icon,
                    categoryName = cat.name,
                    priorityScore = 8.0 + roundedPct,
                    underlyingTransactions = topCategoryInsight.transactions
                )
            } else if (pct == null && targetAmount > 0) {
                StoryInsightCard(
                    type = StoryInsightType.CATEGORY_TREND,
                    title = "${cat.name} is a new expense this month",
                    subtitle = "You spent ₹${formatStoryAmount(targetAmount)} on ${cat.name.lowercase()}.",
                    percentValue = null,
                    rawAmountDiff = targetAmount,
                    trendDirection = StoryTrendDirection.NEUTRAL,
                    iconEmoji = cat.icon,
                    categoryName = cat.name,
                    priorityScore = 7.0,
                    underlyingTransactions = topCategoryInsight.transactions
                )
            } else {
                StoryInsightCard(
                    type = StoryInsightType.CATEGORY_TREND,
                    title = "${cat.name} spending was steady",
                    subtitle = "You spent ₹${formatStoryAmount(targetAmount)} on ${cat.name.lowercase()}.",
                    percentValue = 0.0,
                    rawAmountDiff = diff,
                    trendDirection = StoryTrendDirection.NEUTRAL,
                    iconEmoji = cat.icon,
                    categoryName = cat.name,
                    priorityScore = 4.0,
                    underlyingTransactions = topCategoryInsight.transactions
                )
            }
            cards.add(card)
        }

        // -------------------------------------------------------------
        // Insight 3: Income Growth
        // -------------------------------------------------------------
        val incomeDiff = targetIncome - compIncome
        val incomePct = calculatePercentageChange(targetIncome, compIncome)

        val incomeCard = if (targetIncome == 0.0 && compIncome == 0.0) {
            StoryInsightCard(
                type = StoryInsightType.INCOME_GROWTH,
                title = "No income recorded",
                subtitle = "No income logged for this period.",
                percentValue = 0.0,
                rawAmountDiff = 0.0,
                trendDirection = StoryTrendDirection.NEUTRAL,
                priorityScore = 2.0,
                underlyingTransactions = targetIncomeTxs
            )
        } else if (incomePct != null) {
            val roundedPct = abs(incomePct.roundToInt())
            if (incomePct > 0.5) {
                StoryInsightCard(
                    type = StoryInsightType.INCOME_GROWTH,
                    title = "Your income increased by $roundedPct%",
                    subtitle = "You earned ₹${formatStoryAmount(incomeDiff)} more than last month.",
                    percentValue = incomePct,
                    rawAmountDiff = incomeDiff,
                    trendDirection = StoryTrendDirection.POSITIVE,
                    priorityScore = 9.5 + roundedPct,
                    underlyingTransactions = targetIncomeTxs
                )
            } else if (incomePct < -0.5) {
                StoryInsightCard(
                    type = StoryInsightType.INCOME_GROWTH,
                    title = "Your income decreased by $roundedPct%",
                    subtitle = "You earned ₹${formatStoryAmount(abs(incomeDiff))} less than last month.",
                    percentValue = incomePct,
                    rawAmountDiff = incomeDiff,
                    trendDirection = StoryTrendDirection.NEGATIVE,
                    priorityScore = 9.0 + roundedPct,
                    underlyingTransactions = targetIncomeTxs
                )
            } else {
                StoryInsightCard(
                    type = StoryInsightType.INCOME_GROWTH,
                    title = "Your income remained steady",
                    subtitle = "You earned ₹${formatStoryAmount(targetIncome)}, matching last month.",
                    percentValue = 0.0,
                    rawAmountDiff = incomeDiff,
                    trendDirection = StoryTrendDirection.NEUTRAL,
                    priorityScore = 4.0,
                    underlyingTransactions = targetIncomeTxs
                )
            }
        } else {
            StoryInsightCard(
                type = StoryInsightType.INCOME_GROWTH,
                title = "Income recorded: ₹${formatStoryAmount(targetIncome)}",
                subtitle = "First income logged compared to previous period.",
                percentValue = null,
                rawAmountDiff = targetIncome,
                trendDirection = StoryTrendDirection.POSITIVE,
                priorityScore = 8.5,
                underlyingTransactions = targetIncomeTxs
            )
        }
        cards.add(incomeCard)

        // -------------------------------------------------------------
        // Insight 4: Savings Rate
        // -------------------------------------------------------------
        val savedAmount = targetIncome - netTargetExpense
        val savingsRate = calculateSavingsRate(targetIncome, netTargetExpense)

        val savingsCard = if (savingsRate != null) {
            val roundedRate = savingsRate.roundToInt()
            if (savingsRate >= 0.0) {
                StoryInsightCard(
                    type = StoryInsightType.SAVINGS_RATE,
                    title = "You saved $roundedRate% of your income",
                    subtitle = "₹${formatStoryAmount(savedAmount)} saved from ₹${formatStoryAmount(targetIncome)} income.",
                    percentValue = savingsRate,
                    rawAmountDiff = savedAmount,
                    trendDirection = if (savingsRate >= 20.0) StoryTrendDirection.POSITIVE else StoryTrendDirection.NEUTRAL,
                    iconEmoji = "👛",
                    priorityScore = 11.0,
                    underlyingTransactions = targetIncomeTxs + targetLivingExpenseTxs
                )
            } else {
                StoryInsightCard(
                    type = StoryInsightType.SAVINGS_RATE,
                    title = "You spent more than your income",
                    subtitle = "₹${formatStoryAmount(abs(savedAmount))} deficit over ₹${formatStoryAmount(targetIncome)} income.",
                    percentValue = savingsRate,
                    rawAmountDiff = savedAmount,
                    trendDirection = StoryTrendDirection.NEGATIVE,
                    iconEmoji = "👛",
                    priorityScore = 12.0,
                    underlyingTransactions = targetIncomeTxs + targetLivingExpenseTxs
                )
            }
        } else {
            if (netTargetExpense > 0.0) {
                StoryInsightCard(
                    type = StoryInsightType.SAVINGS_RATE,
                    title = "Expenses exceeded income",
                    subtitle = "₹${formatStoryAmount(netTargetExpense)} spent with no income recorded.",
                    percentValue = null,
                    rawAmountDiff = -netTargetExpense,
                    trendDirection = StoryTrendDirection.NEGATIVE,
                    iconEmoji = "👛",
                    priorityScore = 7.0,
                    underlyingTransactions = targetLivingExpenseTxs
                )
            } else {
                StoryInsightCard(
                    type = StoryInsightType.SAVINGS_RATE,
                    title = "No savings rate available",
                    subtitle = "Log income and expenses to view your savings rate.",
                    percentValue = null,
                    rawAmountDiff = 0.0,
                    trendDirection = StoryTrendDirection.NEUTRAL,
                    iconEmoji = "👛",
                    priorityScore = 1.0,
                    underlyingTransactions = emptyList()
                )
            }
        }
        cards.add(savingsCard)

        // -------------------------------------------------------------
        // Insight 5: Investment Contributions
        // -------------------------------------------------------------
        val investRate = calculateInvestmentRate(targetInvestments, targetIncome)

        val investCard = if (targetInvestments > 0.0) {
            if (investRate != null && investRate > 0.0) {
                val roundedRate = investRate.roundToInt()
                StoryInsightCard(
                    type = StoryInsightType.INVESTMENT_RATE,
                    title = "You invested $roundedRate% of your income",
                    subtitle = "₹${formatStoryAmount(targetInvestments)} contributed to investments.",
                    percentValue = investRate,
                    rawAmountDiff = targetInvestments,
                    trendDirection = StoryTrendDirection.POSITIVE,
                    iconEmoji = "📈",
                    priorityScore = 10.5,
                    underlyingTransactions = targetInvestmentTxs
                )
            } else {
                StoryInsightCard(
                    type = StoryInsightType.INVESTMENT_RATE,
                    title = "You invested ₹${formatStoryAmount(targetInvestments)}",
                    subtitle = "₹${formatStoryAmount(targetInvestments)} contributed towards your portfolio.",
                    percentValue = null,
                    rawAmountDiff = targetInvestments,
                    trendDirection = StoryTrendDirection.POSITIVE,
                    iconEmoji = "📈",
                    priorityScore = 9.0,
                    underlyingTransactions = targetInvestmentTxs
                )
            }
        } else {
            if (targetIncome > 0.0) {
                StoryInsightCard(
                    type = StoryInsightType.INVESTMENT_RATE,
                    title = "No investments made this month",
                    subtitle = "Consider setting aside savings towards investments.",
                    percentValue = 0.0,
                    rawAmountDiff = 0.0,
                    trendDirection = StoryTrendDirection.NEUTRAL,
                    iconEmoji = "📈",
                    priorityScore = 3.0,
                    underlyingTransactions = emptyList()
                )
            } else {
                StoryInsightCard(
                    type = StoryInsightType.INVESTMENT_RATE,
                    title = "No investments recorded",
                    subtitle = "Track portfolio contributions by linking investment accounts.",
                    percentValue = 0.0,
                    rawAmountDiff = 0.0,
                    trendDirection = StoryTrendDirection.NEUTRAL,
                    iconEmoji = "📈",
                    priorityScore = 1.5,
                    underlyingTransactions = emptyList()
                )
            }
        }
        cards.add(investCard)

        // Return top 3-5 prioritized cards (ensuring minimum 3 and maximum 5)
        val selectedCards = cards.sortedByDescending { it.priorityScore }.take(5)

        return MonthlyMoneyStory(
            targetMonthLabel = targetMonthLabel,
            comparisonSubtitle = comparisonSubtitle,
            isEquivalentPeriod = targetWindow.isPartial,
            cards = selectedCards
        )
    }

    private data class CategoryEvaluation(
        val category: CategoryEntity,
        val targetAmount: Double,
        val compAmount: Double,
        val diff: Double,
        val percentChange: Double?,
        val transactions: List<TransactionWithDetails>
    )
}
