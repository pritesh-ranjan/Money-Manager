package com.moneymanager.app.domain.gmail.filter

enum class SupportedBank(val displayName: String, val parserBankId: String?) {
    ICICI("ICICI Bank", "ICICI_AMAZON_PAY_CC"),
    AXIS("Axis Bank", "AXIS_BANK_CC"),
    HDFC("HDFC Bank", "HDFC_SWIGGY_CC"),
    BANK_OF_BARODA("Bank of Baroda", null),
    FEDERAL_BANK("Federal Bank", null)
}

object BankFilterEngine {

    /**
     * Determines whether an email matches at least one supported bank
     * based on sender, subject, and user-configured sync rules.
     */
    fun matchBank(sender: String, subject: String, rules: GmailSyncRules? = null): SupportedBank? {
        val sLower = sender.lowercase()
        val subjLower = subject.lowercase()

        // 1. Check if email matches any custom bank keywords
        if (rules != null && rules.customBankKeywords.isNotEmpty()) {
            val matchesCustom = rules.customBankKeywords.any { kw ->
                kw.isNotBlank() && (sLower.contains(kw.lowercase().trim()) || subjLower.contains(kw.lowercase().trim()))
            }
            if (matchesCustom) {
                // If it matches ICICI/Axis/HDFC/etc. prefer that bank, otherwise pick first enabled bank or HDFC
                val detected = matchBankWithoutRules(sLower, subjLower)
                if (detected != null) return detected
                val firstEnabled = rules.enabledBanks.firstOrNull()?.let { name ->
                    try { SupportedBank.valueOf(name) } catch (_: Exception) { null }
                }
                return firstEnabled ?: SupportedBank.HDFC
            }
        }

        val detectedBank = matchBankWithoutRules(sLower, subjLower) ?: return null

        // Check if this bank is enabled in rules
        if (rules != null && !rules.enabledBanks.contains(detectedBank.name)) {
            return null
        }

        return detectedBank
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

        // 4. Bank of Baroda
        if (sLower.contains("bankofbaroda") || sLower.contains("bobcard") || sLower.contains("bobcards") ||
            sLower.contains("bank of baroda") ||
            subjLower.contains("bank of baroda") || subjLower.contains("bobcard") || subjLower.contains("bob card")
        ) {
            return SupportedBank.BANK_OF_BARODA
        }

        // 5. Federal Bank
        if (sLower.contains("federalbank") || sLower.contains("federal bank") || sLower.contains("fedbank") ||
            subjLower.contains("federal bank") || subjLower.contains("fedbank")
        ) {
            return SupportedBank.FEDERAL_BANK
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
