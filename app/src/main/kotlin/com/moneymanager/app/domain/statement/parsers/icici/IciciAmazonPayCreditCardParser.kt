package com.moneymanager.app.domain.statement.parsers.icici

import com.moneymanager.app.domain.statement.core.*
import com.moneymanager.app.domain.statement.pdf.*
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.regex.Pattern

class IciciAmazonPayCreditCardParser(
    private val reconciliationEngine: StatementReconciliationEngine = StatementReconciliationEngine,
    private val duplicateDetector: StatementDuplicateDetector = StatementDuplicateDetector
) : BankStatementParser {

    override val bankId: String = "ICICI_AMAZON_PAY_CC"
    override val displayName: String = "ICICI Bank Amazon Pay Credit Card"
    override val supportedStatementType: StatementType = StatementType.CREDIT_CARD

    private val statementDateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
    private val dateRegex = Pattern.compile("^\\b(\\d{2}/\\d{2}/\\d{4})\\b")
    private val serNoRegex = Pattern.compile("^\\b(\\d{8,15})\\b")

    override fun canParse(context: StatementInspectionContext): Boolean {
        val text = context.firstPageText.lowercase()
        val hasAmazonPay = text.contains("amazon pay") || text.contains("amazonpay")
        val hasIcici = text.contains("icici bank") || text.contains("icicibank")
        val hasStatement = text.contains("credit card statement") || text.contains("statement summary")
        return hasAmazonPay && hasIcici && hasStatement
    }

    override fun parse(inputStream: InputStream, password: String?): StatementParseResult {
        val pages = SpatialPdfExtractor.extractPages(inputStream, password)
        if (pages.isEmpty()) {
            throw IllegalArgumentException("Empty or unreadable PDF statement.")
        }

        val firstPage = pages[0]
        val metadata = extractMetadata(firstPage)
        val summary = extractSummary(firstPage)
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

        // 1. Account holder name: located in left column (x < 250)
        val name = extractAccountHolderName(firstPage)

        // 2. Masked Card Number: e.g. 4315XXXXXXXX1234
        val cardMasked = extractRegex(
            fullText,
            Pattern.compile("(\\b4315[X\\d]{8,12}\\b|\\b\\d{4}X{6,10}\\d{4}\\b)")
        ) ?: "XXXX-XXXX-XXXX"

        // 3. Statement Date: e.g. "October 12, 2026"
        val statementDate = extractDateNearLabel(firstPage, "STATEMENT DATE") ?: LocalDate.now()

        // 4. Payment Due Date: e.g. "October 30, 2026"
        val dueDate = extractDateNearLabel(firstPage, "PAYMENT DUE DATE")

        // 5. Billing Period: e.g. "September 13, 2026 to October 12, 2026"
        val periodPattern = Pattern.compile(
            "Statement\\s+period[\\s:]*([A-Za-z]+\\s+\\d{1,2},\\s+\\d{4})\\s+to\\s+([A-Za-z]+\\s+\\d{1,2},\\s+\\d{4})",
            Pattern.CASE_INSENSITIVE
        )
        val periodMatcher = periodPattern.matcher(fullText)
        var periodStart: LocalDate? = null
        var periodEnd: LocalDate? = null
        if (periodMatcher.find()) {
            periodStart = parseStatementDate(periodMatcher.group(1))
            periodEnd = parseStatementDate(periodMatcher.group(2))
        }

        // 6. Credit Limits
        // Line after "Credit Limit (Including cash)": `8,40,000.00 `3,15,210.59 `84,000.00 `33,600.00
        val limits = extractLimitsFromSummary(firstPage)

        return StatementMetadata(
            bankId = bankId,
            bankName = "ICICI Bank",
            statementType = StatementType.CREDIT_CARD,
            accountOrCardNumberMasked = cardMasked,
            accountHolderName = name,
            statementDate = statementDate,
            paymentDueDate = dueDate,
            billingPeriodStart = periodStart,
            billingPeriodEnd = periodEnd,
            creditLimit = limits.creditLimit,
            availableCredit = limits.availableCredit,
            cashLimit = limits.cashLimit,
            availableCash = limits.availableCash
        )
    }

    private data class ExtractedLimits(
        val creditLimit: Double?,
        val availableCredit: Double?,
        val cashLimit: Double?,
        val availableCash: Double?
    )

    private fun extractLimitsFromSummary(page: SpatialPage): ExtractedLimits {
        val lines = page.lines
        val headerIdx = lines.indexOfFirst { it.text.contains("Credit Limit (Including cash)") }
        if (headerIdx != -1) {
            // Find numerical row following header within next 5 lines
            for (i in (headerIdx + 1)..minOf(headerIdx + 5, lines.size - 1)) {
                val numbers = parseRowCurrencyNumbers(lines[i].text)
                if (numbers.size >= 4) {
                    return ExtractedLimits(
                        creditLimit = numbers[0],
                        availableCredit = numbers[1],
                        cashLimit = numbers[2],
                        availableCash = numbers[3]
                    )
                }
            }
        }
        return ExtractedLimits(null, null, null, null)
    }

    private fun extractSummary(firstPage: SpatialPage): StatementFinancialSummary {
        val lines = firstPage.lines

        var totalDue = 0.0
        var prevBalance = 0.0
        var purchases = 0.0
        var cashAdvances = 0.0
        var payments = 0.0
        var minDue: Double? = null

        // 1. Locate "STATEMENT SUMMARY"
        val summaryIdx = lines.indexOfFirst { it.text.contains("STATEMENT SUMMARY") }
        if (summaryIdx != -1) {
            for (i in summaryIdx..minOf(summaryIdx + 10, lines.size - 1)) {
                val line = lines[i]
                val t = line.text

                // Total Amount Due line: e.g. line with currency amount on the left side
                if (line.bounds.x < 200 && parseRowCurrencyNumbers(t).size == 1 && totalDue == 0.0) {
                    val nums = parseRowCurrencyNumbers(t)
                    if (nums.size == 1 && totalDue == 0.0) {
                        totalDue = nums[0]
                    }
                }

                // 4-column summary numbers row: Previous Balance, Purchases, Cash Advances, Payments
                val numbers = parseRowCurrencyNumbers(t)
                if (numbers.size == 4 && prevBalance == 0.0) {
                    prevBalance = numbers[0]
                    purchases = numbers[1]
                    cashAdvances = numbers[2]
                    payments = numbers[3]
                }
            }
        }

        // 2. Locate Minimum Amount Due
        val minDueIdx = lines.indexOfFirst { it.text.contains("Minimum Amount due") }
        if (minDueIdx != -1) {
            for (i in (minDueIdx + 1)..minOf(minDueIdx + 3, lines.size - 1)) {
                val nums = parseRowCurrencyNumbers(lines[i].text)
                if (nums.isNotEmpty() && lines[i].bounds.x < 250) {
                    minDue = nums[0]
                    break
                }
            }
        }

        // 3. Fallbacks using regex if needed
        if (totalDue == 0.0) {
            totalDue = extractLabeledAmount(
                firstPage.fullText,
                Pattern.compile("Total\\s+Amount\\s+due[\\s`₹Rs.]*([\\d,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE)
            ) ?: 0.0
        }

        val rewardSummary = extractRewardSummary(firstPage)

        return StatementFinancialSummary(
            previousBalance = prevBalance,
            purchasesOrDebits = purchases,
            paymentsOrCredits = payments,
            cashAdvances = cashAdvances,
            totalAmountDue = totalDue,
            minimumAmountDue = minDue,
            rewardSummary = rewardSummary
        )
    }

    private fun extractDateNearLabel(page: SpatialPage, label: String): LocalDate? {
        val lines = page.lines
        val datePattern = Pattern.compile("([A-Za-z]+\\s+\\d{1,2},\\s+\\d{4})")
        val labelIdx = lines.indexOfFirst { it.getTextInRange(0f, 200f).contains(label, ignoreCase = true) }
        if (labelIdx != -1) {
            for (i in (labelIdx + 1)..minOf(labelIdx + 4, lines.size - 1)) {
                val candidateText = lines[i].getTextInRange(0f, 200f)
                val matcher = datePattern.matcher(candidateText)
                if (matcher.find()) {
                    return parseStatementDate(matcher.group(1))
                }
            }
        }
        return null
    }

    private fun extractRewardSummary(page: SpatialPage): StatementRewardSummary? {
        val exactEarnedPattern = Pattern.compile("^\\s*(\\d{1,6})\\s+(\\d{1,6})\\s*$")
        val lines = page.lines
        val earningsIdx = lines.indexOfFirst { it.text.contains("EARNINGS", ignoreCase = true) }
        if (earningsIdx != -1) {
            for (i in earningsIdx..minOf(earningsIdx + 8, lines.size - 1)) {
                val leftText = lines[i].getTextInRange(0f, 250f).trim()
                val matcher = exactEarnedPattern.matcher(leftText)
                if (matcher.find()) {
                    val earned = matcher.group(1)?.toDoubleOrNull() ?: 0.0
                    val transferred = matcher.group(2)?.toDoubleOrNull() ?: 0.0
                    if (earned > 0) {
                        return StatementRewardSummary(
                            rewardType = StatementRewardType.EXTERNAL_WALLET_CASHBACK,
                            earned = earned,
                            redeemedOrTransferred = transferred,
                            destinationWalletOrProgram = "Amazon Pay balance",
                            impactsPayableAmount = false
                        )
                    }
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

        val candidatePages = pages.take(2)

        for (page in candidatePages) {
            val lines = page.lines

            // 1. Locate header line
            val headerIndex = lines.indexOfFirst { line ->
                val lt = line.text.lowercase()
                lt.contains("date") &&
                    (lt.contains("serno") || lt.contains("ser no")) &&
                    lt.contains("transaction details")
            }
            if (headerIndex == -1) continue

            val headerLine = lines[headerIndex]
            val columns = calculateColumnsFromHeader(headerLine)

            // Stop before footer / non-table text
            val stopIndex = lines.indexOfFirstIndexed(headerIndex + 1) { _, line ->
                val t = line.text.lowercase()
                t.contains("the standing instruction for auto debit") ||
                    t.contains("international spends") ||
                    t.contains("safe banking tips")
            }.takeIf { it != -1 } ?: lines.size

            val tableLines = lines.subList(headerIndex + 1, stopIndex)
            var currentPendingRow: MutableTransactionRow? = null

            for (line in tableLines) {
                // Ignore lines from left sidebar widgets (e.g. Spends Overview pie chart labels)
                val lineDateText = line.getTextInRange(columns.dateMinX, columns.dateMaxX)
                val lineSerNoText = line.getTextInRange(columns.serNoMinX, columns.serNoMaxX)

                val hasDate = dateRegex.matcher(lineDateText.trim()).find()
                val hasSerNo = serNoRegex.matcher(lineSerNoText.trim()).find()
                val isNewRow = hasDate || (hasSerNo && lineDateText.isNotBlank())

                if (isNewRow) {
                    if (currentPendingRow != null) {
                        currentPendingRow.toParsedTransaction(serialCounter++, page.pageNumber)?.let {
                            transactions.add(it)
                        }
                    }

                    currentPendingRow = MutableTransactionRow(
                        dateString = lineDateText,
                        serNoString = lineSerNoText,
                        narrationParts = mutableListOf(line.getTextInRange(columns.detailsMinX, columns.detailsMaxX)),
                        rewardsString = line.getTextInRange(columns.rewardsMinX, columns.rewardsMaxX),
                        amountString = line.getTextInRange(columns.amountMinX, 9999f),
                        rawLines = mutableListOf(line.text)
                    )
                } else if (currentPendingRow != null) {
                    // Continuation line
                    val extraDetails = line.getTextInRange(columns.detailsMinX, columns.detailsMaxX)
                    if (extraDetails.isNotBlank()) {
                        currentPendingRow.narrationParts.add(extraDetails)
                    }

                    val extraReward = line.getTextInRange(columns.rewardsMinX, columns.rewardsMaxX)
                    if (extraReward.isNotBlank() && currentPendingRow.rewardsString.isBlank()) {
                        currentPendingRow.rewardsString = extraReward
                    }

                    val extraAmount = line.getTextInRange(columns.amountMinX, 9999f)
                    if (extraAmount.isNotBlank() && currentPendingRow.amountString.isBlank()) {
                        currentPendingRow.amountString = extraAmount
                    }

                    currentPendingRow.rawLines.add(line.text)
                }
            }

            // Flush last row of page
            if (currentPendingRow != null) {
                currentPendingRow.toParsedTransaction(serialCounter++, page.pageNumber)?.let {
                    transactions.add(it)
                }
            }
        }

        // Calculate synthetic running balances starting from previous balance
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

    private data class DynamicColumns(
        val dateMinX: Float,
        val dateMaxX: Float,
        val serNoMinX: Float,
        val serNoMaxX: Float,
        val detailsMinX: Float,
        val detailsMaxX: Float,
        val rewardsMinX: Float,
        val rewardsMaxX: Float,
        val amountMinX: Float
    )

    private fun calculateColumnsFromHeader(header: SpatialLine): DynamicColumns {
        val elDate = header.elements.firstOrNull { it.text.equals("Date", ignoreCase = true) }
        val elSerNo = header.elements.firstOrNull { it.text.startsWith("SerNo", ignoreCase = true) }
        val elDetails = header.elements.firstOrNull { it.text.contains("Transaction", ignoreCase = true) }
        val elReward = header.elements.firstOrNull { it.text.contains("Reward", ignoreCase = true) }
        val elAmount = header.elements.firstOrNull { it.text.contains("Amount", ignoreCase = true) }

        val dateX = elDate?.bounds?.x ?: 30f
        val serNoX = elSerNo?.bounds?.x ?: (dateX + 70f)
        val detailsX = elDetails?.bounds?.x ?: (serNoX + 60f)
        val detailsRight = elDetails?.bounds?.right ?: (detailsX + 60f)
        val rewardX = elReward?.bounds?.x ?: 400f
        val rewardRight = elReward?.bounds?.right ?: (rewardX + 40f)
        val amountX = elAmount?.bounds?.x ?: 500f

        val dateMax = (dateX + serNoX) / 2f
        val serNoMax = (serNoX + detailsX) / 2f
        val detailsMax = (detailsRight + rewardX) / 2f
        val rewardsMax = (rewardRight + amountX) / 2f

        return DynamicColumns(
            dateMinX = dateX - 15f,
            dateMaxX = dateMax,
            serNoMinX = dateMax,
            serNoMaxX = serNoMax,
            detailsMinX = serNoMax,
            detailsMaxX = detailsMax,
            rewardsMinX = detailsMax,
            rewardsMaxX = rewardsMax,
            amountMinX = rewardsMax
        )
    }

    private data class MutableTransactionRow(
        val dateString: String,
        val serNoString: String,
        val narrationParts: MutableList<String>,
        var rewardsString: String,
        var amountString: String,
        val rawLines: MutableList<String>
    ) {
        fun toParsedTransaction(serialNo: Int, pageNumber: Int): ParsedTransaction? {
            val dateMatcher = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})").matcher(dateString)
            if (!dateMatcher.find()) return null
            val date = try {
                LocalDate.parse(dateMatcher.group(1), DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            } catch (e: Exception) {
                return null
            }

            val serNoMatcher = Pattern.compile("(\\d{8,15})").matcher(serNoString)
            val referenceNumber = if (serNoMatcher.find()) serNoMatcher.group(1) else null

            val rawNarration = narrationParts.filter { it.isNotBlank() }.joinToString(" ").trim()
            val cleanNarration = cleanMerchantNarration(rawNarration)

            val amtClean = amountString.replace(",", "").trim()
            val isCredit = amtClean.endsWith("CR", ignoreCase = true) || amtClean.contains(" CR")
            val numericAmountStr = amtClean.replace(Regex("[^\\d.]"), "")
            val parsedAmount = numericAmountStr.toDoubleOrNull() ?: return null

            val rewardClean = rewardsString.replace(",", "").trim()
            val rewardPoints = rewardClean.toIntOrNull()

            return ParsedTransaction(
                serialNumber = serialNo,
                date = date,
                referenceNumber = referenceNumber,
                rawNarration = rawNarration,
                cleanNarration = cleanNarration,
                debit = if (!isCredit) parsedAmount else null,
                credit = if (isCredit) parsedAmount else null,
                isCredit = isCredit,
                amount = parsedAmount,
                rewardPoints = rewardPoints,
                runningBalance = null,
                pageNumber = pageNumber,
                rawLine = rawLines.joinToString(" | ")
            )
        }

        private fun cleanMerchantNarration(narration: String): String {
            return narration
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }

    private fun extractAccountHolderName(page: SpatialPage): String {
        for (line in page.lines.take(15)) {
            val leftText = line.getTextInRange(0f, 250f).trim()
            if (leftText.startsWith("MR ") || leftText.startsWith("MS ") || leftText.startsWith("MRS ")) {
                return leftText
            }
        }
        return "UNKNOWN"
    }

    private fun extractRegex(text: String, pattern: Pattern): String? {
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }

    private fun extractDateWithPattern(text: String, pattern: Pattern): LocalDate? {
        val dateStr = extractRegex(text, pattern) ?: return null
        return parseStatementDate(dateStr)
    }

    private fun parseStatementDate(str: String?): LocalDate? {
        if (str.isNullOrBlank()) return null
        return try {
            LocalDate.parse(str.trim(), statementDateFormatter)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun extractLabeledAmount(text: String, pattern: Pattern): Double? {
        val matcher = pattern.matcher(text)
        return if (matcher.find()) {
            val raw = matcher.group(1)?.replace(",", "")?.trim()
            raw?.toDoubleOrNull()
        } else null
    }

    private fun parseRowCurrencyNumbers(lineText: String): List<Double> {
        val matcher = Pattern.compile("`?\\s*([\\d,]+\\.\\d{2})").matcher(lineText)
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
