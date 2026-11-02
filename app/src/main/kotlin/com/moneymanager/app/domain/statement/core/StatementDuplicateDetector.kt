package com.moneymanager.app.domain.statement.core

object StatementDuplicateDetector {

    /**
     * Detects potential duplicate transactions in the parsed list.
     * Checks by reference number first (if present), or by (date, amount, isCredit, cleanNarration).
     */
    fun findDuplicates(transactions: List<ParsedTransaction>): List<DuplicateGroup> {
        val groups = mutableListOf<DuplicateGroup>()

        // 1. Check by reference number (when reference number exists and is non-empty)
        val byRef = transactions
            .filter { !it.referenceNumber.isNullOrBlank() }
            .groupBy { it.referenceNumber!!.trim() }
            .filter { it.value.size > 1 }

        for ((ref, txList) in byRef) {
            groups.add(
                DuplicateGroup(
                    key = "ReferenceNumber:$ref",
                    count = txList.size,
                    transactions = txList
                )
            )
        }

        // 2. Check by exact semantic match (Date + Amount + isCredit + CleanNarration)
        // Only consider if not already grouped by ref
        val refsAlreadyGrouped = byRef.keys
        val bySemantic = transactions
            .filter { it.referenceNumber.isNullOrBlank() || it.referenceNumber !in refsAlreadyGrouped }
            .groupBy { "${it.date}|${it.amount}|${it.isCredit}|${it.cleanNarration.lowercase().trim()}" }
            .filter { it.value.size > 1 }

        for ((key, txList) in bySemantic) {
            groups.add(
                DuplicateGroup(
                    key = "Semantic:$key",
                    count = txList.size,
                    transactions = txList
                )
            )
        }

        return groups
    }
}
