package com.moneymanager.app.domain.gmail.filter

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
     * Determines whether an email matches at least one supported bank
     * based on sender, subject, and user-configured sync rules.
     */
    fun matchBank(sender: String, subject: String, rules: GmailSyncRules? = null): SupportedBank? {
        val sLower = sender.lowercase()
        val subjLower = subject.lowercase()

        val detectedBank = matchBankWithoutRules(sLower, subjLower) ?: return null

        // Check if this bank is enabled in rules
        if (rules != null && !rules.enabledBanks.contains(detectedBank.name)) {
            return null
        }

        return detectedBank
    }

    /**
     * Determines whether an email matches any user-configured custom bank.
     * Returns the matched bank name or null.
     */
    fun matchCustomBank(sender: String, subject: String, rules: GmailSyncRules?): String? {
        if (rules == null || rules.customBankKeywords.isEmpty()) return null
        val combined = "${sender.lowercase()} ${subject.lowercase()}"
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

    private fun matchBankWithoutRules(sLower: String, subjLower: String): SupportedBank? {
        // 1. ICICI Bank
        if (sLower.contains("icicibank") || sLower.contains("icici bank") ||
            subjLower.contains("icici")
        ) {
            return SupportedBank.ICICI
        }

        // 2. Axis Bank
        if (sLower.contains("axisbank") || sLower.contains("axis bank") ||
            subjLower.contains("axis bank") || subjLower.contains("axis credit card")
        ) {
            return SupportedBank.AXIS
        }

        // 3. HDFC Bank
        if (sLower.contains("hdfcbank") || sLower.contains("hdfc bank") ||
            subjLower.contains("hdfc")
        ) {
            return SupportedBank.HDFC
        }

        // 4. SBI / State Bank of India
        if (sLower.contains("sbi.co.in") || sLower.contains("sbicard") || sLower.contains("sbicards") ||
            sLower.contains("statebankofindia") ||
            subjLower.contains("sbi card") || subjLower.contains("sbicard") ||
            subjLower.contains("state bank of india") || subjLower.contains("sbi credit card")
        ) {
            return SupportedBank.SBI
        }

        // 5. Kotak Mahindra Bank
        if (sLower.contains("kotak.com") || sLower.contains("kotakbank") || sLower.contains("kotak") ||
            subjLower.contains("kotak mahindra") || subjLower.contains("kotak bank") || subjLower.contains("kotak credit card")
        ) {
            return SupportedBank.KOTAK
        }

        // 6. Bank of Baroda
        if (sLower.contains("bankofbaroda") || sLower.contains("bobcard") || sLower.contains("bobcards") ||
            sLower.contains("bank of baroda") ||
            subjLower.contains("bank of baroda") || subjLower.contains("bobcard") || subjLower.contains("bob card")
        ) {
            return SupportedBank.BANK_OF_BARODA
        }

        // 7. Federal Bank
        if (sLower.contains("federalbank") || sLower.contains("federal bank") || sLower.contains("fedbank") ||
            subjLower.contains("federal bank") || subjLower.contains("fedbank")
        ) {
            return SupportedBank.FEDERAL_BANK
        }

        // 8. IDFC FIRST Bank
        if (sLower.contains("idfcfirstbank") || sLower.contains("idfcbank") ||
            subjLower.contains("idfc first") || subjLower.contains("idfc bank")
        ) {
            return SupportedBank.IDFC
        }

        // 9. IndusInd Bank
        if (sLower.contains("indusind.com") || sLower.contains("indusindbank") ||
            subjLower.contains("indusind bank") || subjLower.contains("indusind credit card")
        ) {
            return SupportedBank.INDUSIND
        }

        // 10. RBL Bank
        if (sLower.contains("rblbank.com") || sLower.contains("rblbank") ||
            subjLower.contains("rbl bank") || subjLower.contains("ratnakar bank")
        ) {
            return SupportedBank.RBL
        }

        // 11. Yes Bank
        if (sLower.contains("yesbank.in") || sLower.contains("yesbank") ||
            subjLower.contains("yes bank")
        ) {
            return SupportedBank.YES_BANK
        }

        // 12. OneCard
        if (sLower.contains("getonecard.app") || sLower.contains("onecard") || sLower.contains("fpltech") ||
            subjLower.contains("onecard") || subjLower.contains("one card")
        ) {
            return SupportedBank.ONE_CARD
        }

        // 13. AU Small Finance Bank
        if (sLower.contains("aubank.in") || sLower.contains("aubank") ||
            subjLower.contains("au small finance") || subjLower.contains("au bank")
        ) {
            return SupportedBank.AU_BANK
        }

        // 14. American Express
        if (sLower.contains("americanexpress") || sLower.contains("aexp.com") || sLower.contains("amex") ||
            subjLower.contains("american express") || subjLower.contains("amex")
        ) {
            return SupportedBank.AMEX
        }

        // 15. Standard Chartered
        if (sLower.contains("sc.com") || sLower.contains("standardchartered") ||
            subjLower.contains("standard chartered") || subjLower.contains("sc bank")
        ) {
            return SupportedBank.STANDARD_CHARTERED
        }

        // 16. Canara Bank
        if (sLower.contains("canarabank.com") || sLower.contains("canarabank") ||
            subjLower.contains("canara bank")
        ) {
            return SupportedBank.CANARA
        }

        // 17. Punjab National Bank
        if (sLower.contains("pnbindia.in") || sLower.contains("pnb.co.in") ||
            subjLower.contains("punjab national bank") || subjLower.contains("pnb")
        ) {
            return SupportedBank.PNB
        }

        // 18. Union Bank of India
        if (sLower.contains("unionbankofindia") || sLower.contains("unionbank") ||
            subjLower.contains("union bank")
        ) {
            return SupportedBank.UNION_BANK
        }

        // 19. HSBC Bank
        if (sLower.contains("hsbc.co.in") || sLower.contains("hsbc.com") ||
            subjLower.contains("hsbc bank")
        ) {
            return SupportedBank.HSBC
        }

        // 20. Citibank
        if (sLower.contains("citibank.com") || sLower.contains("citicorp") ||
            subjLower.contains("citibank") || subjLower.contains("citi bank")
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
