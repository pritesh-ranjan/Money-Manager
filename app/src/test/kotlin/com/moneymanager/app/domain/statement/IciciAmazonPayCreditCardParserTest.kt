package com.moneymanager.app.domain.statement

import com.moneymanager.app.domain.statement.core.*
import com.moneymanager.app.domain.statement.parsers.icici.IciciAmazonPayCreditCardParser
import com.moneymanager.app.domain.statement.pdf.SpatialPdfExtractor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.time.LocalDate

class IciciAmazonPayCreditCardParserTest {

    private lateinit var parser: IciciAmazonPayCreditCardParser
    private lateinit var registry: StatementParserRegistry

    @Before
    fun setUp() {
        parser = IciciAmazonPayCreditCardParser()
        registry = StatementParserRegistry()
        registry.register(parser)
    }

    private fun loadGoldenPdfStream(): InputStream {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/icici_amazon_pay_statement_golden.pdf")
            ?: javaClass.getResourceAsStream("/fixtures/icici_amazon_pay_statement_golden.pdf")
        assertNotNull("Golden PDF test fixture must be present in test resources", stream)
        return stream!!
    }

    @Test
    fun testParserCanParseGoldenStatement() {
        loadGoldenPdfStream().use { stream ->
            val context = SpatialPdfExtractor.inspect(stream)
            assertTrue("Expected parser to recognize ICICI Amazon Pay Credit Card statement", parser.canParse(context))
            assertEquals(10, context.totalPages)

            val resolved = registry.findParser(context)
            assertNotNull("Registry must resolve matching parser", resolved)
            assertEquals("ICICI_AMAZON_PAY_CC", resolved?.bankId)
        }
    }

    @Test
    fun testParserRejectsIrrelevantDocument() {
        val irrelevantContext = StatementInspectionContext(
            firstPageText = "HDFC Bank Ltd Savings Account Statement Account No: 123456789",
            totalPages = 3
        )
        assertFalse(parser.canParse(irrelevantContext))
    }

    @Test
    fun testMetadataExtraction() {
        val result = loadGoldenPdfStream().use { stream ->
            parser.parse(stream)
        }

        val meta = result.metadata
        assertEquals("ICICI_AMAZON_PAY_CC", meta.bankId)
        assertEquals("ICICI Bank", meta.bankName)
        assertEquals(StatementType.CREDIT_CARD, meta.statementType)
        assertTrue("Card mask should contain 4315 and end with 3004: ${meta.accountOrCardNumberMasked}",
            meta.accountOrCardNumberMasked.contains("4315") && meta.accountOrCardNumberMasked.endsWith("3004"))
        assertEquals("MR PRITESH RANJAN", meta.accountHolderName)
        assertEquals(LocalDate.of(2026, 10, 12), meta.statementDate)
        assertEquals(LocalDate.of(2026, 10, 30), meta.paymentDueDate)
        assertEquals(LocalDate.of(2026, 9, 13), meta.billingPeriodStart)
        assertEquals(LocalDate.of(2026, 10, 12), meta.billingPeriodEnd)

        // Limits
        assertEquals(840000.0, meta.creditLimit!!, 0.01)
        assertEquals(315210.59, meta.availableCredit!!, 0.01)
        assertEquals(84000.0, meta.cashLimit!!, 0.01)
        assertEquals(33600.0, meta.availableCash!!, 0.01)
    }

    @Test
    fun testFinancialSummaryExtraction() {
        val result = loadGoldenPdfStream().use { stream ->
            parser.parse(stream)
        }

        val summary = result.summary
        assertEquals(8895.11, summary.previousBalance, 0.01)
        assertEquals(24026.39, summary.purchasesOrDebits, 0.01)
        assertEquals(12132.09, summary.paymentsOrCredits, 0.01)
        assertEquals(0.0, summary.cashAdvances, 0.01)
        assertEquals(20789.41, summary.totalAmountDue, 0.01)
        assertEquals(1040.00, summary.minimumAmountDue!!, 0.01)

        // Reward Summary (Type 2: External Wallet Cashback)
        val rewards = summary.rewardSummary
        assertNotNull("Reward summary must be extracted", rewards)
        assertEquals(StatementRewardType.EXTERNAL_WALLET_CASHBACK, rewards?.rewardType)
        assertEquals(776.0, rewards?.earned!!, 0.01)
        assertEquals(776.0, rewards.redeemedOrTransferred!!, 0.01)
        assertEquals("Amazon Pay balance", rewards.destinationWalletOrProgram)
        assertFalse("Amazon Pay cashback should not alter the credit card payable amount", rewards.impactsPayableAmount)
    }

    @Test
    fun testTransactionsExtractionCountAndContent() {
        val result = loadGoldenPdfStream().use { stream ->
            parser.parse(stream)
        }

        val transactions = result.transactions
        assertEquals("Should extract all 27 transactions across pages 1 and 2", 27, transactions.size)

        // First transaction
        val firstTx = transactions[0]
        assertEquals(1, firstTx.serialNumber)
        assertEquals(LocalDate.of(2026, 9, 15), firstTx.date)
        assertEquals("14177726128", firstTx.referenceNumber)
        assertTrue("Narration must contain e-commerce: ${firstTx.cleanNarration}",
            firstTx.cleanNarration.contains("AMAZON PAY IN E COMMERC BANGALORE"))
        assertEquals(35, firstTx.rewardPoints)
        assertFalse(firstTx.isCredit)
        assertEquals(714.26, firstTx.amount, 0.01)
        assertEquals(714.26, firstTx.debit!!, 0.01)
        assertNull(firstTx.credit)
        assertEquals(1, firstTx.pageNumber)

        // BBPS Payment (Credit)
        val bbpsPayment = transactions.firstOrNull { it.referenceNumber == "14188423023" }
        assertNotNull(bbpsPayment)
        assertTrue(bbpsPayment!!.isCredit)
        assertEquals(8895.11, bbpsPayment.credit!!, 0.01)
        assertNull(bbpsPayment.debit)
        assertEquals(0, bbpsPayment.rewardPoints)

        // Refund with negative reward points
        val refundTx = transactions.firstOrNull { it.referenceNumber == "14195802424" }
        assertNotNull(refundTx)
        assertTrue(refundTx!!.isCredit)
        assertEquals(449.00, refundTx.amount, 0.01)
        assertEquals(-22, refundTx.rewardPoints)

        // Multi-line narration stitching check
        val multiLineTx = transactions.firstOrNull { it.referenceNumber == "14208112763" }
        assertNotNull(multiLineTx)
        assertEquals(651.63, multiLineTx!!.amount, 0.01)
        assertEquals(32, multiLineTx.rewardPoints)
        assertTrue("Multi-line narration should end with IN: ${multiLineTx.cleanNarration}",
            multiLineTx.cleanNarration.endsWith("IN"))

        // Page 2 transaction
        val apolloTx = transactions.firstOrNull { it.referenceNumber == "14334792540" }
        assertNotNull(apolloTx)
        assertEquals(2, apolloTx!!.pageNumber)
        assertTrue(apolloTx.cleanNarration.contains("Apollo Pharmacies"))
        assertEquals(1890.74, apolloTx.amount, 0.01)
        assertEquals(18, apolloTx.rewardPoints)
    }

    @Test
    fun testMathematicalReconciliation() {
        val result = loadGoldenPdfStream().use { stream ->
            parser.parse(stream)
        }

        val recon = result.reconciliation
        assertTrue("Reconciliation discrepancies: ${recon.discrepancies.joinToString(", ")}", recon.isReconciled)
        assertTrue("Debits must match summary purchases", recon.debitsMatch)
        assertEquals(24026.39, recon.calculatedDebits, 0.01)
        assertEquals(24026.39, recon.expectedDebits, 0.01)

        assertTrue("Credits must match summary payments", recon.creditsMatch)
        assertEquals(12132.09, recon.calculatedCredits, 0.01)
        assertEquals(12132.09, recon.expectedCredits, 0.01)

        assertTrue("Closing balance must match Total Amount Due", recon.closingBalanceMatch)
        assertEquals(20789.41, recon.calculatedClosingBalance, 0.01)
        assertEquals(20789.41, recon.expectedClosingBalance, 0.01)

        assertTrue("Total reward points sum must match Earned", recon.rewardMatches)
        assertEquals(776.0, recon.calculatedRewardTotal!!, 0.01)
        assertEquals(776.0, recon.expectedRewardTotal!!, 0.01)

        assertTrue("No reconciliation discrepancies expected", recon.discrepancies.isEmpty())
    }

    @Test
    fun testSyntheticRunningBalanceProgression() {
        val result = loadGoldenPdfStream().use { stream ->
            parser.parse(stream)
        }

        val transactions = result.transactions
        // Initial previous balance = 8895.11
        // After first tx (714.26 debit): running balance = 8895.11 + 714.26 = 9609.37
        assertEquals(9609.37, transactions[0].runningBalance!!, 0.01)

        // After all transactions, final running balance should equal Total Amount Due: 20789.41
        val finalTx = transactions.last()
        assertEquals(20789.41, finalTx.runningBalance!!, 0.01)
    }

    @Test
    fun testDuplicateDetection() {
        val result = loadGoldenPdfStream().use { stream ->
            parser.parse(stream)
        }

        // In the golden statement, all 27 transactions have unique serial numbers
        assertTrue("Golden statement should not have accidental duplicate transactions",
            result.duplicateGroups.isEmpty())

        // Test injecting duplicates
        val duplicateList = result.transactions.toMutableList().apply {
            add(result.transactions[0].copy(serialNumber = 99))
        }
        val duplicatesFound = StatementDuplicateDetector.findDuplicates(duplicateList)
        assertEquals(1, duplicatesFound.size)
        assertEquals(2, duplicatesFound[0].count)
    }
}
