package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class SpendingBaseline(
    val segmentKey: String,
    val sampleCount: Int,
    val monthsCovered: Int,
    val median: Double?,
    val mad: Double?,
    val mean: Double,
    val monthlyAverage: Double,
    val monthlyTxnFrequency: Double,
    val amounts: List<Double>,
    val hasSufficientHistory: Boolean
)

object BaselineCalculator {

    /**
     * Resolves the root category UID for a given category UID, rolling up subcategories.
     */
    fun resolveRootCategoryUid(categoryUid: String?, categoriesMap: Map<String, CategoryEntity>): String? {
        if (categoryUid == null) return null
        val cat = categoriesMap[categoryUid] ?: return categoryUid
        return cat.parentUid ?: cat.uid
    }

    /**
     * Computes the rolling baseline window [startMillis, endMillis) for a given reference date and lookback months.
     * The evaluation window (last [detectionDays]) is strictly excluded.
     */
    fun calculateBaselineWindow(
        evaluationStartMillis: Long,
        lookbackMonths: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Pair<Long, Long> {
        val evalStartDate = Instant.ofEpochMilli(evaluationStartMillis).atZone(zone).toLocalDate()
        val baselineStartDate = evalStartDate.minusMonths(lookbackMonths.toLong())
        val startMillis = baselineStartDate.atStartOfDay(zone).toInstant().toEpochMilli()
        return Pair(startMillis, evaluationStartMillis)
    }

    /**
     * Builds category-segmented baselines from transactions within the baseline window.
     */
    fun buildCategoryBaselines(
        transactions: List<TransactionEntity>,
        categoriesMap: Map<String, CategoryEntity>,
        baselineStartMillis: Long,
        baselineEndMillis: Long,
        config: DetectorConfig,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<String, SpendingBaseline> {
        val baselineTxs = transactions.filter {
            !it.isDeleted &&
            it.type == TransactionType.EXPENSE &&
            it.timestamp in baselineStartMillis until baselineEndMillis
        }

        val grouped = baselineTxs.groupBy {
            resolveRootCategoryUid(it.categoryUid, categoriesMap) ?: "uncategorized"
        }

        return grouped.mapValues { (catKey, txs) ->
            computeBaseline(catKey, txs, baselineStartMillis, baselineEndMillis, config, zone)
        }
    }

    /**
     * Builds merchant-segmented baselines from transactions within the baseline window.
     */
    fun buildMerchantBaselines(
        transactions: List<TransactionEntity>,
        baselineStartMillis: Long,
        baselineEndMillis: Long,
        config: DetectorConfig,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<String, SpendingBaseline> {
        val baselineTxs = transactions.filter {
            !it.isDeleted &&
            it.type == TransactionType.EXPENSE &&
            it.timestamp in baselineStartMillis until baselineEndMillis
        }

        val grouped = baselineTxs.groupBy {
            val raw = it.note.ifBlank { it.description }
            MerchantNormalizer.normalize(raw)
        }.filterKeys { it.isNotBlank() }

        return grouped.mapValues { (merchantKey, txs) ->
            computeBaseline(merchantKey, txs, baselineStartMillis, baselineEndMillis, config, zone)
        }
    }

    private fun computeBaseline(
        segmentKey: String,
        txs: List<TransactionEntity>,
        startMillis: Long,
        endMillis: Long,
        config: DetectorConfig,
        zone: ZoneId
    ): SpendingBaseline {
        val amounts = txs.map { it.baseAmount }
        val count = amounts.size
        val med = RobustStatistics.median(amounts)
        val mad = RobustStatistics.mad(amounts)
        val mean = if (count > 0) amounts.average() else 0.0

        val startDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
        val monthsSpan = maxOf(1, ChronoUnit.MONTHS.between(startDate, endDate).toInt())

        val totalAmount = amounts.sum()
        val monthlyAvg = totalAmount / monthsSpan.toDouble()
        val monthlyFreq = count.toDouble() / monthsSpan.toDouble()

        // Count distinct calendar months in which transactions occurred
        val distinctMonths = txs.map {
            val date = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
            YearMonth.from(date)
        }.distinct().size

        val hasSufficientHistory = distinctMonths >= 3 && count >= config.minBaselineSamples

        return SpendingBaseline(
            segmentKey = segmentKey,
            sampleCount = count,
            monthsCovered = distinctMonths,
            median = med,
            mad = mad,
            mean = mean,
            monthlyAverage = monthlyAvg,
            monthlyTxnFrequency = monthlyFreq,
            amounts = amounts,
            hasSufficientHistory = hasSufficientHistory
        )
    }
}
