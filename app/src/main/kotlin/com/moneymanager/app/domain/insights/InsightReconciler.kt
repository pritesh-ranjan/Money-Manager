package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.local.dao.InsightsDao
import com.moneymanager.app.data.local.dao.MoneyManagerDao
import com.moneymanager.app.data.local.dao.ReconciliationPlan
import com.moneymanager.app.data.local.entity.InsightEntity
import com.moneymanager.app.data.local.entity.InsightStatus
import com.moneymanager.app.data.local.entity.InsightTransactionCrossRef
import java.time.LocalDate
import java.time.ZoneId

data class ReconciliationResult(
    val activeCount: Int,
    val resolvedCount: Int,
    val autoTaggedCount: Int
)

class InsightReconciler(
    private val insightsDao: InsightsDao,
    private val moneyManagerDao: MoneyManagerDao,
    private val zone: ZoneId = ZoneId.systemDefault()
) {

    suspend fun reconcile(
        config: DetectorConfig = DetectorConfig(),
        referenceDate: LocalDate = LocalDate.now(),
        currentTimeMillis: Long = System.currentTimeMillis()
    ): ReconciliationResult {
        val sanitizedConfig = config.sanitized()

        // 1. Fetch raw domain data
        val rawTxs = moneyManagerDao.getAllRawTransactions()
        val accounts = moneyManagerDao.getAllAccountsRaw().associateBy { it.uid }
        val categories = moneyManagerDao.getAllCategoriesRaw().associateBy { it.uid }
        val decisions = insightsDao.getRecurrenceDecisions().associate { it.targetKey to it.decision }
        val existingActive = insightsDao.getAllActiveInsightsSync().associateBy { it.insight.id }

        // 2. Evaluate Engine
        val evalResult = InsightEngine.evaluate(
            allTransactions = rawTxs,
            accountsMap = accounts,
            categoriesMap = categories,
            decisionsMap = decisions,
            referenceDate = referenceDate,
            config = sanitizedConfig,
            zone = zone
        )

        val detectedMap = evalResult.detectedInsights.associateBy { it.id }

        // 3. Diffing Active vs Detected
        val toUpsert = mutableListOf<InsightEntity>()
        val crossRefs = mutableListOf<InsightTransactionCrossRef>()

        for ((id, detected) in detectedMap) {
            val existing = existingActive[id]?.insight
            val firstDetected = existing?.firstDetectedAt ?: currentTimeMillis

            val entity = InsightEntity(
                id = id,
                type = detected.type,
                severity = detected.severity,
                status = InsightStatus.ACTIVE,
                title = detected.title,
                summary = detected.summary,
                targetKey = detected.targetKey,
                categoryUid = detected.categoryUid,
                accountUid = detected.accountUid,
                toAccountUid = detected.toAccountUid,
                transferKind = detected.transferKind,
                amount = detected.amount,
                historicalAverage = detected.historicalAverage,
                historicalMedian = detected.historicalMedian,
                percentChange = detected.percentChange,
                robustZ = detected.robustZ,
                confidence = detected.confidence,
                confidenceBreakdown = detected.confidenceBreakdown,
                frequency = detected.frequency,
                expectedNextDate = detected.expectedNextDate,
                autoAssignedTag = detected.autoAssignedTag,
                firstDetectedAt = firstDetected,
                lastEvaluatedAt = currentTimeMillis
            )
            toUpsert.add(entity)

            for (txUid in detected.transactionUids) {
                crossRefs.add(InsightTransactionCrossRef(insightId = id, transactionUid = txUid))
            }
        }

        // Active insights that are no longer detected are marked RESOLVED
        val toResolveIds = existingActive.keys.filter { it !in detectedMap }

        // 4. Apply Atomic Plan
        val plan = ReconciliationPlan(
            toUpsert = toUpsert,
            toResolveIds = toResolveIds,
            crossRefs = crossRefs,
            autoTags = evalResult.autoTagInstructions
        )
        insightsDao.applyReconciliation(plan)

        // 5. Purge old resolved insights older than 30 days
        val thirtyDaysAgo = currentTimeMillis - (30 * 24 * 60 * 60 * 1000L)
        insightsDao.deleteResolvedOlderThan(thirtyDaysAgo)

        return ReconciliationResult(
            activeCount = toUpsert.size,
            resolvedCount = toResolveIds.size,
            autoTaggedCount = evalResult.autoTagInstructions.size
        )
    }
}
