package com.moneymanager.app.domain.statement.core

import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList

class StatementParserRegistry(
    initialParsers: List<BankStatementParser> = defaultParsers()
) {
    companion object {
        fun defaultParsers(): List<BankStatementParser> = listOf(
            com.moneymanager.app.domain.statement.parsers.icici.IciciAmazonPayCreditCardParser(),
            com.moneymanager.app.domain.statement.parsers.hdfc.HdfcSwiggyCreditCardParser(),
            com.moneymanager.app.domain.statement.parsers.axis.AxisBankCreditCardParser()
        )
    }

    private val parsers = CopyOnWriteArrayList<BankStatementParser>(initialParsers)

    fun register(parser: BankStatementParser) {
        if (parsers.none { it.bankId == parser.bankId }) {
            parsers.add(parser)
        }
    }

    fun unregister(bankId: String) {
        parsers.removeIf { it.bankId == bankId }
    }

    fun getRegisteredParsers(): List<BankStatementParser> = parsers.toList()

    /**
     * Resolves the matching parser for the provided document context.
     */
    fun findParser(context: StatementInspectionContext): BankStatementParser? {
        return parsers.firstOrNull { it.canParse(context) }
    }

    /**
     * Convenience method to parse an input stream with automatic parser detection
     * and optional password support.
     */
    fun parse(inputStream: InputStream, password: String? = null): StatementParseResult {
        // Read bytes so we can inspect and parse from separate streams
        val bytes = inputStream.readBytes()
        val context = com.moneymanager.app.domain.statement.pdf.SpatialPdfExtractor.inspect(
            bytes.inputStream(),
            password
        )
        val parser = findParser(context)
            ?: throw IllegalArgumentException("No matching bank statement parser found for this document.")
        return parser.parse(bytes.inputStream(), password)
    }

    /**
     * Sequentially attempts candidate passwords against an input stream.
     * Tests null (unencrypted) first, then candidate passwords in order.
     * Returns MultiPasswordParseResult on the first working password.
     * If all fail, rethrows the last encountered StatementSecurityException.
     */
    fun parseWithPasswords(
        inputStream: InputStream,
        candidatePasswords: List<String>
    ): MultiPasswordParseResult {
        val bytes = inputStream.readBytes()
        val distinctCandidates = candidatePasswords.map { it.trim() }.filter { it.isNotBlank() }.distinct()

        if (distinctCandidates.isEmpty()) {
            val res = parse(bytes.inputStream(), null)
            return MultiPasswordParseResult(result = res, workingPassword = null)
        }

        var lastSecurityException: StatementSecurityException? = null
        for (pwd in distinctCandidates) {
            try {
                val res = parse(bytes.inputStream(), pwd)
                return MultiPasswordParseResult(result = res, workingPassword = pwd)
            } catch (e: StatementSecurityException.InvalidPasswordException) {
                lastSecurityException = e
            } catch (e: StatementSecurityException.PasswordRequiredException) {
                lastSecurityException = e
            }
        }

        // Try unencrypted fallback in case candidates failed on an unencrypted document
        try {
            val res = parse(bytes.inputStream(), null)
            return MultiPasswordParseResult(result = res, workingPassword = null)
        } catch (_: Exception) {
            // Document required a valid password
        }

        throw (lastSecurityException ?: StatementSecurityException.InvalidPasswordException())
    }
}

data class MultiPasswordParseResult(
    val result: StatementParseResult,
    val workingPassword: String?
)
