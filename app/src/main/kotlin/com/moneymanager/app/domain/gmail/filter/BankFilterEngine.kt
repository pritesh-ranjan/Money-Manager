package com.moneymanager.app.domain.gmail.filter

import com.moneymanager.app.domain.gmail.api.GmailMessageDetail

enum class SupportedBank(val displayName: String, val parserBankId: String?) {
    HDFC("HDFC Bank", "HDFC_SWIGGY_CC"),
    ICICI("ICICI Bank", "ICICI_AMAZON_PAY_CC"),
    AXIS("Axis Bank", "AXIS_BANK_CC"),
    SBI("State Bank of India", null),
    KOTAK("Kotak Mahindra Bank", null),
    BANK_OF_BARODA("Bank of Baroda", null),
    FEDERAL_BANK("Federal Bank", null),
    IDFC("IDFC FIRST Bank", null),
    INDUSIND("IndusInd Bank", null),
    RBL("RBL Bank", null),
    YES_BANK("Yes Bank", null),
    ONE_CARD("OneCard", null),
    AU_BANK("AU Small Finance Bank", null),
    AMEX("American Express", null),
    STANDARD_CHARTERED("Standard Chartered", null),
    CANARA("Canara Bank", null),
    PNB("Punjab National Bank", null),
    UNION_BANK("Union Bank of India", null),
    HSBC("HSBC Bank", null),
    CITI("Citibank", null)
}

object BankFilterEngine {

    /**
     * Extracts and validates an email address from a From header.
     * Prevents multiple/nested angle brackets or malformed address injection.
     */
    fun extractEmailAddress(fromHeader: String): String? {
        val trimmed = fromHeader.trim()
        if (trimmed.isEmpty()) return null

        // Check for angle brackets
        val openCount = trimmed.count { it == '<' }
        val closeCount = trimmed.count { it == '>' }
        if (openCount != closeCount || openCount > 1) return null

        val candidate = if (openCount == 1) {
            val start = trimmed.indexOf('<')
            val end = trimmed.indexOf('>')
            if (start >= end) return null
            trimmed.substring(start + 1, end).trim()
        } else {
            trimmed
        }

        // Validate basic email address structure: user@domain
        if (candidate.contains(' ') || candidate.contains('\t') || candidate.contains('\n') || candidate.contains('\r')) {
            return null
        }
        val atCount = candidate.count { it == '@' }
        if (atCount != 1) return null

        val parts = candidate.split('@')
        val local = parts[0].trim()
        val domain = parts[1].trim()
        if (local.isEmpty() || domain.isEmpty()) return null

        return "$local@$domain".lowercase()
    }

    /**
     * Extracts the sender's hostname domain and strictly enforces that it belongs to the
     * official Reserve Bank of India / IDRBT reserved '.bank.in' domain namespace.
     * Rejects commercial (.com/.net) addresses, IP addresses, and suffix-spoofed domains.
     */
    fun extractBankInDomain(fromHeader: String): String? {
        val email = extractEmailAddress(fromHeader) ?: return null
        val parts = email.split('@')
        if (parts.size != 2) return null
        val domain = parts[1].trim().lowercase()
        if (!domain.matches(Regex("^[a-z0-9.-]+$"))) return null

        // Domain must be "bank.in" or end with ".bank.in" (e.g. axis.bank.in, alerts.icici.bank.in)
        if (domain == "bank.in" || domain.endsWith(".bank.in")) {
            return domain
        }
        return null
    }

    /**
     * Verifies that the Gmail message was authenticated cryptographically via Google's receiving
     * infrastructure (mx.google.com), passed DMARC (confirming SPF or DKIM alignment),
     * and that the header.from domain matches the sender's .bank.in domain.
     * Rejects missing, malformed, or ambiguous authentication headers.
     */
    fun verifyAuthenticatedBankSender(message: GmailMessageDetail): Boolean {
        // 1. Never trust display name or ambiguous/multiple From headers
        val fromHeaders = message.getHeaders("From")
        if (fromHeaders.size != 1) return false

        val fromDomain = extractBankInDomain(fromHeaders[0]) ?: return false

        // 2. Retrieve Authentication-Results headers
        val authResultsHeaders = message.getHeaders("Authentication-Results")
        if (authResultsHeaders.isEmpty()) return false

        // 3. Filter for headers from Gmail's trusted receiving infrastructure (mx.google.com)
        val mxGoogleHeaders = authResultsHeaders.filter { header ->
            val stripped = stripComments(header).trim()
            val authServId = stripped.substringBefore(";").trim().lowercase()
            val firstToken = authServId.split(Regex("\\s+")).firstOrNull() ?: ""
            firstToken == "mx.google.com"
        }

        // Must have exactly one trusted mx.google.com authentication header to avoid ambiguous injected headers
        if (mxGoogleHeaders.size != 1) return false
        val headerBody = stripComments(mxGoogleHeaders[0])

        // 4. Require dmarc=pass
        val dmarcMatches = Regex("(?i)\\bdmarc\\s*=\\s*([a-z0-9_-]+)").findAll(headerBody).toList()
        if (dmarcMatches.isEmpty()) return false
        if (dmarcMatches.any { it.groupValues[1].lowercase() != "pass" }) return false

        // 5. Ensure header.from matches the expected .bank.in domain
        val headerFromMatches = Regex("(?i)\\bheader\\.from\\s*=\\s*([a-z0-9.-]+)").findAll(headerBody).toList()
        if (headerFromMatches.isEmpty()) return false

        val dmarcDomain = headerFromMatches.first().groupValues[1].lowercase()
        // header.from domain must also be a .bank.in domain
        if (dmarcDomain != "bank.in" && !dmarcDomain.endsWith(".bank.in")) {
            return false
        }

        // 6. Domain alignment: fromDomain must equal dmarcDomain or be a subdomain of dmarcDomain
        return fromDomain == dmarcDomain || fromDomain.endsWith(".$dmarcDomain")
    }

    private fun stripComments(input: String): String {
        var result = input
        while (result.contains('(') && result.contains(')')) {
            val next = result.replace(Regex("\\([^()]*\\)"), " ")
            if (next == result) break
            result = next
        }
        return result
    }

    /**
     * Determines whether an email matches at least one supported bank
     * based on sender, subject, and user-configured sync rules.
     * Strictly requires the sender to be an official .bank.in domain.
     */
    fun matchBank(sender: String, subject: String, rules: GmailSyncRules? = null): SupportedBank? {
        val bankInDomain = extractBankInDomain(sender) ?: return null
        val subjLower = subject.lowercase()

        val detectedBank = matchBankWithoutRules(bankInDomain, subjLower) ?: return null

        // Check if this bank is enabled in rules
        if (rules != null && !rules.enabledBanks.contains(detectedBank.name)) {
            return null
        }

        return detectedBank
    }

    /**
     * Determines whether an email matches any user-configured custom bank.
     * Strictly requires the sender to be an official .bank.in domain.
     * Returns the matched bank name or null.
     */
    fun matchCustomBank(sender: String, subject: String, rules: GmailSyncRules?): String? {
        if (rules == null || rules.customBankKeywords.isEmpty()) return null
        val bankInDomain = extractBankInDomain(sender) ?: return null
        val combined = "$bankInDomain ${subject.lowercase()}"
        return rules.customBankKeywords.firstOrNull { bankName ->
            val clean = bankName.trim().lowercase()
            if (clean.isBlank()) return@firstOrNull false
            if (combined.contains(clean)) return@firstOrNull true

            // Also check significant tokens (e.g. "dbs" from "DBS Bank", "jupiter" from "Jupiter Bank")
            val tokens = clean.split(Regex("[^a-zA-Z0-9]"))
                .filter { it.length >= 3 && it !in setOf("bank", "card", "cards", "credit", "ltd", "india") }
            tokens.isNotEmpty() && tokens.any { combined.contains(it) }
        }?.trim()
    }

    private fun matchBankWithoutRules(bankInDomain: String, subjLower: String): SupportedBank? {
        // 1. ICICI Bank
        if (bankInDomain.contains("icici") || (bankInDomain.contains("bank") && subjLower.contains("icici"))) {
            return SupportedBank.ICICI
        }

        // 2. Axis Bank
        if (bankInDomain.contains("axis") || (bankInDomain.contains("bank") && subjLower.contains("axis"))) {
            return SupportedBank.AXIS
        }

        // 3. HDFC Bank
        if (bankInDomain.contains("hdfc") || (bankInDomain.contains("bank") && subjLower.contains("hdfc"))) {
            return SupportedBank.HDFC
        }

        // 4. SBI / State Bank of India
        if (bankInDomain.contains("sbi") || bankInDomain.contains("statebank") ||
            (bankInDomain.contains("bank") && (subjLower.contains("sbi") || subjLower.contains("state bank")))
        ) {
            return SupportedBank.SBI
        }

        // 5. Kotak Mahindra Bank
        if (bankInDomain.contains("kotak") || (bankInDomain.contains("bank") && subjLower.contains("kotak"))) {
            return SupportedBank.KOTAK
        }

        // 6. Bank of Baroda
        if (bankInDomain.contains("bankofbaroda") || bankInDomain.contains("baroda") || bankInDomain.contains("bob") ||
            (bankInDomain.contains("bank") && (subjLower.contains("baroda") || subjLower.contains("bobcard") || subjLower.contains("bob card")))
        ) {
            return SupportedBank.BANK_OF_BARODA
        }

        // 7. Federal Bank
        if (bankInDomain.contains("federal") || bankInDomain.contains("fedbank") ||
            (bankInDomain.contains("bank") && (subjLower.contains("federal bank") || subjLower.contains("fedbank")))
        ) {
            return SupportedBank.FEDERAL_BANK
        }

        // 8. IDFC FIRST Bank
        if (bankInDomain.contains("idfc") ||
            (bankInDomain.contains("bank") && (subjLower.contains("idfc first") || subjLower.contains("idfc bank")))
        ) {
            return SupportedBank.IDFC
        }

        // 9. IndusInd Bank
        if (bankInDomain.contains("indusind") ||
            (bankInDomain.contains("bank") && (subjLower.contains("indusind bank") || subjLower.contains("indusind credit card")))
        ) {
            return SupportedBank.INDUSIND
        }

        // 10. RBL Bank
        if (bankInDomain.contains("rbl") || bankInDomain.contains("ratnakar") ||
            (bankInDomain.contains("bank") && (subjLower.contains("rbl bank") || subjLower.contains("ratnakar bank")))
        ) {
            return SupportedBank.RBL
        }

        // 11. Yes Bank
        if (bankInDomain.contains("yes") ||
            (bankInDomain.contains("bank") && subjLower.contains("yes bank"))
        ) {
            return SupportedBank.YES_BANK
        }

        // 12. OneCard
        if (bankInDomain.contains("onecard") || bankInDomain.contains("fpltech") ||
            subjLower.contains("onecard") || subjLower.contains("one card")
        ) {
            return SupportedBank.ONE_CARD
        }

        // 13. AU Small Finance Bank
        if (bankInDomain.contains("aubank") || bankInDomain.split(".").contains("au") ||
            (bankInDomain.contains("bank") && (subjLower.contains("au small finance") || subjLower.contains("au bank")))
        ) {
            return SupportedBank.AU_BANK
        }

        // 14. American Express
        if (bankInDomain.contains("americanexpress") || bankInDomain.contains("aexp") || bankInDomain.contains("amex") ||
            subjLower.contains("american express") || subjLower.contains("amex")
        ) {
            return SupportedBank.AMEX
        }

        // 15. Standard Chartered
        if (bankInDomain.contains("standardchartered") || bankInDomain.split(".").contains("sc") ||
            subjLower.contains("standard chartered") || subjLower.contains("sc bank")
        ) {
            return SupportedBank.STANDARD_CHARTERED
        }

        // 16. Canara Bank
        if (bankInDomain.contains("canara") ||
            (bankInDomain.contains("bank") && subjLower.contains("canara bank"))
        ) {
            return SupportedBank.CANARA
        }

        // 17. Punjab National Bank
        if (bankInDomain.contains("pnb") || bankInDomain.contains("punjab") ||
            (bankInDomain.contains("bank") && (subjLower.contains("punjab national bank") || subjLower.contains("pnb")))
        ) {
            return SupportedBank.PNB
        }

        // 18. Union Bank of India
        if (bankInDomain.contains("union") ||
            (bankInDomain.contains("bank") && subjLower.contains("union bank"))
        ) {
            return SupportedBank.UNION_BANK
        }

        // 19. HSBC Bank
        if (bankInDomain.contains("hsbc") ||
            (bankInDomain.contains("bank") && subjLower.contains("hsbc bank"))
        ) {
            return SupportedBank.HSBC
        }

        // 20. Citibank
        if (bankInDomain.contains("citi") ||
            (bankInDomain.contains("bank") && (subjLower.contains("citibank") || subjLower.contains("citi bank")))
        ) {
            return SupportedBank.CITI
        }

        return null
    }

    /**
     * Checks if the email subject or sender matches any user-configured ignored/skipped keywords.
     */
    fun shouldIgnoreEmail(sender: String, subject: String, rules: GmailSyncRules): Boolean {
        if (rules.ignoredSubjectKeywords.isEmpty()) return false
        val sLower = sender.lowercase()
        val subjLower = subject.lowercase()
        return rules.ignoredSubjectKeywords.any { kw ->
            val kwLower = kw.lowercase().trim()
            kwLower.isNotBlank() && (subjLower.contains(kwLower) || sLower.contains(kwLower))
        }
    }

    /**
     * Checks if the email subject or filename matches user-configured card criteria (if any set).
     */
    fun matchesCardCriteria(subject: String, fileName: String, rules: GmailSyncRules): Boolean {
        if (rules.supportedCardKeywords.isEmpty()) return true
        val combined = "$subject $fileName".lowercase()
        return rules.supportedCardKeywords.any { kw ->
            val kwLower = kw.lowercase().trim()
            kwLower.isNotBlank() && combined.contains(kwLower)
        }
    }

    /**
     * Checks if the email subject or context likely indicates a financial statement, bill, or invoice.
     */
    fun isLikelyStatement(subject: String): Boolean {
        val s = subject.lowercase()
        return s.contains("statement") ||
                s.contains("e-statement") ||
                s.contains("estatement") ||
                s.contains("bill") ||
                s.contains("e-bill") ||
                s.contains("invoice") ||
                s.contains("account summary") ||
                s.contains("credit card")
    }
}
