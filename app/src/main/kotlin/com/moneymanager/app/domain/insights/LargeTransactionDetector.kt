package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.InsightSeverity
import com.moneymanager.app.data.local.entity.InsightType
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.domain.CurrencyFormatter

object LargeTransactionDetector {

    fun detect(
        transactions: List<TransactionEntity>,
        merchantBaselines: Map<String, SpendingBaseline>,
        categoryBaselines: Map<String, SpendingBaseline>,
        categoriesMap: Map<String, CategoryEntity>,
        config: DetectorConfig
    ): List<DetectedInsight> {
        val insights = mutableListOf<DetectedInsight>()

        val candidateTxs = transactions.filter {
            !it.isDeleted &&
            it.type == TransactionType.EXPENSE &&
            it.baseAmount >= config.largeTxMinAmount
        }

        for (tx in candidateTxs) {
            val merchantKey = MerchantNormalizer.normalize(tx.note.ifBlank { tx.description })
            val rootCatUid = BaselineCalculator.resolveRootCategoryUid(tx.categoryUid, categoriesMap) ?: "uncategorized"

            // Baseline selection priority: merchant first, then category
            val merchantBaseline = merchantBaselines[merchantKey]
            val categoryBaseline = categoryBaselines[rootCatUid]

            val (baseline, segmentName, isMerchant) = when {
                merchantBaseline != null && merchantBaseline.hasSufficientHistory ->
                    Triple(merchantBaseline, tx.note.ifBlank { merchantKey }, true)
                categoryBaseline != null && categoryBaseline.hasSufficientHistory -> {
                    val catName = categoriesMap[rootCatUid]?.name ?: "Uncategorized"
                    Triple(categoryBaseline, catName, false)
                }
                else -> continue // Insufficient history: skip to avoid false positive
            }

            val med = baseline.median ?: continue
            if (tx.baseAmount <= med) continue // Must be an upward outlier

            val z = RobustStatistics.robustZ(tx.baseAmount, baseline.amounts) ?: continue
            if (z < config.robustZThreshold) continue

            val severity = if (z >= config.robustZThreshold * 2.0) {
                InsightSeverity.HIGH
            } else {
                InsightSeverity.WARNING
            }

            val pctChange = RobustStatistics.calculatePercentageChange(tx.baseAmount, med)
            val formattedAmt = CurrencyFormatter.formatIndianNumber(tx.baseAmount, decimals = 0)
            val formattedMed = CurrencyFormatter.formatIndianNumber(med, decimals = 0)
            val pctStr = pctChange?.let { "+${String.format("%.0f", it)}%" } ?: "significantly"

            val title = if (isMerchant) {
                "Unusual Spending at $segmentName"
            } else {
                "Unusually Large $segmentName Expense"
            }

            val summary = "₹$formattedAmt is $pctStr higher than your typical ₹$formattedMed spent on $segmentName (MAD Z-Score: ${String.format("%.1f", z)})."

            insights.add(
                DetectedInsight(
                    id = "ANOMALY:${tx.uid}",
                    type = InsightType.LARGE_TRANSACTION,
                    severity = severity,
                    title = title,
                    summary = summary,
                    targetKey = if (isMerchant) merchantKey else rootCatUid,
                    categoryUid = tx.categoryUid,
                    accountUid = tx.accountUid,
                    amount = tx.baseAmount,
                    historicalAverage = baseline.mean,
                    historicalMedian = med,
                    percentChange = pctChange,
                    robustZ = z,
                    confidence = 1.0,
                    transactionUids = listOf(tx.uid)
                )
            )
        }

        return insights
    }
}
