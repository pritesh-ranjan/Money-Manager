package com.moneymanager.app.domain.statement.core

import com.moneymanager.app.data.local.entity.CardStatementTransactionEntity
import java.time.format.DateTimeFormatter
import java.util.Locale

object StatementEntityMapper {

    private val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ENGLISH)

    /**
     * Ensures credit card numbers are defensively masked to prevent raw 13-19 digit PAN leakage.
     */
    fun sanitizeCardMask(rawMask: String): String {
        val clean = rawMask.replace(" ", "").trim()
        val allDigits = clean.all { it.isDigit() }
        return if (allDigits && clean.length in 13..19) {
            val first = clean.take(4)
            val last = clean.takeLast(4)
            val maskedMiddle = "X".repeat(clean.length - 8)
            "$first$maskedMiddle$last"
        } else {
            clean
        }
    }

    /**
     * Converts a domain [StatementParseResult] into a list of [CardStatementTransactionEntity]
     * ready for persistence in the unified statement table.
     */
    fun toEntities(
        result: StatementParseResult,
        importedAt: Long = System.currentTimeMillis(),
        isPaidOverride: Boolean? = null
    ): List<CardStatementTransactionEntity> {
        val meta = result.metadata
        val summary = result.summary

        val maskedCard = sanitizeCardMask(meta.accountOrCardNumberMasked)
        val statementYearMonth = meta.statementDate.format(yearMonthFormatter)
        val statementId = buildStatementId(maskedCard, statementYearMonth)

        val statementDateEpoch = meta.statementDate.toEpochDay()
        val paymentDueDateEpoch = meta.paymentDueDate?.toEpochDay()
        val billingStartEpoch = meta.billingPeriodStart?.toEpochDay()
        val billingEndEpoch = meta.billingPeriodEnd?.toEpochDay()

        val initialIsPaid = isPaidOverride ?: (summary.totalAmountDue <= 0.01)

        val reward = summary.rewardSummary

        return result.transactions.map { tx ->
            CardStatementTransactionEntity(
                uid = "${statementId}_${tx.serialNumber}",
                statementId = statementId,
                bankId = meta.bankId,
                bankName = meta.bankName,
                accountOrCardNumberMasked = maskedCard,
                accountHolderName = meta.accountHolderName,
                statementYearMonth = statementYearMonth,
                statementDateEpochDay = statementDateEpoch,
                paymentDueDateEpochDay = paymentDueDateEpoch,
                billingPeriodStartEpochDay = billingStartEpoch,
                billingPeriodEndEpochDay = billingEndEpoch,
                creditLimit = meta.creditLimit,
                availableCredit = meta.availableCredit,
                cashLimit = meta.cashLimit,
                previousBalance = summary.previousBalance,
                statementPurchasesOrDebits = summary.purchasesOrDebits,
                statementPaymentsOrCredits = summary.paymentsOrCredits,
                statementTotalDue = summary.totalAmountDue,
                statementMinDue = summary.minimumAmountDue,
                isReconciled = initialIsPaid,
                rewardType = reward?.rewardType?.name,
                rewardEarned = reward?.earned,
                rewardRedeemedOrCredited = reward?.redeemedOrTransferred,
                rewardClosingBalance = reward?.closingBalance,
                rewardProgramName = reward?.destinationWalletOrProgram,
                serialNumber = tx.serialNumber,
                transactionDateEpochDay = tx.date.toEpochDay(),
                referenceNumber = tx.referenceNumber,
                rawNarration = tx.rawNarration,
                cleanNarration = tx.cleanNarration,
                amount = tx.amount,
                isCredit = tx.isCredit,
                debit = tx.debit,
                credit = tx.credit,
                rewardPoints = tx.rewardPoints,
                pageNumber = tx.pageNumber,
                importedAtTimestamp = importedAt
            )
        }
    }

    /**
     * Builds a deterministic statement identifier from the masked card number and YYYY-MM.
     * e.g. "418688******8158_2026-10"
     */
    fun buildStatementId(cardMasked: String, yearMonth: String): String {
        val sanitizedCard = sanitizeCardMask(cardMasked)
        val sanitizedYm = yearMonth.trim()
        return "${sanitizedCard}_$sanitizedYm"
    }
}
