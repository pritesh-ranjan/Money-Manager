package com.moneymanager.app.domain.statement

import com.moneymanager.app.domain.statement.core.*
import com.moneymanager.app.domain.statement.parsers.hdfc.HdfcSwiggyCreditCardParser
import com.moneymanager.app.domain.statement.pdf.SpatialPdfExtractor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.time.LocalDate

class HdfcSwiggyCreditCardParserTest {

    private lateinit var parser: HdfcSwiggyCreditCardParser
    private lateinit var registry: StatementParserRegistry

    @Before
    fun setUp() {
        parser = HdfcSwiggyCreditCardParser()
        registry = StatementParserRegistry()
    }

    private fun loadGoldenPdfStream(): InputStream {
        val stream = javaClass.getResourceAsStream("/fixtures/second_statement_golden.pdf")
        assertNotNull("Golden PDF test fixture must be present in test resources", stream)
        return stream!!
    }

    @Test
    fun testParserCanParseHdfcSwiggyStatement() {
        loadGoldenPdfStream().use { stream ->
            val context = SpatialPdfExtractor.inspect(stream)
            assertTrue("Expected parser to recognize HDFC Swiggy Credit Card statement", parser.canParse(context))
            assertEquals(3, context.totalPages)

            val resolved = registry.findParser(context)
            assertNotNull("Registry must resolve matching HDFC parser", resolved)
            assertEquals("HDFC_SWIGGY_CC", resolved?.bankId)
        }
    }

    @Test
    fun testParserRejectsIrrelevantDocument() {
        val irrelevantContext = StatementInspectionContext(
            firstPageText = "State Bank of India Savings Account Statement Account No: 123456789",
            totalPages = 2
        )
        assertFalse(parser.canParse(irrelevantContext))
    }

    @Test
    fun testMetadataExtraction() {
        val result = loadGoldenPdfStream().use { stream ->
            parser.parse(stream)
        }

        val meta = result.metadata
        assertEquals("HDFC_SWIGGY_CC", meta.bankId)
        assertEquals("HDFC Bank", meta.bankName)
        assertEquals(StatementType.CREDIT_CARD, meta.statementType)
        assertTrue(
            "Card number should start with 5268: ${meta.accountOrCardNumberMasked}",
            meta.accountOrCardNumberMasked.startsWith("5268")
        )
        assertEquals("PRITESH RANJAN", meta.accountHolderName)
        assertEquals(LocalDate.of(2026, 10, 14), meta.statementDate)
        assertEquals(LocalDate.of(2026, 11, 3), meta.paymentDueDate)
        assertEquals(LocalDate.of(2026, 9, 15), meta.billingPeriodStart)
        assertEquals(LocalDate.of(2026, 10, 14), meta.billingPeriodEnd)

        // Limits
        assertEquals(263000.0, meta.creditLimit!!, 0.01)
        assertEquals(257823.0, meta.availableCredit!!, 0.01)
        assertEquals(105200.0, meta.cashLimit!!, 0.01)
    }

    @Test
    fun testFinancialSummaryExtraction() {
        val result = loadGoldenPdfStream().use { stream ->
            parser.parse(stream)
        }

        val summary = result.summary
        assertEquals(6628.21, summary.previousBalance, 0.01)
        assertEquals(5790.00, summary.purchasesOrDebits, 0.01)
        assertEquals(7240.80, summary.paymentsOrCredits, 0.01)
        assertEquals(0.0, summary.cashAdvances, 0.01)
        assertEquals(5177.00, summary.totalAmountDue, 0.01)
        assertEquals(260.00, summary.minimumAmountDue!!, 0.01)

        // Cashback Summary (Type 1: In-statement cashback)
        val rewards = summary.rewardSummary
        assertNotNull("Reward/Cashback summary must be extracted", rewards)
        assertEquals(StatementRewardType.IN_STATEMENT_CASHBACK, rewards?.rewardType)
        assertEquals(502.80, rewards?.earned!!, 0.01)
        assertTrue("In-statement cashback directly impacts/credits the statement dues", rewards.impactsPayableAmount)
    }

    @Test
    fun testTransactionsExtractionCountAndContent() {
        val result = loadGoldenPdfStream().use { stream ->
            parser.parse(stream)
        }

        val transactions = result.transactions
        assertEquals("Should extract all 23 transactions across pages 1 and 2", 23, transactions.size)

        // First purchase transaction (Debit)
        val firstTx = transactions[0]
        assertEquals(1, firstTx.serialNumber)
        assertEquals(LocalDate.of(2026, 9, 14), firstTx.date)
        assertTrue(firstTx.cleanNarration.contains("Swiggy Instamart"))
        assertFalse(firstTx.isCredit)
        assertEquals(350.00, firstTx.amount, 0.01)
        assertEquals(350.00, firstTx.debit!!, 0.01)
        assertNull(firstTx.credit)
        assertEquals(1, firstTx.pageNumber)

        // First cashback transaction (Credit)
        val cashbackTx = transactions[1]
        assertEquals(LocalDate.of(2026, 9, 16), cashbackTx.date)
        assertTrue(cashbackTx.cleanNarration.contains("10% Swiggy CashBack"))
        assertTrue(cashbackTx.isCredit)
        assertEquals(35.00, cashbackTx.amount, 0.01)
        assertEquals(35.00, cashbackTx.credit!!, 0.01)
        assertNull(cashbackTx.debit)

        // BPPY Payment with Reference Number
        val bppyTx = transactions.firstOrNull { it.cleanNarration.contains("BPPY CC PAYMENT") }
        assertNotNull(bppyTx)
        assertTrue(bppyTx!!.isCredit)
        assertEquals(6628.00, bppyTx.amount, 0.01)
        assertEquals("ST262620083000010056108", bppyTx.referenceNumber)

        // Page 2 Transaction
        val razorpayTx = transactions.firstOrNull { it.cleanNarration.contains("Razorpay Payments") }
        assertNotNull(razorpayTx)
        assertEquals(2, razorpayTx!!.pageNumber)
        assertTrue(razorpayTx.isCredit)
        assertEquals(10.00, razorpayTx.amount, 0.01)

        // Final transaction
        val lastTx = transactions.last()
        assertEquals(23, lastTx.serialNumber)
        assertEquals(LocalDate.of(2026, 10, 13), lastTx.date)
        assertTrue(lastTx.cleanNarration.contains("Swiggy Instamart"))
        assertFalse(lastTx.isCredit)
        assertEquals(662.00, lastTx.amount, 0.01)
    }

    @Test
    fun testMathematicalReconciliation() {
        val result = loadGoldenPdfStream().use { stream ->
            parser.parse(stream)
        }

        val recon = result.reconciliation
        assertTrue("Reconciliation discrepancies: ${recon.discrepancies.joinToString(", ")}", recon.isReconciled)
        assertTrue("Debits must match summary purchases (5790.00)", recon.debitsMatch)
        assertEquals(5790.00, recon.calculatedDebits, 0.01)
        assertEquals(5790.00, recon.expectedDebits, 0.01)

        assertTrue("Credits must match summary payments & cashbacks (7240.80)", recon.creditsMatch)
        assertEquals(7240.80, recon.calculatedCredits, 0.01)
        assertEquals(7240.80, recon.expectedCredits, 0.01)

        // Note: Closing balance: 6628.21 - 7240.80 + 5790.00 = 5177.41 (billed rounded to 5177.00)
        // Within standard rounding tolerance
        assertTrue("Closing balance match within rounding: ${recon.calculatedClosingBalance} vs ${recon.expectedClosingBalance}",
            kotlin.math.abs(recon.calculatedClosingBalance - recon.expectedClosingBalance) < 1.0)
    }

    @Test
    fun testRegistryAutoDetectsBothBanks() {
        // Test ICICI Auto-detection
        val iciciStream = javaClass.getResourceAsStream("/fixtures/icici_amazon_pay_statement_golden.pdf")!!
        val iciciResult = registry.parse(iciciStream)
        assertEquals("ICICI_AMAZON_PAY_CC", iciciResult.metadata.bankId)

        // Test HDFC Auto-detection
        val hdfcStream = javaClass.getResourceAsStream("/fixtures/second_statement_golden.pdf")!!
        val hdfcResult = registry.parse(hdfcStream)
        assertEquals("HDFC_SWIGGY_CC", hdfcResult.metadata.bankId)
    }
}
