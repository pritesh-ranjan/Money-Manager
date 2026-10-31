package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.local.entity.InsightSeverity
import com.moneymanager.app.data.local.entity.InsightType
import com.moneymanager.app.data.local.entity.RecurrenceFrequency
import com.moneymanager.app.data.local.entity.RecurrentTransferKind

data class DetectedInsight(
    val id: String,
    val type: InsightType,
    val severity: InsightSeverity,
    val title: String,
    val summary: String,
    val targetKey: String?,
    val categoryUid: String? = null,
    val accountUid: String? = null,
    val toAccountUid: String? = null,
    val transferKind: RecurrentTransferKind? = null,
    val amount: Double?,
    val historicalAverage: Double? = null,
    val historicalMedian: Double? = null,
    val percentChange: Double? = null,
    val robustZ: Double? = null,
    val confidence: Double? = null,
    val confidenceBreakdown: String? = null,
    val frequency: RecurrenceFrequency? = null,
    val expectedNextDate: Long? = null,
    val autoAssignedTag: String? = null,
    val transactionUids: List<String> = emptyList()
)
