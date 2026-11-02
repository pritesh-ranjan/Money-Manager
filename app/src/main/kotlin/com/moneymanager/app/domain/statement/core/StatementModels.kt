package com.moneymanager.app.domain.statement.core

import java.time.LocalDate

enum class StatementType(val displayName: String) {
    CREDIT_CARD("Credit Card"),
    SAVINGS_ACCOUNT("Savings Account"),
    CURRENT_ACCOUNT("Current Account")
}

enum class StatementRewardType(val displayName: String) {
    /** 1. Cashback directly credited into the statement; alters Total Amount Due */
    IN_STATEMENT_CASHBACK("In-Statement Cashback"),

    /** 2. Cashback transferred to an external merchant or wallet (e.g. Amazon Pay); does NOT alter statement bill */
    EXTERNAL_WALLET_CASHBACK("External Wallet Cashback"),

    /** 3. Traditional reward/loyalty points accrued to a reward balance; does NOT alter statement bill */
    LOYALTY_POINTS("Loyalty Points")
}

data class StatementRewardSummary(
    val rewardType: StatementRewardType,
    val earned: Double,
    val openingBalance: Double? = null,
    val redeemedOrTransferred: Double? = null,
    val closingBalance: Double? = null,
    val destinationWalletOrProgram: String? = null,
    val impactsPayableAmount: Boolean = false
)

data class StatementMetadata(
    val bankId: String,
    val bankName: String,
    val statementType: StatementType,
    val accountOrCardNumberMasked: String,
    val accountHolderName: String,
    val statementDate: LocalDate,
    val paymentDueDate: LocalDate?,
    val billingPeriodStart: LocalDate? = null,
    val billingPeriodEnd: LocalDate? = null,
    val creditLimit: Double? = null,
    val availableCredit: Double? = null,
    val cashLimit: Double? = null,
    val availableCash: Double? = null
)

data class StatementFinancialSummary(
    val previousBalance: Double,
    val purchasesOrDebits: Double,
    val paymentsOrCredits: Double,
    val cashAdvances: Double = 0.0,
    val totalAmountDue: Double,
    val minimumAmountDue: Double? = null,
    val rewardSummary: StatementRewardSummary? = null
)

data class ParsedTransaction(
    val serialNumber: Int,
    val date: LocalDate,
    val referenceNumber: String? = null,
    val rawNarration: String,
    val cleanNarration: String,
    val debit: Double? = null,
    val credit: Double? = null,
    val isCredit: Boolean,
    val amount: Double,
    val rewardPoints: Int? = null,
    val runningBalance: Double? = null,
    val pageNumber: Int,
    val rawLine: String? = null
)

data class ReconciliationResult(
    val isReconciled: Boolean,
    val openingBalance: Double,
    val calculatedDebits: Double,
    val expectedDebits: Double,
    val debitsMatch: Boolean,
    val calculatedCredits: Double,
    val expectedCredits: Double,
    val creditsMatch: Boolean,
    val calculatedClosingBalance: Double,
    val expectedClosingBalance: Double,
    val closingBalanceMatch: Boolean,
    val calculatedRewardTotal: Double? = null,
    val expectedRewardTotal: Double? = null,
    val rewardMatches: Boolean = true,
    val discrepancies: List<String> = emptyList()
)

data class DuplicateGroup(
    val key: String,
    val count: Int,
    val transactions: List<ParsedTransaction>
)

sealed class StatementSecurityException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class PasswordRequiredException(message: String = "Statement PDF is password-protected. Password is required.") :
        StatementSecurityException(message)

    class InvalidPasswordException(message: String = "The provided password for the statement PDF is incorrect.") :
        StatementSecurityException(message)
}

data class StatementParseResult(
    val metadata: StatementMetadata,
    val summary: StatementFinancialSummary,
    val transactions: List<ParsedTransaction>,
    val reconciliation: ReconciliationResult,
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val warnings: List<String> = emptyList()
)
