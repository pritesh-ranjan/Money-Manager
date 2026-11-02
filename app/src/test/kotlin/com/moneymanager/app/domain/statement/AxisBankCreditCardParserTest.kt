package com.moneymanager.app.domain.statement

import com.moneymanager.app.domain.statement.core.*
import com.moneymanager.app.domain.statement.parsers.axis.AxisBankCreditCardParser
import com.moneymanager.app.domain.statement.pdf.SpatialPdfExtractor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.time.LocalDate

class AxisBankCreditCardParserTest {

    private lateinit var parser: AxisBankCreditCardParser
    private lateinit var registry: StatementParserRegistry

    @Before
    fun setUp() {
        parser = AxisBankCreditCardParser()
        registry = StatementParserRegistry()
    }

    private fun loadHorizonPdfStream(): InputStream {
        val stream = javaClass.getResourceAsStream("/fixtures/axis_horizon_golden.pdf")
        assertNotNull("Axis Horizon golden PDF fixture must be present", stream)
        return stream!!
    }

    private fun loadAirtelPdfStream(): InputStream {
        val stream = javaClass.getResourceAsStream("/fixtures/axis_airtel_golden.pdf")
        assertNotNull("Axis Airtel golden PDF fixture must be present", stream)
        return stream!!
    }

    @Test
    fun testParserCanParseBothStatementsInRegistry() {
        loadHorizonPdfStream().use { stream ->
            val context = SpatialPdfExtractor.inspect(stream)
            assertTrue("Parser should recognize Axis Horizon statement", parser.canParse(context))
            assertEquals(2, context.totalPages)

            val resolved = registry.findParser(context)
            assertNotNull("Registry must resolve Axis parser for Horizon", resolved)
            assertEquals("AXIS_BANK_CC", resolved?.bankId)
        }

        loadAirtelPdfStream().use { stream ->
            val context = SpatialPdfExtractor.inspect(stream)
            assertTrue("Parser should recognize Axis Airtel statement", parser.canParse(context))
            assertEquals(3, context.totalPages)

            val resolved = registry.findParser(context)
            assertNotNull("Registry must resolve Axis parser for Airtel", resolved)
            assertEquals("AXIS_BANK_CC", resolved?.bankId)
        }
    }

    @Test
    fun testParserRejectsIrrelevantDocument() {
        val irrelevantContext = StatementInspectionContext(
            firstPageText = "HDFC Bank Swiggy Credit Card Statement Account",
            totalPages = 3
        )
        assertFalse(parser.canParse(irrelevantContext))
    }

    // ==========================================
    // 1. AXIS HORIZON TESTS (Traditional Points)
    // ==========================================

    @Test
    fun testHorizonMetadataExtraction() {
        val result = loadHorizonPdfStream().use { parser.parse(it) }
        val meta = result.metadata

        assertEquals("AXIS_BANK_CC", meta.bankId)
        assertEquals("Axis Bank", meta.bankName)
        assertEquals(StatementType.CREDIT_CARD, meta.statementType)
        assertEquals("418688******8158", meta.accountOrCardNumberMasked)
        assertEquals("PRITESH RANJAN", meta.accountHolderName)
        assertEquals(LocalDate.of(2026, 10, 10), meta.statementDate)
        assertEquals(LocalDate.of(2026, 10, 30), meta.paymentDueDate)
        assertEquals(LocalDate.of(2026, 9, 12), meta.billingPeriodStart)
        assertEquals(LocalDate.of(2026, 10, 10), meta.billingPeriodEnd)

        // Limits
        assertEquals(350000.0, meta.creditLimit!!, 0.01)
        assertEquals(324661.65, meta.availableCredit!!, 0.01)
        assertEquals(0.0, meta.cashLimit!!, 0.01)
    }

    @Test
    fun testHorizonFinancialSummaryAndRewards() {
        val result = loadHorizonPdfStream().use { parser.parse(it) }
        val summary = result.summary

        assertEquals(19013.31, summary.previousBalance, 0.01)
        assertEquals(25338.35, summary.purchasesOrDebits, 0.01)
        assertEquals(19013.31, summary.paymentsOrCredits, 0.01)
        assertEquals(0.0, summary.cashAdvances, 0.01)
        assertEquals(25338.35, summary.totalAmountDue, 0.01)
        assertEquals(507.00, summary.minimumAmountDue!!, 0.01)

        // Rewards: Traditional Loyalty Points (Does not alter statement dues)
        val rewards = summary.rewardSummary
        assertNotNull("Loyalty reward summary must be extracted", rewards)
        assertEquals(StatementRewardType.LOYALTY_POINTS, rewards?.rewardType)
        assertEquals(7686.0, rewards?.closingBalance!!, 0.01)
        assertFalse("Traditional points do not impact payable amount", rewards.impactsPayableAmount)
    }

    @Test
    fun testHorizonTransactionsAndReconciliation() {
        val result = loadHorizonPdfStream().use { parser.parse(it) }
        val txns = result.transactions

        // 20 debits + 1 credit = 21 transactions
        assertEquals(21, txns.size)

        val debits = txns.filter { !it.isCredit }
        val credits = txns.filter { it.isCredit }
        assertEquals(20, debits.size)
        assertEquals(1, credits.size)

        // Verify BBPS Payment
        val payment = credits.first()
        assertEquals(LocalDate.of(2026, 9, 30), payment.date)
        assertEquals(19013.31, payment.amount, 0.01)
        assertEquals("BBPS PAYMENT RECEIVED", payment.cleanNarration)
        assertEquals("DP216273JUUIQY0WWG4Q", payment.referenceNumber)

        // Verify Sample Purchases
        val blinkit = debits.first()
        assertEquals(LocalDate.of(2026, 9, 14), blinkit.date)
        assertEquals(134.50, blinkit.amount, 0.01)
        assertEquals("BLINKIT,GURGAON", blinkit.cleanNarration)

        // Exact Reconciliation
        val recon = result.reconciliation
        assertTrue("Axis Horizon statement must reconcile cleanly", recon.isReconciled)
        assertTrue("Debits must match summary purchases", recon.debitsMatch)
        assertTrue("Credits must match summary payments", recon.creditsMatch)
        assertTrue("Closing balance must match Total Due", recon.closingBalanceMatch)
        assertTrue("Reward check must pass", recon.rewardMatches)
        assertEquals(0, recon.discrepancies.size)
    }

    // ==========================================
    // 2. AXIS AIRTEL TESTS (In-Statement Cashback)
    // ==========================================

    @Test
    fun testAirtelMetadataExtraction() {
        val result = loadAirtelPdfStream().use { parser.parse(it) }
        val meta = result.metadata

        assertEquals("AXIS_BANK_CC", meta.bankId)
        assertEquals("Axis Bank", meta.bankName)
        assertEquals(StatementType.CREDIT_CARD, meta.statementType)
        assertEquals("653036******6930", meta.accountOrCardNumberMasked)
        assertEquals("PRITESH RANJAN", meta.accountHolderName)
        assertEquals(LocalDate.of(2026, 10, 12), meta.statementDate)
        assertEquals(LocalDate.of(2026, 11, 1), meta.paymentDueDate)
        assertEquals(LocalDate.of(2026, 9, 14), meta.billingPeriodStart)
        assertEquals(LocalDate.of(2026, 10, 12), meta.billingPeriodEnd)

        // Limits
        assertEquals(350000.0, meta.creditLimit!!, 0.01)
        assertEquals(339990.52, meta.availableCredit!!, 0.01)
        assertEquals(35000.0, meta.cashLimit!!, 0.01)
    }

    @Test
    fun testAirtelFinancialSummaryAndRewards() {
        val result = loadAirtelPdfStream().use { parser.parse(it) }
        val summary = result.summary

        assertEquals(13429.49, summary.previousBalance, 0.01)
        assertEquals(10249.48, summary.purchasesOrDebits, 0.01)
        assertEquals(13669.49, summary.paymentsOrCredits, 0.01) // 13,429.49 payment + 240.00 credit
        assertEquals(0.0, summary.cashAdvances, 0.01)
        assertEquals(10009.48, summary.totalAmountDue, 0.01)
        assertEquals(201.00, summary.minimumAmountDue!!, 0.01)

        // Rewards: In-statement cashback (Directly credits and reduces statement balance)
        val rewards = summary.rewardSummary
        assertNotNull("Cashback reward summary must be extracted", rewards)
        assertEquals(StatementRewardType.IN_STATEMENT_CASHBACK, rewards?.rewardType)
        assertEquals(414.00, rewards?.earned!!, 0.01)
        assertEquals(240.00, rewards?.redeemedOrTransferred!!, 0.01)
        assertTrue("In-statement cashback alters the statement dues", rewards.impactsPayableAmount)
    }

    @Test
    fun testAirtelTransactionsAndReconciliation() {
        val result = loadAirtelPdfStream().use { parser.parse(it) }
        val txns = result.transactions

        // 51 debits + 2 credits = 53 transactions across pages 1 and 2
        assertEquals(53, txns.size)

        val page1Txns = txns.filter { it.pageNumber == 1 }
        val page2Txns = txns.filter { it.pageNumber == 2 }
        assertEquals(28, page1Txns.size)
        assertEquals(25, page2Txns.size)

        val debits = txns.filter { !it.isCredit }
        val credits = txns.filter { it.isCredit }
        assertEquals(51, debits.size)
        assertEquals(2, credits.size)

        // Verify BBPS Payment
        val payment = credits.first { it.cleanNarration == "BBPS PAYMENT RECEIVED" }
        assertEquals(LocalDate.of(2026, 9, 30), payment.date)
        assertEquals(13429.49, payment.amount, 0.01)
        assertEquals("DP2162739EMP06RSY10B", payment.referenceNumber)

        // Verify Cashback Credit Line Item
        val cashbackTx = credits.first { it.rawNarration.contains("CASHBACK CREDIT") }
        assertEquals(LocalDate.of(2026, 10, 8), cashbackTx.date)
        assertEquals(240.00, cashbackTx.amount, 0.01)
        assertTrue(cashbackTx.isCredit)

        // Exact Reconciliation
        val recon = result.reconciliation
        assertTrue("Axis Airtel statement must reconcile cleanly", recon.isReconciled)
        assertTrue("Debits must match summary purchases", recon.debitsMatch)
        assertTrue("Credits must match summary payments", recon.creditsMatch)
        assertTrue("Closing balance must match Total Due", recon.closingBalanceMatch)
        assertTrue("Reward check must pass", recon.rewardMatches)
        assertEquals(0, recon.discrepancies.size)
    }
}
