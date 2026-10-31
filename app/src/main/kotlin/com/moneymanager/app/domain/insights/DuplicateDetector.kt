package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.local.entity.InsightSeverity
import com.moneymanager.app.data.local.entity.InsightType
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.domain.CurrencyFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

object DuplicateDetector {

    fun detect(
        transactions: List<TransactionEntity>,
        config: DetectorConfig,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<DetectedInsight> {
        val insights = mutableListOf<DetectedInsight>()

        val candidateTxs = transactions.filter {
            !it.isDeleted && it.type != TransactionType.ADJUSTMENT
        }.sortedBy { it.timestamp }

        val n = candidateTxs.size
        if (n < 2) return emptyList()

        val parent = IntArray(n) { it }
        fun find(i: Int): Int {
            var root = i
            while (root != parent[root]) root = parent[root]
            var curr = i
            while (curr != root) {
                val next = parent[curr]
                parent[curr] = root
                curr = next
            }
            return root
        }
        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) parent[rootI] = rootJ
        }

        val exactWindowMillis = config.duplicateExactWindowMinutes * 60 * 1000L
        val oneDayMillis = 24 * 60 * 60 * 1000L

        for (i in 0 until n) {
            val txA = candidateTxs[i]
            val dateA = Instant.ofEpochMilli(txA.timestamp).atZone(zone).toLocalDate()

            for (j in (i + 1) until n) {
                val txB = candidateTxs[j]
                val timeDiff = abs(txB.timestamp - txA.timestamp)

                // Window constraint: within same calendar day
                if (timeDiff > oneDayMillis) break
                val dateB = Instant.ofEpochMilli(txB.timestamp).atZone(zone).toLocalDate()
                if (dateA != dateB && !config.duplicateSameDayEnabled) continue
                if (dateA != dateB && timeDiff > exactWindowMillis) continue

                // Account & Type consistency
                if (txA.type != txB.type) continue
                if (txA.accountUid != txB.accountUid) continue
                if (txA.toAccountUid != txB.toAccountUid) continue
                if (txA.currencyUid != txB.currencyUid) continue

                // Amount match
                val amountDiff = abs(txA.accountAmount - txB.accountAmount)
                if (amountDiff > config.amountTolerance) continue

                // Merchant / Payee similarity
                val noteA = txA.note.ifBlank { txA.description }
                val noteB = txB.note.ifBlank { txB.description }

                val merchantSim = when {
                    noteA.isNotBlank() && noteB.isNotBlank() -> MerchantNormalizer.similarity(noteA, noteB)
                    noteA.isBlank() && noteB.isBlank() -> {
                        if (txA.categoryUid != null && txA.categoryUid == txB.categoryUid) 0.90 else 0.50
                    }
                    else -> 0.40
                }

                if (merchantSim < config.duplicateMerchantSimilarity && noteA.isNotBlank() && noteB.isNotBlank()) continue

                // Time proximity score: 1.0 if within exact window, linearly decaying to 0.3 at 24 hours
                val timeScore = if (timeDiff <= exactWindowMillis) {
                    1.0
                } else {
                    max(0.3, 1.0 - (0.7 * (timeDiff.toDouble() / oneDayMillis.toDouble())))
                }

                val amountExactScore = if (amountDiff < 0.0001) 1.0 else 0.8
                val totalScore = 0.45 * merchantSim + 0.35 * timeScore + 0.20 * amountExactScore

                if (totalScore >= 0.75) {
                    union(i, j)
                }
            }
        }

        // Group into clusters
        val clusters = mutableMapOf<Int, MutableList<TransactionEntity>>()
        for (i in 0 until n) {
            val root = find(i)
            clusters.getOrPut(root) { mutableListOf() }.add(candidateTxs[i])
        }

        for ((_, clusterTxs) in clusters) {
            if (clusterTxs.size < 2) continue

            val sortedCluster = clusterTxs.sortedBy { it.timestamp }
            val first = sortedCluster.first()
            val second = sortedCluster[1]
            val timeGapMinutes = max(1L, (second.timestamp - first.timestamp) / (60 * 1000L))

            val isHighConfidence = timeGapMinutes <= config.duplicateExactWindowMinutes
            val severity = if (isHighConfidence) InsightSeverity.HIGH else InsightSeverity.WARNING

            val merchantName = first.note.ifBlank { "Transaction" }
            val formattedAmt = CurrencyFormatter.formatIndianNumber(first.baseAmount, decimals = 2)

            val summary = if (sortedCluster.size == 2) {
                "Found 2 identical charges of ₹$formattedAmt for '$merchantName' logged ${timeGapMinutes}m apart on the same day."
            } else {
                "Found ${sortedCluster.size} identical charges of ₹$formattedAmt for '$merchantName' logged on the same day."
            }

            val uids = sortedCluster.map { it.uid }
            val deterministicId = "DUP:" + uids.sorted().joinToString(":")

            insights.add(
                DetectedInsight(
                    id = deterministicId,
                    type = InsightType.DUPLICATE,
                    severity = severity,
                    title = "Possible Duplicate: $merchantName",
                    summary = summary,
                    targetKey = merchantName,
                    categoryUid = first.categoryUid,
                    accountUid = first.accountUid,
                    toAccountUid = first.toAccountUid,
                    amount = first.baseAmount,
                    confidence = if (isHighConfidence) 0.95 else 0.80,
                    transactionUids = uids
                )
            )
        }

        return insights
    }
}
