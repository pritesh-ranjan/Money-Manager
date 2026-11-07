package com.moneymanager.app.domain.gmail

import com.moneymanager.app.domain.gmail.api.GmailHeader
import com.moneymanager.app.domain.gmail.api.GmailMessageDetail
import com.moneymanager.app.domain.gmail.filter.BankFilterEngine
import com.moneymanager.app.domain.gmail.filter.SupportedBank
import org.junit.Assert.*
import org.junit.Test

class BankFilterEngineTest {

    @Test
    fun testIciciBankMatching() {
        assertEquals(
            SupportedBank.ICICI,
            BankFilterEngine.matchBank("credit_cards@icici.bank.in", "Your ICICI Bank Credit Card e-Statement")
        )
        assertEquals(
            SupportedBank.ICICI,
            BankFilterEngine.matchBank("ICICI Bank Alerts <alerts@icici.bank.in>", "Monthly Account Statement")
        )
        assertEquals(
            SupportedBank.ICICI,
            BankFilterEngine.matchBank("statements@cards.bank.in", "Your ICICI Bank Statement for OCT 2026")
        )
    }

    @Test
    fun testAxisBankMatching() {
        assertEquals(
            SupportedBank.AXIS,
            BankFilterEngine.matchBank("ebills@axis.bank.in", "Your Axis Bank Credit Card Statement")
        )
        assertEquals(
            SupportedBank.AXIS,
            BankFilterEngine.matchBank("Axis Bank Alerts <notifications@axis.bank.in>", "Statement for Card ending 8158")
        )
    }

    @Test
    fun testHdfcBankMatching() {
        assertEquals(
            SupportedBank.HDFC,
            BankFilterEngine.matchBank("estatements@hdfc.bank.in", "HDFC Bank Card Statement")
        )
        assertEquals(
            SupportedBank.HDFC,
            BankFilterEngine.matchBank("alerts@hdfc.bank.in", "Your E-Statement from HDFC Bank")
        )
    }

    @Test
    fun testBankOfBarodaMatching() {
        assertEquals(
            SupportedBank.BANK_OF_BARODA,
            BankFilterEngine.matchBank("statements@bankofbaroda.bank.in", "Bank of Baroda e-Statement")
        )
        assertEquals(
            SupportedBank.BANK_OF_BARODA,
            BankFilterEngine.matchBank("ebill@bobcard.bank.in", "BOBCARD Credit Card Statement")
        )
        assertEquals(
            SupportedBank.BANK_OF_BARODA,
            BankFilterEngine.matchBank("BOB Cards <alerts@bob.bank.in>", "Monthly Account Statement")
        )
    }

    @Test
    fun testFederalBankMatching() {
        assertEquals(
            SupportedBank.FEDERAL_BANK,
            BankFilterEngine.matchBank("estatement@federal.bank.in", "Federal Bank Statement for Account ending 1234")
        )
        assertEquals(
            SupportedBank.FEDERAL_BANK,
            BankFilterEngine.matchBank("FedBank Alerts <alerts@fedbank.bank.in>", "Your E-Statement is ready")
        )
    }

    @Test
    fun testNonMatchingEmailsReturnNull() {
        assertNull(BankFilterEngine.matchBank("marketing@amazon.in", "Big Billion Days are here"))
        assertNull(BankFilterEngine.matchBank("no-reply@uber.com", "Your trip with Uber"))
        assertNull(BankFilterEngine.matchBank("support@netflix.com", "Your Netflix membership has been renewed"))
        assertNull(BankFilterEngine.matchBank("swiggy@orders.com", "Your Swiggy order delivery confirmation"))
    }

    @Test
    fun testCommercialAndSpoofedDomainsRejected() {
        // Commercial domains (.com, .net, etc.) must be rejected
        assertNull(BankFilterEngine.matchBank("alerts@icicibank.com", "ICICI Bank Statement"))
        assertNull(BankFilterEngine.matchBank("alerts@hdfcbank.net", "HDFC Statement"))
        assertNull(BankFilterEngine.matchBank("ebills@axisbank.com", "Axis Bank Statement"))

        // Display name spoofing must be rejected
        assertNull(BankFilterEngine.matchBank("\"alerts@hdfc.bank.in\" <attacker@evil.com>", "HDFC Statement"))

        // Suffix spoofing (.bank.in.evil.com) must be rejected
        assertNull(BankFilterEngine.matchBank("alerts@hdfc.bank.in.evil.com", "HDFC Statement"))
        assertNull(BankFilterEngine.matchBank("alerts@fakebank.in", "Bank Statement"))
    }

    @Test
    fun testAdditionalSupportedBanksMatching() {
        // SBI
        assertEquals(
            SupportedBank.SBI,
            BankFilterEngine.matchBank("statements@sbi.bank.in", "Your SBI Card Statement")
        )
        // Kotak
        assertEquals(
            SupportedBank.KOTAK,
            BankFilterEngine.matchBank("alerts@kotak.bank.in", "Your Kotak Mahindra Bank e-Statement")
        )
        // IDFC
        assertEquals(
            SupportedBank.IDFC,
            BankFilterEngine.matchBank("statements@idfc.bank.in", "Monthly Account Statement")
        )
        // OneCard
        assertEquals(
            SupportedBank.ONE_CARD,
            BankFilterEngine.matchBank("statements@onecard.bank.in", "Your OneCard Statement")
        )
    }

    @Test
    fun testCustomBankMatching() {
        val rules = com.moneymanager.app.domain.gmail.filter.GmailSyncRules(
            customBankKeywords = listOf("DBS Bank", "Jupiter")
        )
        assertEquals(
            "DBS Bank",
            BankFilterEngine.matchCustomBank("statements@dbs.bank.in", "Your e-Statement is ready", rules)
        )
        assertEquals(
            "Jupiter",
            BankFilterEngine.matchCustomBank("alerts@jupiter.bank.in", "Jupiter Monthly Statement", rules)
        )
        assertNull(
            BankFilterEngine.matchCustomBank("promo@unknown.bank.in", "Special offer", rules)
        )
        assertNull(
            BankFilterEngine.matchCustomBank("statements@dbs.com", "Your e-Statement is ready", rules)
        )
    }

    @Test
    fun testIsLikelyStatement() {
        assertTrue(BankFilterEngine.isLikelyStatement("Your Monthly e-Statement for October 2026"))
        assertTrue(BankFilterEngine.isLikelyStatement("Axis Bank Credit Card Statement"))
        assertTrue(BankFilterEngine.isLikelyStatement("HDFC Bank E-Bill for Card"))
        assertTrue(BankFilterEngine.isLikelyStatement("Account Summary & Invoice"))
        assertFalse(BankFilterEngine.isLikelyStatement("Special discount offer just for you"))
        assertFalse(BankFilterEngine.isLikelyStatement("OTP for logging in"))
    }

    @Test
    fun testVerifyAuthenticatedBankSender_ValidDmarcPass() {
        val msg = createMessage(
            from = "alerts@axis.bank.in",
            authResults = listOf(
                "mx.google.com; dkim=pass header.i=@axis.bank.in; dmarc=pass (p=REJECT sp=REJECT dis=NONE) header.from=axis.bank.in"
            )
        )
        assertTrue(BankFilterEngine.verifyAuthenticatedBankSender(msg))
    }

    @Test
    fun testVerifyAuthenticatedBankSender_SubdomainAlignmentPass() {
        val msg = createMessage(
            from = "Credit Card Alerts <cards.alerts@axis.bank.in>",
            authResults = listOf(
                "mx.google.com; dkim=pass; dmarc=pass header.from=axis.bank.in"
            )
        )
        assertTrue(BankFilterEngine.verifyAuthenticatedBankSender(msg))
    }

    @Test
    fun testVerifyAuthenticatedBankSender_RejectsNonBankInDomain() {
        val msg = createMessage(
            from = "alerts@axisbank.com",
            authResults = listOf(
                "mx.google.com; dkim=pass; dmarc=pass header.from=axisbank.com"
            )
        )
        assertFalse("Must reject non-.bank.in domains", BankFilterEngine.verifyAuthenticatedBankSender(msg))
    }

    @Test
    fun testVerifyAuthenticatedBankSender_RejectsDmarcFailOrNone() {
        val msgFail = createMessage(
            from = "alerts@axis.bank.in",
            authResults = listOf(
                "mx.google.com; dkim=fail; dmarc=fail (p=REJECT dis=REJECT) header.from=axis.bank.in"
            )
        )
        assertFalse(BankFilterEngine.verifyAuthenticatedBankSender(msgFail))

        val msgNone = createMessage(
            from = "alerts@axis.bank.in",
            authResults = listOf(
                "mx.google.com; dmarc=none header.from=axis.bank.in"
            )
        )
        assertFalse(BankFilterEngine.verifyAuthenticatedBankSender(msgNone))
    }

    @Test
    fun testVerifyAuthenticatedBankSender_RejectsMissingOrForeignAuthResults() {
        val msgNoAuth = createMessage(from = "alerts@axis.bank.in", authResults = emptyList())
        assertFalse(BankFilterEngine.verifyAuthenticatedBankSender(msgNoAuth))

        val msgForeign = createMessage(
            from = "alerts@axis.bank.in",
            authResults = listOf(
                "mail.attacker.com; dmarc=pass header.from=axis.bank.in"
            )
        )
        assertFalse(BankFilterEngine.verifyAuthenticatedBankSender(msgForeign))
    }

    @Test
    fun testVerifyAuthenticatedBankSender_RejectsAmbiguousMultipleAuthResults() {
        val msg = createMessage(
            from = "alerts@axis.bank.in",
            authResults = listOf(
                "mx.google.com; dmarc=pass header.from=axis.bank.in",
                "mx.google.com; dmarc=fail header.from=axis.bank.in"
            )
        )
        assertFalse("Multiple ambiguous mx.google.com headers must be rejected", BankFilterEngine.verifyAuthenticatedBankSender(msg))
    }

    @Test
    fun testVerifyAuthenticatedBankSender_RejectsAmbiguousMultipleFromHeaders() {
        val msg = GmailMessageDetail(
            id = "msg1",
            threadId = "t1",
            internalDate = 1000L,
            headers = listOf(
                GmailHeader("From", "alerts@axis.bank.in"),
                GmailHeader("From", "attacker@evil.com"),
                GmailHeader("Authentication-Results", "mx.google.com; dmarc=pass header.from=axis.bank.in")
            ),
            attachments = emptyList()
        )
        assertFalse("Multiple From headers must be rejected", BankFilterEngine.verifyAuthenticatedBankSender(msg))
    }

    @Test
    fun testVerifyAuthenticatedBankSender_RejectsMismatchedDomain() {
        val msg = createMessage(
            from = "alerts@icici.bank.in",
            authResults = listOf(
                "mx.google.com; dmarc=pass header.from=axis.bank.in"
            )
        )
        assertFalse("Mismatched sender and DMARC header.from must be rejected", BankFilterEngine.verifyAuthenticatedBankSender(msg))
    }

    private fun createMessage(from: String, authResults: List<String>): GmailMessageDetail {
        val headers = mutableListOf(GmailHeader("From", from))
        authResults.forEach { headers.add(GmailHeader("Authentication-Results", it)) }
        return GmailMessageDetail(
            id = "msg_test",
            threadId = "t1",
            internalDate = 1000L,
            headers = headers,
            attachments = emptyList()
        )
    }
}
