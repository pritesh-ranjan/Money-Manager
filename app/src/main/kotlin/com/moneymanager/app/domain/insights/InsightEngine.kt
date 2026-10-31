package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.local.dao.AutoTagInstruction
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.RecurrenceDecision
import com.moneymanager.app.data.local.entity.TransactionEntity
import java.time.LocalDate
import java.time.ZoneId

data class EngineEvaluationResult(
    val detectedInsights: List<DetectedInsight>,
    val autoTagInstructions: List<AutoTagInstruction>
)

object InsightEngine {

    fun evaluate(
        allTransactions: List<TransactionEntity>,
        accountsMap: Map<String, AccountEntity>,
        categoriesMap: Map<String, CategoryEntity>,
        decisionsMap: Map<String, RecurrenceDecision>,
        referenceDate: LocalDate = LocalDate.now(),
        config: DetectorConfig = DetectorConfig(),
        zone: ZoneId = ZoneId.systemDefault()
    ): EngineEvaluationResult {
        val sanitizedConfig = config.sanitized()

        // 1. Establish baseline & detection time boundaries
        val nowMillis = referenceDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val detectionWindowMillis = sanitizedConfig.detectionWindowDays * 24 * 60 * 60 * 1000L
        val evaluationStartMillis = nowMillis - detectionWindowMillis

        val (baselineStartMillis, baselineEndMillis) = BaselineCalculator.calculateBaselineWindow(
            evaluationStartMillis = evaluationStartMillis,
            lookbackMonths = sanitizedConfig.lookbackMonths,
            zone = zone
        )

        // 2. Build Segmented Baselines
        val categoryBaselines = BaselineCalculator.buildCategoryBaselines(
            transactions = allTransactions,
            categoriesMap = categoriesMap,
            baselineStartMillis = baselineStartMillis,
            baselineEndMillis = baselineEndMillis,
            config = sanitizedConfig,
            zone = zone
        )

        val merchantBaselines = BaselineCalculator.buildMerchantBaselines(
            transactions = allTransactions,
            baselineStartMillis = baselineStartMillis,
            baselineEndMillis = baselineEndMillis,
            config = sanitizedConfig,
            zone = zone
        )

        // Recent candidate transactions in the detection window
        val recentTxs = allTransactions.filter {
            !it.isDeleted && it.timestamp >= evaluationStartMillis
        }

        val allDetected = mutableListOf<DetectedInsight>()

        // 3. Run Anomaly Detectors
        // A. Large Transactions (MAD Robust Z-score)
        val largeTxInsights = LargeTransactionDetector.detect(
            transactions = recentTxs,
            merchantBaselines = merchantBaselines,
            categoryBaselines = categoryBaselines,
            categoriesMap = categoriesMap,
            config = sanitizedConfig
        )
        allDetected.addAll(largeTxInsights)

        // B. Category Spending Spikes (% Change vs Baseline)
        val spikeInsights = CategorySpikeDetector.detect(
            allTransactions = allTransactions,
            categoriesMap = categoriesMap,
            referenceDate = referenceDate,
            config = sanitizedConfig,
            zone = zone
        )
        allDetected.addAll(spikeInsights)

        // C. Duplicate Transactions
        val duplicateInsights = DuplicateDetector.detect(
            transactions = recentTxs,
            config = sanitizedConfig,
            zone = zone
        )
        allDetected.addAll(duplicateInsights)

        // D. Recurring Payments (Subscriptions & Transfers)
        val recurringInsights = RecurringPaymentDetector.detect(
            transactions = allTransactions,
            accountsMap = accountsMap,
            categoriesMap = categoriesMap,
            decisionsMap = decisionsMap,
            referenceDate = referenceDate,
            config = sanitizedConfig,
            zone = zone
        )
        allDetected.addAll(recurringInsights)

        // 4. Approach A Auto-Tagging
        val autoTagInstructions = AutoTaggingEngine.evaluate(
            detectedInsights = recurringInsights,
            allTransactions = allTransactions,
            decisionsMap = decisionsMap,
            config = sanitizedConfig
        )

        return EngineEvaluationResult(
            detectedInsights = allDetected,
            autoTagInstructions = autoTagInstructions
        )
    }
}
