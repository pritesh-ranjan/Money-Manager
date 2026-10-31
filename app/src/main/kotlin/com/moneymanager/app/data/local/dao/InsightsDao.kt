package com.moneymanager.app.data.local.dao

import androidx.room.*
import com.moneymanager.app.data.local.entity.InsightEntity
import com.moneymanager.app.data.local.entity.InsightTransactionCrossRef
import com.moneymanager.app.data.local.entity.InsightWithTransactions
import com.moneymanager.app.data.local.entity.RecurrenceDecisionEntity
import com.moneymanager.app.data.local.entity.TagEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionTagCrossRef
import kotlinx.coroutines.flow.Flow

data class AutoTagInstruction(
    val tagName: String,
    val transactionUids: List<String>,
    val isRemoval: Boolean = false
)

data class ReconciliationPlan(
    val toUpsert: List<InsightEntity>,
    val toResolveIds: List<String>,
    val crossRefs: List<InsightTransactionCrossRef>,
    val autoTags: List<AutoTagInstruction> = emptyList()
)

@Dao
interface InsightsDao {

    @Transaction
    @Query("SELECT * FROM insights WHERE status = 'ACTIVE' ORDER BY severity DESC, lastEvaluatedAt DESC")
    fun observeActiveInsights(): Flow<List<InsightWithTransactions>>

    @Transaction
    @Query("SELECT * FROM insights WHERE status = 'ACTIVE' ORDER BY severity DESC, lastEvaluatedAt DESC")
    suspend fun getAllActiveInsightsSync(): List<InsightWithTransactions>

    @Query("SELECT * FROM insights")
    suspend fun getAllInsightsRaw(): List<InsightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInsight(insight: InsightEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInsights(insights: List<InsightEntity>)

    @Query("UPDATE insights SET status = 'RESOLVED', lastEvaluatedAt = :resolvedAt WHERE id IN (:ids)")
    suspend fun markResolved(ids: List<String>, resolvedAt: Long)

    @Query("DELETE FROM insights WHERE status = 'RESOLVED' AND lastEvaluatedAt < :thresholdMillis")
    suspend fun deleteResolvedOlderThan(thresholdMillis: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInsightTransactionCrossRefs(refs: List<InsightTransactionCrossRef>)

    @Query("DELETE FROM insight_transactions WHERE insightId = :insightId")
    suspend fun deleteCrossRefsForInsight(insightId: String)

    @Query("SELECT * FROM recurrence_decisions")
    suspend fun getRecurrenceDecisions(): List<RecurrenceDecisionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRecurrenceDecision(decision: RecurrenceDecisionEntity)

    @Query("DELETE FROM recurrence_decisions WHERE targetKey = :targetKey")
    suspend fun deleteRecurrenceDecision(targetKey: String)

    @Query("DELETE FROM insight_transactions")
    suspend fun clearInsightTransactionCrossRefs()

    @Query("DELETE FROM insights")
    suspend fun clearAllInsights()

    // --- Tag Operations for Auto-Tagging (Approach A) ---
    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactionTagCrossRefs(crossRefs: List<TransactionTagCrossRef>)

    @Query("DELETE FROM transaction_tags WHERE tagUid = :tagUid AND transactionUid IN (:transactionUids)")
    suspend fun removeTagFromTransactions(tagUid: String, transactionUids: List<String>)

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND timestamp >= :fromTime ORDER BY timestamp DESC")
    suspend fun getTransactionsSince(fromTime: Long): List<TransactionEntity>

    @Transaction
    suspend fun applyReconciliation(plan: ReconciliationPlan) {
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
                        uid = java.util.UUID.randomUUID().toString(),
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
