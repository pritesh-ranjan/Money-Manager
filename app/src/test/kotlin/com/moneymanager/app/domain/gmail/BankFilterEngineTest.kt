package com.moneymanager.app.domain.gmail

import com.moneymanager.app.domain.gmail.filter.BankFilterEngine
import com.moneymanager.app.domain.gmail.filter.SupportedBank
import org.junit.Assert.*
import org.junit.Test

class BankFilterEngineTest {

    @Test
    fun testIciciBankMatching() {
        assertEquals(
            SupportedBank.ICICI,
            BankFilterEngine.matchBank("credit_cards@icicibank.com", "Your ICICI Bank Credit Card e-Statement")
        )
        assertEquals(
            SupportedBank.ICICI,
            BankFilterEngine.matchBank("ICICI Bank Alerts <alerts@icicibankpay.com>", "Monthly Account Statement")
        )
        assertEquals(
            SupportedBank.ICICI,
            BankFilterEngine.matchBank("statements@randombank.com", "Your ICICI Bank Statement for OCT 2026")
        )
    }

    @Test
    fun testAxisBankMatching() {
        assertEquals(
            SupportedBank.AXIS,
            BankFilterEngine.matchBank("ebills@axisbank.com", "Your Axis Bank Credit Card Statement")
        )
        assertEquals(
            SupportedBank.AXIS,
            BankFilterEngine.matchBank("Axis Bank Alerts <notifications@axisbank.co.in>", "Statement for Card ending 8158")
        )
    }

    @Test
    fun testHdfcBankMatching() {
        assertEquals(
            SupportedBank.HDFC,
            BankFilterEngine.matchBank("estatements@hdfcbank.net", "HDFC Bank Card Statement")
        )
        assertEquals(
            SupportedBank.HDFC,
            BankFilterEngine.matchBank("alerts@hdfcbank.com", "Your E-Statement from HDFC Bank")
        )
    }

    @Test
    fun testBankOfBarodaMatching() {
        assertEquals(
            SupportedBank.BANK_OF_BARODA,
            BankFilterEngine.matchBank("statements@bankofbaroda.com", "Bank of Baroda e-Statement")
        )
        assertEquals(
            SupportedBank.BANK_OF_BARODA,
            BankFilterEngine.matchBank("ebill@bobcard.co.in", "BOBCARD Credit Card Statement")
        )
        assertEquals(
            SupportedBank.BANK_OF_BARODA,
            BankFilterEngine.matchBank("BOB Cards <alerts@bobcards.com>", "Monthly Account Statement")
        )
    }

    @Test
    fun testFederalBankMatching() {
        assertEquals(
            SupportedBank.FEDERAL_BANK,
            BankFilterEngine.matchBank("estatement@federalbank.co.in", "Federal Bank Statement for Account ending 1234")
        )
        assertEquals(
            SupportedBank.FEDERAL_BANK,
            BankFilterEngine.matchBank("FedBank Alerts <alerts@fedbank.co.in>", "Your E-Statement is ready")
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
    fun testAdditionalSupportedBanksMatching() {
        // SBI
        assertEquals(
            SupportedBank.SBI,
            BankFilterEngine.matchBank("statements@sbicard.com", "Your SBI Card Statement")
        )
        // Kotak
        assertEquals(
            SupportedBank.KOTAK,
            BankFilterEngine.matchBank("alerts@kotak.com", "Your Kotak Mahindra Bank e-Statement")
        )
        // IDFC
        assertEquals(
            SupportedBank.IDFC,
            BankFilterEngine.matchBank("statements@idfcfirstbank.com", "Monthly Account Statement")
        )
        // OneCard
        assertEquals(
            SupportedBank.ONE_CARD,
            BankFilterEngine.matchBank("statements@getonecard.app", "Your OneCard Statement")
        )
    }

    @Test
    fun testCustomBankMatching() {
        val rules = com.moneymanager.app.domain.gmail.filter.GmailSyncRules(
            customBankKeywords = listOf("DBS Bank", "Jupiter")
        )
        assertEquals(
            "DBS Bank",
            BankFilterEngine.matchCustomBank("statements@dbs.com", "Your e-Statement is ready", rules)
        )
        assertEquals(
            "Jupiter",
            BankFilterEngine.matchCustomBank("alerts@jupiter.money", "Jupiter Monthly Statement", rules)
        )
        assertNull(
            BankFilterEngine.matchCustomBank("promo@unknown.com", "Special offer", rules)
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
}
