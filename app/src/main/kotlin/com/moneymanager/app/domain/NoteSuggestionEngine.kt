package com.moneymanager.app.domain

import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.data.local.entity.TransactionWithDetails

data class NoteSuggestion(
    val note: String,
    val usageCount: Int,
    val lastTimestamp: Long,
    val lastExpenseCategoryUid: String? = null,
    val lastIncomeCategoryUid: String? = null,
    val lastAccountUid: String? = null
)

object NoteSuggestionEngine {

    fun getSuggestions(
        transactions: List<TransactionWithDetails>,
        query: String,
        limit: Int = 8
    ): List<NoteSuggestion> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return emptyList()

        val validTxs = transactions.filter {
            !it.transaction.isDeleted && it.transaction.note.isNotBlank()
        }

        val grouped = validTxs.groupBy { it.transaction.note.trim().lowercase() }
        val suggestions = mutableListOf<NoteSuggestion>()

        for ((_, txList) in grouped) {
            val sortedByRecent = txList.sortedByDescending { it.transaction.timestamp }
            val mostRecent = sortedByRecent.first()
            val displayNote = mostRecent.transaction.note.trim()

            // Don't show suggestion if it's already an exact match
            if (displayNote.equals(trimmedQuery, ignoreCase = true)) {
                continue
            }

            // Must contain the query substring
            if (!displayNote.contains(trimmedQuery, ignoreCase = true)) {
                continue
            }

            val lastExpenseCat = sortedByRecent.firstOrNull {
                it.transaction.type == TransactionType.EXPENSE && it.transaction.categoryUid != null
            }?.transaction?.categoryUid

            val lastIncomeCat = sortedByRecent.firstOrNull {
                it.transaction.type == TransactionType.INCOME && it.transaction.categoryUid != null
            }?.transaction?.categoryUid

            val lastAccount = mostRecent.transaction.accountUid

            suggestions.add(
                NoteSuggestion(
                    note = displayNote,
                    usageCount = txList.size,
                    lastTimestamp = mostRecent.transaction.timestamp,
                    lastExpenseCategoryUid = lastExpenseCat,
                    lastIncomeCategoryUid = lastIncomeCat,
                    lastAccountUid = lastAccount
                )
            )
        }

        return suggestions.sortedWith(
            compareByDescending<NoteSuggestion> { it.note.startsWith(trimmedQuery, ignoreCase = true) }
                .thenByDescending { it.usageCount }
                .thenByDescending { it.lastTimestamp }
        ).take(limit)
    }
}
