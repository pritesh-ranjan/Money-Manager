package com.moneymanager.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class InsightType {
    LARGE_TRANSACTION,
    CATEGORY_SPIKE,
    DUPLICATE,
    SUBSCRIPTION,            // Recurring expense (Netflix, Spotify, Gym, Utilities)
    RECURRING_TRANSFER,      // Recurring transfer (SIP investments, RD deposits, loan EMIs)
    FORGOTTEN_SUBSCRIPTION,  // Recurring expense with no manual interaction
    PRICE_INCREASE           // Subscription renewal amount jumped above variation tolerance
}

enum class RecurrentTransferKind {
    SIP_INVESTMENT,          // To Mutual Funds or Equity
    RECURRING_DEPOSIT,       // To FD / RD / PPF Investment account
    LOAN_EMI,                // To Loan / Liability account
    REGULAR_TRANSFER         // General inter-account transfer
}

enum class InsightSeverity {
    INFO,
    WARNING,
    HIGH
}

enum class InsightStatus {
    ACTIVE,
    RESOLVED
}

enum class RecurrenceFrequency(val displayName: String, val nominalDays: Double, val toleranceDays: Int) {
    WEEKLY("Weekly", 7.0, 1),
    FORTNIGHTLY("Fortnightly", 14.0, 2),
    MONTHLY("Monthly", 30.44, 4),
    QUARTERLY("Quarterly", 91.31, 7),
    YEARLY("Yearly", 365.25, 15)
}

enum class RecurrenceDecision {
    CONFIRMED,
    REJECTED
}

@Entity(
    tableName = "insights",
    indices = [
        Index("type"),
        Index("status"),
        Index("targetKey")
    ]
)
data class InsightEntity(
    @PrimaryKey
    val id: String,                         // Deterministic ID (e.g. "DUP:<tx1>:<tx2>", "SUB:<merchantKey>", "TXF:<toAcc>:<noteKey>")
    val type: InsightType,
    val severity: InsightSeverity,
    val status: InsightStatus,
    val title: String,
    val summary: String,                    // Human-readable explanation
    val targetKey: String?,                 // Normalized merchant key or transfer identifier
    val categoryUid: String? = null,
    val accountUid: String? = null,         // Source account
    val toAccountUid: String? = null,       // Destination account (for recurring transfers)
    val transferKind: RecurrentTransferKind? = null,
    val amount: Double?,                    // Flagged or latest transaction amount in base currency
    val historicalAverage: Double? = null,
    val historicalMedian: Double? = null,
    val percentChange: Double? = null,
    val robustZ: Double? = null,
    val confidence: Double? = null,         // 0.0 to 1.0 explainable score
    val confidenceBreakdown: String? = null, // "interval=0.95;amount=1.0;merchant=0.90;history=0.80"
    val frequency: RecurrenceFrequency? = null,
    val expectedNextDate: Long? = null,     // Epoch millis of expected next payment / transfer
    val autoAssignedTag: String? = null,    // Tag automatically assigned (e.g. "Subscription", "SIP")
    val firstDetectedAt: Long,
    val lastEvaluatedAt: Long
)

@Entity(
    tableName = "insight_transactions",
    primaryKeys = ["insightId", "transactionUid"],
    foreignKeys = [
        ForeignKey(
            entity = InsightEntity::class,
            parentColumns = ["id"],
            childColumns = ["insightId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("transactionUid")]
)
data class InsightTransactionCrossRef(
    val insightId: String,
    val transactionUid: String
)

@Entity(tableName = "recurrence_decisions")
data class RecurrenceDecisionEntity(
    @PrimaryKey
    val targetKey: String,                  // Normalized merchant key or transfer target key
    val decision: RecurrenceDecision,
    val decidedAt: Long
)

data class InsightWithTransactions(
    @Embedded
    val insight: InsightEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "uid",
        associateBy = Junction(
            value = InsightTransactionCrossRef::class,
            parentColumn = "insightId",
            entityColumn = "transactionUid"
        )
    )
    val transactions: List<TransactionEntity> = emptyList()
)
