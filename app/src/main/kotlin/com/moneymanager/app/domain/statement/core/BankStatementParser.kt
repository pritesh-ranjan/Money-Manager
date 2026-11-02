package com.moneymanager.app.domain.statement.core

import java.io.InputStream

data class StatementInspectionContext(
    val firstPageText: String,
    val totalPages: Int,
    val headerLines: List<String> = emptyList()
)

interface BankStatementParser {
    val bankId: String
    val displayName: String
    val supportedStatementType: StatementType

    /**
     * Inspects lightweight document signals (e.g. keywords, header text, page count)
     * to determine if this parser strategy handles the document.
     */
    fun canParse(context: StatementInspectionContext): Boolean

    /**
     * Parses the PDF stream into a fully structured, normalized, and reconciled result.
     * Accepts optional password if the PDF is password-protected.
     */
    fun parse(inputStream: InputStream, password: String? = null): StatementParseResult
}
