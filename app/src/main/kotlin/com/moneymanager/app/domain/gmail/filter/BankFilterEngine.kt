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
     * Determines whether an email matches at least one of the 5 supported banks
     * (ICICI, Axis, HDFC, Bank of Baroda, or Federal Bank) based on sender and subject.
     */
    fun matchBank(sender: String, subject: String): SupportedBank? {
        val sLower = sender.lowercase()
        val subjLower = subject.lowercase()

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
