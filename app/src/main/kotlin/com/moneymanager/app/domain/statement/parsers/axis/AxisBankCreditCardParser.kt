package com.moneymanager.app.domain.statement.parsers.axis

import com.moneymanager.app.domain.statement.core.*
import com.moneymanager.app.domain.statement.pdf.*
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

class AxisBankCreditCardParser(
    private val reconciliationEngine: StatementReconciliationEngine = StatementReconciliationEngine,
    private val duplicateDetector: StatementDuplicateDetector = StatementDuplicateDetector
) : BankStatementParser {

    override val bankId: String = "AXIS_BANK_CC"
    override val displayName: String = "Axis Bank Credit Card"
    override val supportedStatementType: StatementType = StatementType.CREDIT_CARD

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
    private val txnRegex = Pattern.compile("^(\\d{2}/\\d{2}/\\d{4})\\s+(.+?)\\s+([\\d,]+\\.\\d{2})\\s*(Dr|Cr)$")
    private val cardHeaderRegex = Pattern.compile("Card\\s*No:\\s*([0-9*Xx]{16})\\s+Name\\s+(.+)", Pattern.CASE_INSENSITIVE)
    private val cardMaskedRegex = Pattern.compile("\\b([0-9]{4,6}[*Xx]{4,8}[0-9]{4})\\b")

    private val knownCategories = listOf(
        "DEPT STORES",
        "MISC STORE",
        "MEDICAL",
        "MISCELLANEOUS",
        "RESTAURANTS",
        "HOME FURNISHING",
        "ENTERTAINMENT",
        "TRANSPORT",
        "BOOKS AND STATIONERY",
        "FOOD PRODUCTS",
        "UTILITIES",
        "LEATHER GOODS"
    )

    override fun canParse(context: StatementInspectionContext): Boolean {
        val text = context.firstPageText.lowercase(Locale.ENGLISH)
        val hasAxis = text.contains("axis bank") || text.contains("axis")
        val hasCreditCard = text.contains("credit card")
        val hasPaymentSummary = text.contains("payment summary") || text.contains("total payment due")
        return hasAxis && (hasCreditCard || hasPaymentSummary)
    }

    override fun parse(inputStream: InputStream, password: String?): StatementParseResult {
        val pages = SpatialPdfExtractor.extractPages(inputStream, password)
        if (pages.isEmpty()) {
            throw IllegalArgumentException("Empty or unreadable Axis Bank statement PDF.")
        }

        val firstPage = pages[0]
        val metadata = extractMetadata(firstPage, pages)
        val summary = extractSummary(firstPage, pages)
        val rawTransactions = extractTransactions(pages)
        val duplicateGroups = duplicateDetector.findDuplicates(rawTransactions)
        val reconciliation = reconciliationEngine.reconcile(summary, rawTransactions)

        val warnings = mutableListOf<String>()
        if (!reconciliation.isReconciled) {
            warnings.addAll(reconciliation.discrepancies)
        }
        if (duplicateGroups.isNotEmpty()) {
            warnings.add("Detected ${duplicateGroups.size} duplicate transaction groups.")
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

    private fun extractMetadata(firstPage: SpatialPage, allPages: List<SpatialPage>): StatementMetadata {
        var cardholderName = "Unknown"
        var maskedCardNumber = "Unknown"

        // Search for "Card No: ... Name ..." in lines
        for (page in allPages) {
            for (line in page.lines) {
                val cardMatcher = cardHeaderRegex.matcher(line.text)
                if (cardMatcher.find()) {
                    maskedCardNumber = cardMatcher.group(1).trim()
                    cardholderName = cardMatcher.group(2).trim()
                    break
                }
            }
            if (maskedCardNumber != "Unknown") break
        }

        // Fallback for cardholder name from top of page 1
        if (cardholderName == "Unknown" && firstPage.lines.size > 1) {
            val potentialNameLine = firstPage.lines[1].text.trim().replace("\\s+".toRegex(), " ")
            if (potentialNameLine.isNotEmpty() && !potentialNameLine.contains("FLAT", ignoreCase = true) && !potentialNameLine.contains("Credit Card", ignoreCase = true)) {
                cardholderName = potentialNameLine
            }
        }

        // Fallback for card number from limit line
        if (maskedCardNumber == "Unknown") {
            for (line in firstPage.lines) {
                val matcher = cardMaskedRegex.matcher(line.text)
                if (matcher.find()) {
                    maskedCardNumber = matcher.group(1).trim()
                    break
                }
            }
        }

        // Payment Summary line 1: Total Due, Min Due, Statement Period, Due Date, Gen Date
        var billingPeriodStart: LocalDate? = null
        var billingPeriodEnd: LocalDate? = null
        var paymentDueDate: LocalDate? = null
        var statementDate: LocalDate? = null

        val periodRegex = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})\\s*-\\s*(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2}/\\d{2}/\\d{4})")
        for (line in firstPage.lines) {
            val m = periodRegex.matcher(line.text)
            if (m.find()) {
                billingPeriodStart = LocalDate.parse(m.group(1), dateFormatter)
                billingPeriodEnd = LocalDate.parse(m.group(2), dateFormatter)
                paymentDueDate = LocalDate.parse(m.group(3), dateFormatter)
                statementDate = LocalDate.parse(m.group(4), dateFormatter)
                break
            }
        }

        // Credit Limits
        var creditLimit: Double? = null
        var availableCredit: Double? = null
        var cashLimit: Double? = null
        var availableCash: Double? = null

        val limitRegex = Pattern.compile("([0-9*Xx]{16})\\s+([\\d,]+(?:\\.\\d{2})?)\\s+([\\d,]+(?:\\.\\d{2})?)\\s+([\\d,]+(?:\\.\\d{2})?)")
        for (line in firstPage.lines) {
            val m = limitRegex.matcher(line.text)
            if (m.find()) {
                creditLimit = parseAmount(m.group(2))
                availableCredit = parseAmount(m.group(3))
                val cash = parseAmount(m.group(4))
                cashLimit = cash
                availableCash = cash
                break
            }
        }

        return StatementMetadata(
            bankId = bankId,
            bankName = "Axis Bank",
            statementType = supportedStatementType,
            accountOrCardNumberMasked = maskedCardNumber,
            accountHolderName = cardholderName,
            statementDate = statementDate ?: LocalDate.now(),
            paymentDueDate = paymentDueDate,
            billingPeriodStart = billingPeriodStart,
            billingPeriodEnd = billingPeriodEnd,
            creditLimit = creditLimit,
            availableCredit = availableCredit,
            cashLimit = cashLimit,
            availableCash = availableCash
        )
    }

    private fun extractSummary(firstPage: SpatialPage, allPages: List<SpatialPage>): StatementFinancialSummary {
        var minDue: Double? = null
        val minDueRegex = Pattern.compile("^[\\s]*[\\d,]+\\.\\d{2}\\s*(?:Dr|Cr)?\\s+([\\d,]+\\.\\d{2})\\s*(?:Dr|Cr)?")
        for (line in firstPage.lines) {
            if (line.text.contains("Dr") && (line.text.contains("-") || line.text.contains("/"))) {
                val m = minDueRegex.matcher(line.text)
                if (m.find()) {
                    minDue = parseAmount(m.group(1))
                    break
                }
            }
        }

        // Formula row:
        // Previous Balance - Payments - Credits + Purchase + Cash Advance + Other Debit&Charges =Total Payment Due
        // Next value line has 7 numbers:
        // <prevBal> Dr <payments> <credits> <purchases> <cashAdvance> <otherDebits> <totalDue> Dr
        var previousBalance = 0.0
        var payments = 0.0
        var credits = 0.0
        var purchases = 0.0
        var cashAdvance = 0.0
        var otherDebits = 0.0
        var totalAmountDue = 0.0

        val formulaValRegex = Pattern.compile("([\\d,]+\\.\\d{2})\\s*(?:Dr|Cr)?\\s+([\\d,]+\\.\\d{2})\\s+([\\d,]+\\.\\d{2})\\s+([\\d,]+\\.\\d{2})\\s+([\\d,]+\\.\\d{2})\\s+([\\d,]+\\.\\d{2})\\s+([\\d,]+\\.\\d{2})\\s*(?:Dr|Cr)?")
        for (line in firstPage.lines) {
            val m = formulaValRegex.matcher(line.text)
            if (m.find()) {
                previousBalance = parseAmount(m.group(1))
                payments = parseAmount(m.group(2))
                credits = parseAmount(m.group(3))
                purchases = parseAmount(m.group(4))
                cashAdvance = parseAmount(m.group(5))
                otherDebits = parseAmount(m.group(6))
                totalAmountDue = parseAmount(m.group(7))
                break
            }
        }

        val purchasesOrDebits = purchases + cashAdvance + otherDebits
        val paymentsOrCredits = payments + credits

        val rewardSummary = extractRewardSummary(firstPage)

        return StatementFinancialSummary(
            previousBalance = previousBalance,
            purchasesOrDebits = purchasesOrDebits,
            paymentsOrCredits = paymentsOrCredits,
            cashAdvances = cashAdvance,
            totalAmountDue = totalAmountDue,
            minimumAmountDue = minDue,
            rewardSummary = rewardSummary
        )
    }

    private fun extractRewardSummary(firstPage: SpatialPage): StatementRewardSummary? {
        val lines = firstPage.lines

        // 1. Check for Cashback Details (Axis Airtel)
        var cashbackIdx = -1
        for (i in lines.indices) {
            if (lines[i].text.contains("CASHBACK DETAILS", ignoreCase = true)) {
                cashbackIdx = i
                break
            }
        }

        if (cashbackIdx != -1) {
            val cashbackEarnedRegex = Pattern.compile("([\\d,]+(?:\\.\\d{2})?)\\s+([\\d,]+(?:\\.\\d{2})?)")
            for (i in cashbackIdx + 1 until minOf(cashbackIdx + 5, lines.size)) {
                val m = cashbackEarnedRegex.matcher(lines[i].text)
                if (m.find()) {
                    val earned = parseAmount(m.group(1))
                    val credited = parseAmount(m.group(2))
                    return StatementRewardSummary(
                        rewardType = StatementRewardType.IN_STATEMENT_CASHBACK,
                        earned = earned,
                        redeemedOrTransferred = credited,
                        closingBalance = earned,
                        destinationWalletOrProgram = "Airtel Axis Bank Cashback",
                        impactsPayableAmount = true
                    )
                }
            }
        }

        // 2. Check for eDGE Miles Points (Axis Horizon)
        var edgeIdx = -1
        for (i in lines.indices) {
            if (lines[i].text.contains("eDGE", ignoreCase = true) || lines[i].text.contains("POINTS BALANCE", ignoreCase = true)) {
                edgeIdx = i
                break
            }
        }

        if (edgeIdx != -1) {
            val edgeRegex = Pattern.compile("^\\s*(\\d{2,8})\\s+\\d{2}-\\d{2}-\\d{4}")
            for (i in edgeIdx..minOf(edgeIdx + 5, lines.size - 1)) {
                val m = edgeRegex.matcher(lines[i].text)
                if (m.find()) {
                    val points = m.group(1).toDouble()
                    return StatementRewardSummary(
                        rewardType = StatementRewardType.LOYALTY_POINTS,
                        earned = 0.0,
                        closingBalance = points,
                        destinationWalletOrProgram = "Axis eDGE Rewards",
                        impactsPayableAmount = false
                    )
                }
            }
        }

        return null
    }

    private fun extractTransactions(pages: List<SpatialPage>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        var serialNumber = 1

        for (page in pages) {
            var inTxnTable = false
            for (line in page.lines) {
                val text = line.text.trim()
                if (text.contains("DATE") && text.contains("TRANSACTION DETAILS")) {
                    inTxnTable = true
                    continue
                }

                if (inTxnTable) {
                    if (text.contains("End of Statement", ignoreCase = true) ||
                        text.contains("CASHBACK DETAILS", ignoreCase = true) ||
                        text.contains("IMPORTANT MESSAGE", ignoreCase = true) ||
                        text.contains("Finance Charge calculation", ignoreCase = true)
                    ) {
                        inTxnTable = false
                        continue
                    }

                    if (text.startsWith("Card No:", ignoreCase = true)) {
                        continue
                    }

                    val matcher = txnRegex.matcher(text)
                    if (matcher.matches()) {
                        val date = LocalDate.parse(matcher.group(1), dateFormatter)
                        val rawDesc = matcher.group(2).trim()
                        val amount = parseAmount(matcher.group(3))
                        val isCredit = matcher.group(4).equals("Cr", ignoreCase = true)

                        var cleanDesc = rawDesc
                        for (cat in knownCategories) {
                            if (cleanDesc.endsWith(cat, ignoreCase = true)) {
                                cleanDesc = cleanDesc.substring(0, cleanDesc.length - cat.length).trim()
                                break
                            }
                        }

                        var refNo: String? = null
                        if (cleanDesc.startsWith("BBPS PAYMENT RECEIVED - ", ignoreCase = true)) {
                            refNo = cleanDesc.substringAfter(" - ").trim()
                            cleanDesc = "BBPS PAYMENT RECEIVED"
                        }

                        transactions.add(
                            ParsedTransaction(
                                serialNumber = serialNumber++,
                                date = date,
                                referenceNumber = refNo,
                                rawNarration = rawDesc,
                                cleanNarration = cleanDesc,
                                debit = if (!isCredit) amount else null,
                                credit = if (isCredit) amount else null,
                                isCredit = isCredit,
                                amount = amount,
                                pageNumber = page.pageNumber,
                                rawLine = text
                            )
                        )
                    }
                }
            }
        }

        return transactions
    }

    private fun parseAmount(str: String): Double {
        return str.replace(",", "").trim().toDoubleOrNull() ?: 0.0
    }
}
