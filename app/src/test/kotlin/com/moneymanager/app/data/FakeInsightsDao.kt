package com.moneymanager.app.data

import com.moneymanager.app.data.local.dao.AutoTagInstruction
import com.moneymanager.app.data.local.dao.InsightsDao
import com.moneymanager.app.data.local.dao.ReconciliationPlan
import com.moneymanager.app.data.local.entity.InsightEntity
import com.moneymanager.app.data.local.entity.InsightStatus
import com.moneymanager.app.data.local.entity.InsightTransactionCrossRef
import com.moneymanager.app.data.local.entity.InsightWithTransactions
import com.moneymanager.app.data.local.entity.RecurrenceDecisionEntity
import com.moneymanager.app.data.local.entity.TagEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionTagCrossRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeInsightsDao : InsightsDao {

    val insightsMap = mutableMapOf<String, InsightEntity>()
    val crossRefsList = mutableListOf<InsightTransactionCrossRef>()
    val decisionsMap = mutableMapOf<String, RecurrenceDecisionEntity>()
    val tagsMap = mutableMapOf<String, TagEntity>()
    val transactionTagsList = mutableListOf<TransactionTagCrossRef>()

    private val _insightsFlow = MutableStateFlow<List<InsightEntity>>(emptyList())

    private fun emitFlow() {
        _insightsFlow.value = insightsMap.values.toList()
    }

    override fun observeActiveInsights(): Flow<List<InsightWithTransactions>> {
        return _insightsFlow.map { list ->
            list.filter { it.status == InsightStatus.ACTIVE }.map { insight ->
                val linkedTxUids = crossRefsList.filter { it.insightId == insight.id }.map { it.transactionUid }.toSet()
                InsightWithTransactions(
                    insight = insight,
                    transactions = linkedTxUids.map { uid ->
                        TransactionEntity(
                            uid = uid,
                            type = com.moneymanager.app.data.local.entity.TransactionType.EXPENSE,
                            accountAmount = insight.amount ?: 0.0,
                            baseAmount = insight.amount ?: 0.0,
                            currencyUid = "INR",
                            accountUid = insight.accountUid ?: "ACC_1"
                        )
                    }
                )
            }
        }
    }

    override suspend fun getAllActiveInsightsSync(): List<InsightWithTransactions> {
        return insightsMap.values
            .filter { it.status == InsightStatus.ACTIVE }
            .map { insight ->
                InsightWithTransactions(insight = insight, transactions = emptyList())
            }
    }

    override suspend fun getAllInsightsRaw(): List<InsightEntity> {
        return insightsMap.values.toList()
    }

    override suspend fun upsertInsight(insight: InsightEntity) {
        insightsMap[insight.id] = insight
        emitFlow()
    }

    override suspend fun upsertInsights(insights: List<InsightEntity>) {
        for (i in insights) {
            insightsMap[i.id] = i
        }
        emitFlow()
    }

    override suspend fun markResolved(ids: List<String>, resolvedAt: Long) {
        for (id in ids) {
            insightsMap[id]?.let {
                insightsMap[id] = it.copy(status = InsightStatus.RESOLVED, lastEvaluatedAt = resolvedAt)
            }
        }
        emitFlow()
    }

    override suspend fun deleteResolvedOlderThan(thresholdMillis: Long) {
        insightsMap.entries.removeIf {
            it.value.status == InsightStatus.RESOLVED && it.value.lastEvaluatedAt < thresholdMillis
        }
        emitFlow()
    }

    override suspend fun insertInsightTransactionCrossRefs(refs: List<InsightTransactionCrossRef>) {
        crossRefsList.addAll(refs)
    }

    override suspend fun deleteCrossRefsForInsight(insightId: String) {
        crossRefsList.removeIf { it.insightId == insightId }
    }

    override suspend fun getRecurrenceDecisions(): List<RecurrenceDecisionEntity> {
        return decisionsMap.values.toList()
    }

    override suspend fun saveRecurrenceDecision(decision: RecurrenceDecisionEntity) {
        decisionsMap[decision.targetKey] = decision
    }

    override suspend fun deleteRecurrenceDecision(targetKey: String) {
        decisionsMap.remove(targetKey)
    }

    override suspend fun clearInsightTransactionCrossRefs() {
        crossRefsList.clear()
    }

    override suspend fun clearAllInsights() {
        insightsMap.clear()
        emitFlow()
    }

    override suspend fun getTagByName(name: String): TagEntity? {
        return tagsMap.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    override suspend fun insertTag(tag: TagEntity): Long {
        tagsMap[tag.uid] = tag
        return 1L
    }

    override suspend fun insertTransactionTagCrossRefs(crossRefs: List<TransactionTagCrossRef>) {
        transactionTagsList.addAll(crossRefs)
    }

    override suspend fun removeTagFromTransactions(tagUid: String, transactionUids: List<String>) {
        transactionTagsList.removeIf { it.tagUid == tagUid && it.transactionUid in transactionUids }
    }

    override suspend fun getTransactionsSince(fromTime: Long): List<TransactionEntity> {
        return emptyList()
    }

    override suspend fun applyReconciliation(plan: ReconciliationPlan) {
        if (plan.toUpsert.isNotEmpty()) {
            upsertInsights(plan.toUpsert)
        }
        if (plan.toResolveIds.isNotEmpty()) {
            markResolved(plan.toResolveIds, System.currentTimeMillis())
        }
        for (insight in plan.toUpsert) {
            deleteCrossRefsForInsight(insight.id)
        }
        if (plan.crossRefs.isNotEmpty()) {
            insertInsightTransactionCrossRefs(plan.crossRefs)
        }
        for (tagAction in plan.autoTags) {
            if (tagAction.transactionUids.isEmpty()) continue
            if (tagAction.isRemoval) {
                val existingTag = getTagByName(tagAction.tagName)
                if (existingTag != null) {
                    removeTagFromTransactions(existingTag.uid, tagAction.transactionUids)
                }
            } else {
                var tag = getTagByName(tagAction.tagName)
                if (tag == null) {
                    val newTag = TagEntity(
                        uid = UUID.randomUUID().toString(),
                        name = tagAction.tagName
                    )
                    insertTag(newTag)
                    tag = getTagByName(tagAction.tagName) ?: newTag
                }
                val refs = tagAction.transactionUids.map { txUid ->
                    TransactionTagCrossRef(transactionUid = txUid, tagUid = tag.uid)
                }
                insertTransactionTagCrossRefs(refs)
            }
        }
    }
}
