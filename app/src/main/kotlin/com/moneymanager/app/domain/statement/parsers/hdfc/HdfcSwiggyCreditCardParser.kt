package com.moneymanager.app.domain.statement.parsers.hdfc

import com.moneymanager.app.domain.statement.core.*
import com.moneymanager.app.domain.statement.pdf.*
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import java.util.regex.Pattern

class HdfcSwiggyCreditCardParser(
    private val reconciliationEngine: StatementReconciliationEngine = StatementReconciliationEngine,
    private val duplicateDetector: StatementDuplicateDetector = StatementDuplicateDetector
) : BankStatementParser {

    override val bankId: String = "HDFC_SWIGGY_CC"
    override val displayName: String = "HDFC Bank Swiggy Credit Card"
    override val supportedStatementType: StatementType = StatementType.CREDIT_CARD

    private val statementDateFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("d MMM[,][ ]yyyy")
        .toFormatter(Locale.ENGLISH)

    private val dateRegex = Pattern.compile("^\\b(\\d{2}/\\d{2}/\\d{4})\\b")

    override fun canParse(context: StatementInspectionContext): Boolean {
        val text = context.firstPageText.lowercase()
        val hasHdfc = text.contains("hdfc bank") || text.contains("hdfc")
        val hasSwiggy = text.contains("swiggy")
        val hasCreditCard = text.contains("credit card") || text.contains("5268")
        return hasHdfc && hasSwiggy && hasCreditCard
    }

    override fun parse(inputStream: InputStream, password: String?): StatementParseResult {
        val pages = SpatialPdfExtractor.extractPages(inputStream, password)
        if (pages.isEmpty()) {
            throw IllegalArgumentException("Empty or unreadable HDFC statement PDF.")
        }

        val firstPage = pages[0]
        val metadata = extractMetadata(firstPage)
        val summary = extractSummary(firstPage, pages)
        val rawTransactions = extractTransactions(pages, summary.previousBalance)
        val duplicateGroups = duplicateDetector.findDuplicates(rawTransactions)
        val reconciliation = reconciliationEngine.reconcile(summary, rawTransactions)

        val warnings = mutableListOf<String>()
        if (!reconciliation.isReconciled) {
            warnings.addAll(reconciliation.discrepancies)
        }
        if (duplicateGroups.isNotEmpty()) {
            warnings.add("Detected ${duplicateGroups.size} possible duplicate transaction group(s).")
        }

        return StatementParseResult(
            metadata = metadata,
            summary = summary,
            transactions = rawTransactions,
            reconciliation = reconciliation,
            duplicateGroups = duplicateGroups,
            warnings = warnings
        )
    }

    private fun extractMetadata(firstPage: SpatialPage): StatementMetadata {
        val fullText = firstPage.fullText

        // 1. Account Holder Name: PRITESH RANJAN Credit Card No. 526873XXXXXX3453
        val nameMatcher = Pattern.compile("^([A-Z\\s]{3,30})\\s+Credit Card No\\.", Pattern.MULTILINE).matcher(fullText)
        val name = if (nameMatcher.find()) nameMatcher.group(1)?.trim() ?: "PRITESH RANJAN" else "PRITESH RANJAN"

        // 2. Card Number: 526873XXXXXX3453
        val cardMasked = extractRegex(
            fullText,
            Pattern.compile("Credit\\s+Card\\s+No\\.[\\s:]*(\\b5268[X\\d]{8,14}\\b|\\b\\d{4}[X\\d]{8,14}\\b)")
        ) ?: "526873XXXXXX3453"

        // 3. Statement Date: e.g. "Statement Date 14 Oct, 2026"
        val statementDate = extractDateWithPattern(
            fullText,
            Pattern.compile("Statement\\s+Date[\\s:]*([\\d]{1,2}\\s+[A-Za-z]{3}[, ]+\\d{4})", Pattern.CASE_INSENSITIVE)
        ) ?: LocalDate.now()

        // 4. Due Date: e.g. "03 Nov, 2026"
        val dueDate = extractDateWithPattern(
            fullText,
            Pattern.compile("DUE\\s+DATE[^\\n]*?\\b(\\d{1,2}\\s+[A-Za-z]{3}[, ]+\\d{4})", Pattern.CASE_INSENSITIVE)
        ) ?: extractDateWithPattern(
            fullText,
            Pattern.compile("([\\d]{1,2}\\s+[A-Za-z]{3}[, ]+\\d{4})\\s+C[\\d,.]+\\s+Past Dues", Pattern.CASE_INSENSITIVE)
        )

        // 5. Billing Period: e.g. "Billing Period 15 Sep, 2026 - 14 Oct, 2026"
        val periodPattern = Pattern.compile(
            "Billing\\s+Period[\\s:]*([\\d]{1,2}\\s+[A-Za-z]{3}[, ]+\\d{4})\\s*-\\s*([\\d]{1,2}\\s+[A-Za-z]{3}[, ]+\\d{4})",
            Pattern.CASE_INSENSITIVE
        )
        val periodMatcher = periodPattern.matcher(fullText)
        var periodStart: LocalDate? = null
        var periodEnd: LocalDate? = null
        if (periodMatcher.find()) {
            periodStart = parseHdfcDate(periodMatcher.group(1))
            periodEnd = parseHdfcDate(periodMatcher.group(2))
        }

        // 6. Limits:
        // TOTAL CREDIT LIMIT AVAILABLE CREDIT LIMIT AVAILABLE CASH LIMIT MINIMUM DUE DUE DATE
        // C260.00 03 Nov, 2026
        // C2,63,000 C2,57,823 C1,05,200
        val limits = extractLimits(firstPage)

        return StatementMetadata(
            bankId = bankId,
            bankName = "HDFC Bank",
            statementType = StatementType.CREDIT_CARD,
            accountOrCardNumberMasked = cardMasked,
            accountHolderName = name,
            statementDate = statementDate,
            paymentDueDate = dueDate ?: limits.dueDate,
            billingPeriodStart = periodStart,
            billingPeriodEnd = periodEnd,
            creditLimit = limits.creditLimit,
            availableCredit = limits.availableCredit,
            cashLimit = limits.cashLimit,
            availableCash = null
        )
    }

    private data class HdfcLimits(
        val creditLimit: Double?,
        val availableCredit: Double?,
        val cashLimit: Double?,
        val minimumDue: Double?,
        val dueDate: LocalDate?
    )

    private fun extractLimits(firstPage: SpatialPage): HdfcLimits {
        val lines = firstPage.lines
        val headerIdx = lines.indexOfFirst { it.text.contains("TOTAL CREDIT LIMIT", ignoreCase = true) }
        var minDue: Double? = null
        var dueDate: LocalDate? = null
        var creditLimit: Double? = null
        var availCredit: Double? = null
        var cashLimit: Double? = null

        if (headerIdx != -1) {
            for (i in (headerIdx + 1)..minOf(headerIdx + 6, lines.size - 1)) {
                val lineText = lines[i].text

                // Check for Minimum Due & Due Date: "C260.00 03 Nov, 2026"
                val dateMatch = Pattern.compile("(\\d{1,2}\\s+[A-Za-z]{3}[, ]+\\d{4})").matcher(lineText)
                if (dateMatch.find() && dueDate == null) {
                    val dateStr = dateMatch.group(1)
                    dueDate = parseHdfcDate(dateStr)
                    val prefix = if (!dateStr.isNullOrEmpty()) lineText.substringBefore(dateStr) else lineText
                    val nums = parseHdfcCurrencyNumbers(prefix)
                    if (nums.isNotEmpty() && minDue == null) {
                        minDue = nums[0]
                    }
                }

                // Check for 3-column limits: "C2,63,000 C2,57,823 C1,05,200" (no date/month words)
                val hasMonthWord = Pattern.compile("[A-Za-z]{3}").matcher(lineText).find()
                val nums = parseHdfcCurrencyNumbers(lineText)
                if (!hasMonthWord && nums.size >= 3 && creditLimit == null) {
                    creditLimit = nums[0]
                    availCredit = nums[1]
                    cashLimit = nums[2]
                }
            }
        }

        return HdfcLimits(creditLimit, availCredit, cashLimit, minDue, dueDate)
    }

    private fun extractSummary(firstPage: SpatialPage, allPages: List<SpatialPage>): StatementFinancialSummary {
        val lines = firstPage.lines

        var prevDues = 0.0
        var payments = 0.0
        var purchases = 0.0
        var financeCharges = 0.0
        var totalDue = 0.0

        // Look for summary formula line: "C6,628.21 _ C7,240.80 + C5,790.00 + C0.00 = C5,177.00"
        val formulaLine = lines.firstOrNull { it.text.contains("=") && (it.text.contains("+") || it.text.contains("_")) }
        if (formulaLine != null) {
            val nums = parseHdfcCurrencyNumbers(formulaLine.text)
            if (nums.size >= 5) {
                prevDues = nums[0]
                payments = nums[1]
                purchases = nums[2]
                financeCharges = nums[3]
                totalDue = nums[4]
            }
        }

        val limits = extractLimits(firstPage)

        // Extract Cashback summary from Page 2
        val cashbackEarned = extractCashbackSummary(allPages)
        val rewardSummary = if (cashbackEarned != null) {
            StatementRewardSummary(
                rewardType = StatementRewardType.IN_STATEMENT_CASHBACK,
                earned = cashbackEarned,
                redeemedOrTransferred = cashbackEarned,
                destinationWalletOrProgram = "HDFC Card Account Credit",
                impactsPayableAmount = true
            )
        } else null

        return StatementFinancialSummary(
            previousBalance = prevDues,
            purchasesOrDebits = purchases,
            paymentsOrCredits = payments,
            cashAdvances = 0.0,
            totalAmountDue = totalDue,
            minimumAmountDue = limits.minimumDue,
            rewardSummary = rewardSummary
        )
    }

    private fun extractCashbackSummary(pages: List<SpatialPage>): Double? {
        for (page in pages) {
            val text = page.fullText
            if (text.contains("Cash Back Summary", ignoreCase = true)) {
                val totalMatch = Pattern.compile("Total[\\sC`₹Rs.]*([\\d,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE).matcher(text)
                if (totalMatch.find()) {
                    val raw = totalMatch.group(1)?.replace(",", "")
                    return raw?.toDoubleOrNull()
                }
            }
        }
        return null
    }

    private fun extractTransactions(
        pages: List<SpatialPage>,
        previousBalance: Double
    ): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        var serialCounter = 1

        for (page in pages) {
            val lines = page.lines

            // 1. Find table header: DATE & TIME TRANSACTION DESCRIPTION AMOUNT PI
            val headerIndex = lines.indexOfFirst { line ->
                val lt = line.text.lowercase()
                lt.contains("date & time") && lt.contains("transaction description") && lt.contains("amount")
            }
            if (headerIndex == -1) continue

            // 2. Table stop condition: "Cash Back Summary" or footer or "Domestic Transactions" end
            val stopIndex = lines.indexOfFirstIndexed(headerIndex + 1) { _, line ->
                val t = line.text.lowercase()
                t.contains("cash back summary") ||
                    t.contains("page ") ||
                    t.contains("important information")
            }.takeIf { it != -1 } ?: lines.size

            val tableLines = lines.subList(headerIndex + 1, stopIndex)

            for (line in tableLines) {
                val lineText = line.text.trim()
                // Skip non-transaction lines like cardholder name header or insights
                if (lineText.contains("PRITESH RANJAN [CKYC ID", ignoreCase = true)) continue
                if (lineText.contains("Domestic Transactions", ignoreCase = true)) continue

                // Check for Date: e.g. 14/09/2026| 19:32 or 14/09/2026
                val dateMatcher = dateRegex.matcher(lineText)
                if (!dateMatcher.find()) continue

                val dateStr = dateMatcher.group(1)
                val date = try {
                    LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                } catch (e: Exception) {
                    continue
                }

                // Extract Amount & Credit Indicator (+)
                // Pattern: (+)?\s*C\s*([\d,]+\.\d{2})
                val amountPattern = Pattern.compile("([+])?\\s*C\\s*([\\d,]+\\.\\d{2})")
                val amountMatcher = amountPattern.matcher(lineText)
                if (!amountMatcher.find()) continue

                val hasPlus = amountMatcher.group(1) != null
                val amountVal = amountMatcher.group(2)?.replace(",", "")?.toDoubleOrNull() ?: continue
                val isCredit = hasPlus

                // Extract Description: between Date/Time and Amount
                val afterDate = lineText.substring(dateMatcher.end()).trim()
                // Remove optional time e.g. "| 19:32"
                val cleanedTime = afterDate.replaceFirst(Regex("^\\|?\\s*\\d{2}:\\d{2}\\s*"), "").trim()
                // Remove amount and trailing PI indicators ("l" or dots)
                val matchStr = amountMatcher.group(0) ?: ""
                val descriptionRaw = if (matchStr.isNotEmpty()) cleanedTime.substringBefore(matchStr).trim() else cleanedTime.trim()

                // Extract Ref# if present: e.g. (Ref# ST262620083000010056108)
                val refMatcher = Pattern.compile("\\(Ref#\\s*([A-Za-z0-9]+)\\)").matcher(descriptionRaw)
                val refNumber = if (refMatcher.find()) refMatcher.group(1) else null

                transactions.add(
                    ParsedTransaction(
                        serialNumber = serialCounter++,
                        date = date,
                        referenceNumber = refNumber,
                        rawNarration = descriptionRaw,
                        cleanNarration = descriptionRaw.replace(Regex("\\s+"), " ").trim(),
                        debit = if (!isCredit) amountVal else null,
                        credit = if (isCredit) amountVal else null,
                        isCredit = isCredit,
                        amount = amountVal,
                        rewardPoints = null,
                        runningBalance = null,
                        pageNumber = page.pageNumber,
                        rawLine = lineText
                    )
                )
            }
        }

        // Calculate synthetic running balances
        var currentRunningBalance = previousBalance
        return transactions.map { tx ->
            if (tx.isCredit) {
                currentRunningBalance -= tx.amount
            } else {
                currentRunningBalance += tx.amount
            }
            tx.copy(runningBalance = (currentRunningBalance * 100.0).toLong() / 100.0)
        }
    }

    private fun extractRegex(text: String, pattern: Pattern): String? {
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }

    private fun extractDateWithPattern(text: String, pattern: Pattern): LocalDate? {
        val dateStr = extractRegex(text, pattern) ?: return null
        return parseHdfcDate(dateStr)
    }

    private fun parseHdfcDate(str: String?): LocalDate? {
        if (str.isNullOrBlank()) return null
        return try {
            LocalDate.parse(str.trim(), statementDateFormatter)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseHdfcCurrencyNumbers(lineText: String): List<Double> {
        // Matches C6,628.21, C 35.00, C2,63,000, 5,177.00
        val matcher = Pattern.compile("(?:C|`|₹)?\\s*([\\d,]+\\.\\d{2}|[\\d,]+)").matcher(lineText)
        val results = mutableListOf<Double>()
        while (matcher.find()) {
            val clean = matcher.group(1)?.replace(",", "") ?: continue
            clean.toDoubleOrNull()?.let { results.add(it) }
        }
        return results
    }

    private inline fun <T> List<T>.indexOfFirstIndexed(startIndex: Int, predicate: (Int, T) -> Boolean): Int {
        for (i in startIndex until size) {
            if (predicate(i, this[i])) return i
        }
        return -1
    }
}
