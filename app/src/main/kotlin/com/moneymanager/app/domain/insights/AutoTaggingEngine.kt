package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.local.dao.AutoTagInstruction
import com.moneymanager.app.data.local.entity.InsightType
import com.moneymanager.app.data.local.entity.RecurrenceDecision
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType

object AutoTaggingEngine {

    /**
     * Evaluates detected recurrences and user decisions to generate Approach A auto-tagging instructions.
     */
    fun evaluate(
        detectedInsights: List<DetectedInsight>,
        allTransactions: List<TransactionEntity> = emptyList(),
        decisionsMap: Map<String, RecurrenceDecision>,
        config: DetectorConfig
    ): List<AutoTagInstruction> {
        if (!config.autoTagEnabled) return emptyList()

        val instructions = mutableListOf<AutoTagInstruction>()

        val recurrenceTypes = setOf(
            InsightType.SUBSCRIPTION,
            InsightType.RECURRING_TRANSFER,
            InsightType.FORGOTTEN_SUBSCRIPTION,
            InsightType.PRICE_INCREASE
        )

        for (insight in detectedInsights) {
            if (insight.type !in recurrenceTypes) continue
            val tag = insight.autoAssignedTag ?: continue
            val targetKey = insight.targetKey ?: continue
            val uids = insight.transactionUids
            if (uids.isEmpty()) continue

            val decision = decisionsMap[targetKey]

            when {
                decision == RecurrenceDecision.REJECTED -> {
                    instructions.add(
                        AutoTagInstruction(
                            tagName = tag,
                            transactionUids = uids,
                            isRemoval = true
                        )
                    )
                }
                decision == RecurrenceDecision.CONFIRMED || (insight.confidence ?: 0.0) >= config.autoTagMinConfidence -> {
                    instructions.add(
                        AutoTagInstruction(
                            tagName = tag,
                            transactionUids = uids,
                            isRemoval = false
                        )
                    )
                }
            }
        }

        // Check rejected decisions directly against transactions to ensure suppressed series are untagged
        val rejectedKeys = decisionsMap.filterValues { it == RecurrenceDecision.REJECTED }.keys
        for (key in rejectedKeys) {
            val matchingExpenseUids = allTransactions.filter {
                !it.isDeleted &&
                it.type == TransactionType.EXPENSE &&
                MerchantNormalizer.normalize(it.note.ifBlank { it.description }) == key
            }.map { it.uid }

            if (matchingExpenseUids.isNotEmpty()) {
                instructions.add(AutoTagInstruction("Subscription", matchingExpenseUids, isRemoval = true))
            }

            val matchingTransferUids = allTransactions.filter {
                !it.isDeleted &&
                it.type == TransactionType.TRANSFER &&
                it.toAccountUid != null &&
                "${it.toAccountUid}:${MerchantNormalizer.normalize(it.note.ifBlank { it.description })}" == key
            }.map { it.uid }

            if (matchingTransferUids.isNotEmpty()) {
                instructions.add(AutoTagInstruction("SIP", matchingTransferUids, isRemoval = true))
                instructions.add(AutoTagInstruction("Recurring Deposit", matchingTransferUids, isRemoval = true))
                instructions.add(AutoTagInstruction("EMI", matchingTransferUids, isRemoval = true))
                instructions.add(AutoTagInstruction("Recurring Transfer", matchingTransferUids, isRemoval = true))
            }
        }

        return instructions
    }
}
