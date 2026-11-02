package com.moneymanager.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "card_statement_transactions",
    indices = [
        Index("statementId"),
        Index("accountOrCardNumberMasked"),
        Index("statementYearMonth"),
        Index(value = ["accountOrCardNumberMasked", "statementYearMonth"]),
        Index("transactionDateEpochDay"),
        Index("bankId"),
        Index("referenceNumber")
    ]
)
data class CardStatementTransactionEntity(
    @PrimaryKey
    val uid: String,

    // --- Statement Identity & Temporal Partitioning ---
    val statementId: String,
    val bankId: String,
    val bankName: String,
    val accountOrCardNumberMasked: String,
    val accountHolderName: String,
    val statementYearMonth: String, // "YYYY-MM"
    val statementDateEpochDay: Long,
    val paymentDueDateEpochDay: Long?,
    val billingPeriodStartEpochDay: Long?,
    val billingPeriodEndEpochDay: Long?,

    // --- Statement Financial & Limit Snapshot ---
    val creditLimit: Double?,
    val availableCredit: Double?,
    val cashLimit: Double?,
    val previousBalance: Double,
    val statementPurchasesOrDebits: Double,
    val statementPaymentsOrCredits: Double,
    val statementTotalDue: Double,
    val statementMinDue: Double?,
    val isReconciled: Boolean,

    // --- Reward / Cashback Snapshot ---
    val rewardType: String?,
    val rewardEarned: Double?,
    val rewardRedeemedOrCredited: Double?,
    val rewardClosingBalance: Double?,
    val rewardProgramName: String?,

    // --- Transaction Line Item ---
    val serialNumber: Int,
    val transactionDateEpochDay: Long,
    val referenceNumber: String?,
    val rawNarration: String,
    val cleanNarration: String,
    val amount: Double,
    val isCredit: Boolean,
    val debit: Double?,
    val credit: Double?,
    val rewardPoints: Int?,
    val pageNumber: Int,
    val importedAtTimestamp: Long = System.currentTimeMillis()
)

data class CardStatementSummaryRecord(
    val statementId: String,
    val bankId: String,
    val bankName: String,
    val accountOrCardNumberMasked: String,
    val accountHolderName: String,
    val statementYearMonth: String,
    val statementDateEpochDay: Long,
    val paymentDueDateEpochDay: Long?,
    val statementTotalDue: Double,
    val statementMinDue: Double?,
    val isReconciled: Boolean,
    val transactionCount: Int,
    val totalDebits: Double,
    val totalCredits: Double
)
