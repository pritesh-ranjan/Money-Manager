package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.InsightSeverity
import com.moneymanager.app.data.local.entity.InsightType
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.domain.CurrencyFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

object CategorySpikeDetector {

    fun detect(
        allTransactions: List<TransactionEntity>,
        categoriesMap: Map<String, CategoryEntity>,
        referenceDate: LocalDate = LocalDate.now(),
        config: DetectorConfig,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<DetectedInsight> {
        val insights = mutableListOf<DetectedInsight>()

        val currentYm = YearMonth.from(referenceDate)
        val currentDay = referenceDate.dayOfMonth

        // 1. Current month period: days 1..currentDay
        val currentStartMillis = currentYm.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val currentEndMillis = currentYm.atDay(currentDay).atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()

        val currentMonthTxs = allTransactions.filter {
            !it.isDeleted &&
            it.type == TransactionType.EXPENSE &&
            it.timestamp in currentStartMillis..currentEndMillis
        }

        // Group current month spending by root category
        val currentCategorySpending = currentMonthTxs.groupBy {
            BaselineCalculator.resolveRootCategoryUid(it.categoryUid, categoriesMap) ?: "uncategorized"
        }

        // 2. Build baseline months (e.g. previous 3 to 6 months)
        val lookback = config.lookbackMonths
        val baselineMonths = (1..lookback).map { currentYm.minusMonths(it.toLong()) }

        for ((catUid, catTxs) in currentCategorySpending) {
            val currentTotal = catTxs.sumOf { it.baseAmount }

            // Compute equivalent-period spending in each baseline month
            val baselineMonthTotals = mutableListOf<Double>()
            for (bm in baselineMonths) {
                val maxDay = minOf(currentDay, bm.lengthOfMonth())
                val bmStart = bm.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val bmEnd = bm.atDay(maxDay).atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()

                val bmTxs = allTransactions.filter {
                    !it.isDeleted &&
                    it.type == TransactionType.EXPENSE &&
                    it.timestamp in bmStart..bmEnd &&
                    (BaselineCalculator.resolveRootCategoryUid(it.categoryUid, categoriesMap) ?: "uncategorized") == catUid
                }
                baselineMonthTotals.add(bmTxs.sumOf { it.baseAmount })
            }

            // Require sufficient baseline months with activity
            val nonZeroMonths = baselineMonthTotals.count { it > 0.0 }
            if (nonZeroMonths < config.spikeMinBaselineMonths) continue

            val previousAvg = baselineMonthTotals.average()
            if (previousAvg <= 0.0) continue

            val diff = currentTotal - previousAvg
            if (diff < config.spikeMinAbsoluteIncrease) continue

            val pctChange = RobustStatistics.calculatePercentageChange(currentTotal, previousAvg) ?: continue
            if (pctChange < config.spikeThresholdPercent) continue

            val category = categoriesMap[catUid]
            val catName = category?.name ?: "Uncategorized"
            val icon = category?.icon ?: "📁"

            val severity = if (pctChange >= 100.0) InsightSeverity.HIGH else InsightSeverity.WARNING
            val formattedCurrent = CurrencyFormatter.formatIndianNumber(currentTotal, decimals = 0)
            val formattedAvg = CurrencyFormatter.formatIndianNumber(previousAvg, decimals = 0)
            val formattedDiff = CurrencyFormatter.formatIndianNumber(diff, decimals = 0)

            val summary = "$icon Spending on $catName is +${String.format("%.0f", pctChange)}% higher than your historical baseline (₹$formattedCurrent vs avg ₹$formattedAvg, +₹$formattedDiff)."

            insights.add(
                DetectedInsight(
                    id = "SPIKE:${catUid}:${currentYm}",
                    type = InsightType.CATEGORY_SPIKE,
                    severity = severity,
                    title = "$catName Spending Surge",
                    summary = summary,
                    targetKey = catUid,
                    categoryUid = catUid,
                    amount = currentTotal,
                    historicalAverage = previousAvg,
                    percentChange = pctChange,
                    confidence = 1.0,
                    transactionUids = catTxs.map { it.uid }
                )
            )
        }

        return insights
    }
}
