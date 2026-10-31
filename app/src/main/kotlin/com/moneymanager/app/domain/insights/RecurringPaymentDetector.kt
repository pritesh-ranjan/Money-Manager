package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.InsightSeverity
import com.moneymanager.app.data.local.entity.InsightType
import com.moneymanager.app.data.local.entity.RecurrenceDecision
import com.moneymanager.app.data.local.entity.RecurrenceFrequency
import com.moneymanager.app.data.local.entity.RecurrentTransferKind
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.domain.CurrencyFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

object RecurringPaymentDetector {

    fun detect(
        transactions: List<TransactionEntity>,
        accountsMap: Map<String, AccountEntity>,
        categoriesMap: Map<String, CategoryEntity>,
        decisionsMap: Map<String, RecurrenceDecision>,
        referenceDate: LocalDate = LocalDate.now(),
        config: DetectorConfig,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<DetectedInsight> {
        val insights = mutableListOf<DetectedInsight>()

        // 1. Detect recurring expenses (Subscriptions & Bills)
        insights.addAll(
            detectRecurringExpenses(transactions, decisionsMap, referenceDate, config, zone)
        )

        // 2. Detect recurring transfers (SIPs & Recurring Deposits)
        insights.addAll(
            detectRecurringTransfers(transactions, accountsMap, decisionsMap, referenceDate, config, zone)
        )

        return insights
    }

    // --- Recurrent Expenses (Subscriptions & Bills) ---

    private fun detectRecurringExpenses(
        transactions: List<TransactionEntity>,
        decisionsMap: Map<String, RecurrenceDecision>,
        referenceDate: LocalDate,
        config: DetectorConfig,
        zone: ZoneId
    ): List<DetectedInsight> {
        val expenseTxs = transactions.filter {
            !it.isDeleted && it.type == TransactionType.EXPENSE
        }

        val rawMerchants = expenseTxs.map { it.note.ifBlank { it.description } }
            .filter { it.isNotBlank() }
            .toSet()

        val merchantClusters = MerchantNormalizer.clusterMerchants(rawMerchants, config.duplicateMerchantSimilarity)

        val groupedByCluster = expenseTxs.groupBy {
            val raw = it.note.ifBlank { it.description }
            if (raw.isBlank()) "" else (merchantClusters[raw] ?: MerchantNormalizer.normalize(raw))
        }.filterKeys { it.isNotBlank() }

        val insights = mutableListOf<DetectedInsight>()

        for ((canonicalKey, clusterTxs) in groupedByCluster) {
            // Check if user previously rejected this recurrence
            if (decisionsMap[canonicalKey] == RecurrenceDecision.REJECTED) continue

            val sortedTxs = clusterTxs.sortedBy { it.timestamp }
            if (sortedTxs.size < 2) continue

            val analysis = analyzeSeries(sortedTxs, canonicalKey, config, zone) ?: continue
            if (analysis.confidence < config.recurrenceMinConfidence) continue

            val latestTx = sortedTxs.last()
            val priorTxs = sortedTxs.dropLast(1)
            val priorMedian = if (priorTxs.isNotEmpty()) RobustStatistics.median(priorTxs.map { it.baseAmount }) else null

            // Determine subtype
            val isPriceIncrease = priorMedian != null &&
                    latestTx.baseAmount > priorMedian * (1.0 + (config.recurrenceAmountVariationPercent / 100.0))

            val isForgotten = !isPriceIncrease &&
                    sortedTxs.size >= config.forgottenMinCycles &&
                    decisionsMap[canonicalKey] != RecurrenceDecision.CONFIRMED &&
                    isOnlySubscriptionActivity(clusterTxs, canonicalKey)

            val (type, severity, title) = when {
                isPriceIncrease -> Triple(
                    InsightType.PRICE_INCREASE,
                    InsightSeverity.WARNING,
                    "Price Increase: ${analysis.displayName}"
                )
                isForgotten -> Triple(
                    InsightType.FORGOTTEN_SUBSCRIPTION,
                    InsightSeverity.WARNING,
                    "Potentially Forgotten: ${analysis.displayName}"
                )
                else -> Triple(
                    InsightType.SUBSCRIPTION,
                    InsightSeverity.INFO,
                    "Recurring Subscription: ${analysis.displayName}"
                )
            }

            val formattedAmt = CurrencyFormatter.formatIndianNumber(latestTx.baseAmount, decimals = 0)
            val freqLabel = analysis.frequency.displayName

            val summary = when (type) {
                InsightType.PRICE_INCREASE -> {
                    val formattedPrior = priorMedian?.let { CurrencyFormatter.formatIndianNumber(it, decimals = 0) } ?: "earlier"
                    "${analysis.displayName} was charged at ₹$formattedAmt, up from typical ₹$formattedPrior ($freqLabel)."
                }
                InsightType.FORGOTTEN_SUBSCRIPTION -> {
                    "${analysis.displayName} charges ₹$formattedAmt $freqLabel automatically, with no active transactions logged."
                }
                else -> {
                    "${analysis.displayName} charges ₹$formattedAmt $freqLabel (${String.format("%.0f", analysis.confidence * 100)}% confidence)."
                }
            }

            insights.add(
                DetectedInsight(
                    id = "SUB:$canonicalKey",
                    type = type,
                    severity = severity,
                    title = title,
                    summary = summary,
                    targetKey = canonicalKey,
                    categoryUid = latestTx.categoryUid,
                    accountUid = latestTx.accountUid,
                    amount = latestTx.baseAmount,
                    historicalAverage = analysis.meanAmount,
                    historicalMedian = analysis.medianAmount,
                    confidence = analysis.confidence,
                    confidenceBreakdown = analysis.confidenceBreakdown,
                    frequency = analysis.frequency,
                    expectedNextDate = analysis.expectedNextDateMillis,
                    autoAssignedTag = "Subscription",
                    transactionUids = sortedTxs.map { it.uid }
                )
            )
        }

        return insights
    }

    // --- Recurrent Transfers (SIP Investments & Recurring Deposits) ---

    private fun detectRecurringTransfers(
        transactions: List<TransactionEntity>,
        accountsMap: Map<String, AccountEntity>,
        decisionsMap: Map<String, RecurrenceDecision>,
        referenceDate: LocalDate,
        config: DetectorConfig,
        zone: ZoneId
    ): List<DetectedInsight> {
        val transferTxs = transactions.filter {
            !it.isDeleted &&
            it.type == TransactionType.TRANSFER &&
            it.toAccountUid != null
        }

        // Group by destination account + normalized transfer note key
        val groupedTransfers = transferTxs.groupBy {
            val noteKey = MerchantNormalizer.normalize(it.note.ifBlank { it.description })
            "${it.toAccountUid}:$noteKey"
        }

        val insights = mutableListOf<DetectedInsight>()

        for ((targetKey, groupTxs) in groupedTransfers) {
            if (decisionsMap[targetKey] == RecurrenceDecision.REJECTED) continue

            val sortedTxs = groupTxs.sortedBy { it.timestamp }
            if (sortedTxs.size < 2) continue

            val toAccountUid = sortedTxs.first().toAccountUid ?: continue
            val destAccount = accountsMap[toAccountUid]
            val destName = destAccount?.name ?: "Investment Account"
            val rawNote = sortedTxs.first().note.ifBlank { destName }

            val analysis = analyzeSeries(sortedTxs, rawNote, config, zone) ?: continue
            if (analysis.confidence < config.recurrenceMinConfidence) continue

            val latestTx = sortedTxs.last()
            val destType = destAccount?.accountType
            val noteLower = rawNote.lowercase()

            val (transferKind, suggestedTag, title) = when {
                destType == AccountType.MUTUAL_FUNDS || destType == AccountType.EQUITY || noteLower.contains("sip") ->
                    Triple(RecurrentTransferKind.SIP_INVESTMENT, "SIP", "Recurring SIP: $destName")
                destType == AccountType.INVESTMENT || noteLower.contains("rd") || noteLower.contains("recurring deposit") ->
                    Triple(RecurrentTransferKind.RECURRING_DEPOSIT, "Recurring Deposit", "Recurring Deposit: $destName")
                destType == AccountType.LOAN || noteLower.contains("emi") ->
                    Triple(RecurrentTransferKind.LOAN_EMI, "EMI", "Recurring Loan EMI: $destName")
                else ->
                    Triple(RecurrentTransferKind.REGULAR_TRANSFER, "Recurring Transfer", "Recurring Transfer: $destName")
            }

            val formattedAmt = CurrencyFormatter.formatIndianNumber(latestTx.baseAmount, decimals = 0)
            val freqLabel = analysis.frequency.displayName
            val fromAccountName = accountsMap[latestTx.accountUid]?.name ?: "Account"

            val summary = "$fromAccountName → $destName: ₹$formattedAmt $freqLabel (${String.format("%.0f", analysis.confidence * 100)}% confidence)."

            insights.add(
                DetectedInsight(
                    id = "TXF:$targetKey",
                    type = InsightType.RECURRING_TRANSFER,
                    severity = InsightSeverity.INFO,
                    title = title,
                    summary = summary,
                    targetKey = targetKey,
                    accountUid = latestTx.accountUid,
                    toAccountUid = toAccountUid,
                    transferKind = transferKind,
                    amount = latestTx.baseAmount,
                    historicalAverage = analysis.meanAmount,
                    historicalMedian = analysis.medianAmount,
                    confidence = analysis.confidence,
                    confidenceBreakdown = analysis.confidenceBreakdown,
                    frequency = analysis.frequency,
                    expectedNextDate = analysis.expectedNextDateMillis,
                    autoAssignedTag = suggestedTag,
                    transactionUids = sortedTxs.map { it.uid }
                )
            )
        }

        return insights
    }

    // --- Core Series & Confidence Analyzer ---

    private data class SeriesAnalysis(
        val displayName: String,
        val frequency: RecurrenceFrequency,
        val confidence: Double,
        val confidenceBreakdown: String,
        val medianAmount: Double,
        val meanAmount: Double,
        val expectedNextDateMillis: Long
    )

    private fun analyzeSeries(
        sortedTxs: List<TransactionEntity>,
        displayName: String,
        config: DetectorConfig,
        zone: ZoneId
    ): SeriesAnalysis? {
        val n = sortedTxs.size
        if (n < 2) return null

        val dates = sortedTxs.map {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }

        val intervalsInDays = mutableListOf<Double>()
        for (i in 1 until dates.size) {
            val days = ChronoUnit.DAYS.between(dates[i - 1], dates[i]).toDouble()
            intervalsInDays.add(days)
        }

        val medianInterval = RobustStatistics.median(intervalsInDays) ?: return null

        // Pick closest frequency candidate
        val freqCandidates = RecurrenceFrequency.values()
        val bestFreq = freqCandidates.minByOrNull { abs(it.nominalDays - medianInterval) } ?: return null

        // Min occurrences check
        val minRequired = when (bestFreq) {
            RecurrenceFrequency.WEEKLY -> 4
            RecurrenceFrequency.FORTNIGHTLY -> 3
            RecurrenceFrequency.MONTHLY -> 3
            RecurrenceFrequency.QUARTERLY -> 3
            RecurrenceFrequency.YEARLY -> 2
        }
        if (n < minRequired) return null

        // 1. Interval Consistency Score: S_interval
        val matchingIntervals = intervalsInDays.count { intervalDays ->
            isIntervalMatchingFrequency(intervalDays, bestFreq)
        }
        val sInterval = matchingIntervals.toDouble() / intervalsInDays.size.toDouble()

        // 2. Amount Consistency Score: S_amount
        val amounts = sortedTxs.map { it.baseAmount }
        val medianAmt = RobustStatistics.median(amounts) ?: return null
        val meanAmt = amounts.average()
        val varTolerance = config.recurrenceAmountVariationPercent / 100.0

        val matchingAmounts = amounts.count { amt ->
            abs(amt - medianAmt) <= (medianAmt * varTolerance) + 0.01
        }
        val fractionMatchingAmount = matchingAmounts.toDouble() / amounts.size.toDouble()

        val variance = amounts.map { (it - meanAmt) * (it - meanAmt) }.average()
        val stdDev = sqrt(variance)
        val cv = if (meanAmt > 0) stdDev / meanAmt else 1.0
        val sAmount = fractionMatchingAmount * (1.0 - min(cv, 1.0))

        // 3. Target / Merchant Name Consistency Score: S_target
        val rawNames = sortedTxs.map { it.note.ifBlank { it.description } }
        val sTarget = if (rawNames.all { it.isNotBlank() }) {
            rawNames.map { MerchantNormalizer.similarity(it, displayName) }.average()
        } else {
            0.80
        }

        // 4. History Depth Score: S_history
        val targetHistoryCount = when (bestFreq) {
            RecurrenceFrequency.WEEKLY -> 8
            RecurrenceFrequency.FORTNIGHTLY, RecurrenceFrequency.MONTHLY -> 6
            RecurrenceFrequency.QUARTERLY -> 4
            RecurrenceFrequency.YEARLY -> 3
        }
        val sHistory = min(1.0, n.toDouble() / targetHistoryCount.toDouble())

        // Weighted explainable confidence
        val w = config.weights
        val totalConfidence = (w.intervalWeight * sInterval) +
                (w.amountWeight * sAmount) +
                (w.targetWeight * sTarget) +
                (w.historyWeight * sHistory)

        val breakdown = "interval=${String.format("%.2f", sInterval)};amount=${String.format("%.2f", sAmount)};target=${String.format("%.2f", sTarget)};history=${String.format("%.2f", sHistory)}"

        // Compute expected next date
        val lastDate = dates.last()
        val nextDate = when (bestFreq) {
            RecurrenceFrequency.WEEKLY -> lastDate.plusWeeks(1)
            RecurrenceFrequency.FORTNIGHTLY -> lastDate.plusWeeks(2)
            RecurrenceFrequency.MONTHLY -> lastDate.plusMonths(1)
            RecurrenceFrequency.QUARTERLY -> lastDate.plusMonths(3)
            RecurrenceFrequency.YEARLY -> lastDate.plusYears(1)
        }
        val nextDateMillis = nextDate.atStartOfDay(zone).toInstant().toEpochMilli()

        return SeriesAnalysis(
            displayName = displayName.replaceFirstChar { it.uppercase() },
            frequency = bestFreq,
            confidence = totalConfidence.coerceIn(0.0, 1.0),
            confidenceBreakdown = breakdown,
            medianAmount = medianAmt,
            meanAmount = meanAmt,
            expectedNextDateMillis = nextDateMillis
        )
    }

    private fun isIntervalMatchingFrequency(days: Double, freq: RecurrenceFrequency): Boolean {
        return when (freq) {
            RecurrenceFrequency.WEEKLY -> days in 6.0..8.0
            RecurrenceFrequency.FORTNIGHTLY -> days in 12.0..16.0
            RecurrenceFrequency.MONTHLY -> days in 26.0..35.0 // Handles 28-day February to 31-day months
            RecurrenceFrequency.QUARTERLY -> days in 84.0..98.0
            RecurrenceFrequency.YEARLY -> days in 350.0..380.0
        }
    }

    private fun isOnlySubscriptionActivity(clusterTxs: List<TransactionEntity>, canonicalKey: String): Boolean {
        // If all transactions have identical notes or amounts, it is purely an automated debited subscription
        val distinctAmounts = clusterTxs.map { it.baseAmount }.distinct()
        return distinctAmounts.size <= 2
    }
}
