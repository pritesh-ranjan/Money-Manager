package com.moneymanager.app.domain

import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class StatementStatus(val label: String) {
    PAID("Paid"),
    DUE_SOON("Due Soon"),
    OVERDUE("Overdue"),
    UNPAID("Unpaid"),
    NO_BILL("No Bill")
}

data class CreditCardCycleSummary(
    val accountUid: String,
    val cardName: String,
    val lastStatementDate: LocalDate,
    val nextStatementDate: LocalDate,
    val paymentDueDate: LocalDate,
    val statementBilledAmount: Double,
    val paymentsSinceStatement: Double,
    val remainingStatementDue: Double,
    val unbilledAmount: Double,
    val totalOutstanding: Double,
    val creditLimit: Double?,
    val availableCredit: Double?,
    val creditUtilizationPercent: Int?,
    val daysUntilDue: Long,
    val status: StatementStatus,
    val settlementAccountUid: String?
)

object CreditCardStatementEngine {

    fun calculateStatement(
        card: AccountEntity,
        transactions: List<TransactionEntity>, // all transactions involving this card
        currentDate: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CreditCardCycleSummary {
        val statementDay = card.statementDay ?: 1
        val dueDay = card.paymentDueDay ?: 1

        val currentYm = YearMonth.from(currentDate)
        val currentMonthCutoff = currentYm.atDay(clampDay(currentYm, statementDay))

        // Determine latest closed statement cutoff date and previous cutoff
        val (lastStatementDate, prevStatementDate, nextStatementDate) = if (!currentDate.isBefore(currentMonthCutoff)) {
            val last = currentMonthCutoff
            val prevYm = currentYm.minusMonths(1)
            val prev = prevYm.atDay(clampDay(prevYm, statementDay))
            val nextYm = currentYm.plusMonths(1)
            val next = nextYm.atDay(clampDay(nextYm, statementDay))
            Triple(last, prev, next)
        } else {
            val prevYm = currentYm.minusMonths(1)
            val last = prevYm.atDay(clampDay(prevYm, statementDay))
            val prevPrevYm = currentYm.minusMonths(2)
            val prev = prevPrevYm.atDay(clampDay(prevPrevYm, statementDay))
            val next = currentMonthCutoff
            Triple(last, prev, next)
        }

        // Determine payment due date
        val statementYm = YearMonth.from(lastStatementDate)
        val paymentDueDate = if (dueDay > statementDay) {
            statementYm.atDay(clampDay(statementYm, dueDay))
        } else {
            val nextYm = statementYm.plusMonths(1)
            nextYm.atDay(clampDay(nextYm, dueDay))
        }

        val prevCutoffMillis = prevStatementDate.atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli()
        val lastCutoffMillis = lastStatementDate.atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli()

        // 1. Spends within latest closed cycle: (prevCutoffMillis, lastCutoffMillis]
        var billedSpends = 0.0
        var billedCredits = 0.0

        // 2. Payments made after lastStatementDate: (lastCutoffMillis, now]
        var paymentsSinceStatement = 0.0

        // 3. Spends after lastStatementDate: (lastCutoffMillis, now]
        var unbilledSpends = 0.0
        var unbilledCredits = 0.0

        // Track full ledger balance using the same sign rules as NetWorthCalculator
        var rawLedgerBalance = -card.openingBalance

        for (tx in transactions) {
            if (tx.isDeleted) continue

            when (tx.type) {
                TransactionType.INCOME -> {
                    if (tx.accountUid == card.uid) {
                        rawLedgerBalance += tx.accountAmount
                    }
                }
                TransactionType.EXPENSE -> {
                    if (tx.accountUid == card.uid) {
                        rawLedgerBalance -= tx.accountAmount
                    }
                }
                TransactionType.TRANSFER -> {
                    if (tx.accountUid == card.uid) {
                        rawLedgerBalance -= tx.accountAmount
                    }
                    if (tx.toAccountUid == card.uid) {
                        rawLedgerBalance += (tx.toAccountAmount ?: tx.accountAmount)
                    }
                }
                TransactionType.ADJUSTMENT -> {
                    if (tx.accountUid == card.uid) {
                        rawLedgerBalance += tx.accountAmount
                    }
                }
            }

            val txTime = tx.timestamp

            // Is this a payment/transfer into this card?
            val isPaymentToCard = tx.type == TransactionType.TRANSFER && tx.toAccountUid == card.uid
            val isCardExpense = tx.accountUid == card.uid && tx.type == TransactionType.EXPENSE
            val isCardRefund = tx.accountUid == card.uid && tx.type == TransactionType.INCOME
            val isCardAdjustment = tx.accountUid == card.uid && tx.type == TransactionType.ADJUSTMENT

            if (txTime in (prevCutoffMillis + 1)..lastCutoffMillis) {
                if (isCardExpense) {
                    billedSpends += tx.accountAmount
                } else if (isCardRefund) {
                    billedCredits += tx.accountAmount
                } else if (isCardAdjustment) {
                    billedSpends -= tx.accountAmount // positive ledger adjustment reduces liability debt
                }
            } else if (txTime > lastCutoffMillis) {
                if (isPaymentToCard) {
                    paymentsSinceStatement += (tx.toAccountAmount ?: tx.accountAmount)
                } else if (isCardExpense) {
                    unbilledSpends += tx.accountAmount
                } else if (isCardRefund) {
                    unbilledCredits += tx.accountAmount
                } else if (isCardAdjustment) {
                    unbilledSpends -= tx.accountAmount // positive ledger adjustment reduces liability debt
                }
            }
        }

        val statementBilled = maxOf(0.0, billedSpends - billedCredits)
        val totalOutstanding = maxOf(0.0, -rawLedgerBalance)
        val remainingStatementDue = minOf(maxOf(0.0, statementBilled - paymentsSinceStatement), totalOutstanding)
        val unbilled = maxOf(0.0, unbilledSpends - unbilledCredits)

        val creditLimit = card.creditLimit
        val availableCredit = creditLimit?.let { maxOf(0.0, it - totalOutstanding) }
        val creditUtilization = if (creditLimit != null && creditLimit > 0) {
            ((totalOutstanding / creditLimit) * 100).toInt().coerceIn(0, 100)
        } else null

        val daysUntilDue = ChronoUnit.DAYS.between(currentDate, paymentDueDate)

        val status = when {
            statementBilled <= 0.01 -> StatementStatus.NO_BILL
            remainingStatementDue <= 0.01 -> StatementStatus.PAID
            daysUntilDue < 0 -> StatementStatus.OVERDUE
            daysUntilDue <= 3 -> StatementStatus.DUE_SOON
            else -> StatementStatus.UNPAID
        }

        return CreditCardCycleSummary(
            accountUid = card.uid,
            cardName = card.name,
            lastStatementDate = lastStatementDate,
            nextStatementDate = nextStatementDate,
            paymentDueDate = paymentDueDate,
            statementBilledAmount = statementBilled,
            paymentsSinceStatement = paymentsSinceStatement,
            remainingStatementDue = remainingStatementDue,
            unbilledAmount = unbilled,
            totalOutstanding = totalOutstanding,
            creditLimit = creditLimit,
            availableCredit = availableCredit,
            creditUtilizationPercent = creditUtilization,
            daysUntilDue = daysUntilDue,
            status = status,
            settlementAccountUid = card.settlementAccountUid
        )
    }

    private fun clampDay(yearMonth: YearMonth, day: Int): Int {
        return day.coerceIn(1, yearMonth.lengthOfMonth())
    }
}
