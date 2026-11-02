package com.moneymanager.app.data

import com.moneymanager.app.domain.statement.parsers.axis.AxisBankCreditCardParser
import com.moneymanager.app.domain.statement.parsers.hdfc.HdfcSwiggyCreditCardParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StatementRepositoryTest {

    private lateinit var fakeDao: FakeStatementDao
    private lateinit var repository: StatementRepository

    @Before
    fun setUp() {
        fakeDao = FakeStatementDao()
        repository = StatementRepository(fakeDao)
    }

    @Test
    fun testImportAndQueryByCardAndMonth() = runBlocking {
        val horizonStream = javaClass.getResourceAsStream("/fixtures/axis_horizon_golden.pdf")!!
        val horizonResult = horizonStream.use { AxisBankCreditCardParser().parse(it) }

        val airtelStream = javaClass.getResourceAsStream("/fixtures/axis_airtel_golden.pdf")!!
        val airtelResult = airtelStream.use { AxisBankCreditCardParser().parse(it) }

        // Import both into the single table
        val horizonStmtId = repository.importStatement(horizonResult)
        val airtelStmtId = repository.importStatement(airtelResult)

        assertEquals("418688******8158_2026-10", horizonStmtId)
        assertEquals("653036******6930_2026-10", airtelStmtId)

        // Total rows in table = 21 + 53 = 74
        assertEquals(74, fakeDao.transactionsMap.size)

        // Query Horizon by (cardMasked, yearMonth)
        val horizonTxns = repository.getTransactionsByCardAndMonthRaw("418688******8158", "2026-10")
        assertEquals(21, horizonTxns.size)
        assertTrue(horizonTxns.all { it.statementId == "418688******8158_2026-10" })
        assertTrue(horizonTxns.all { it.bankId == "AXIS_BANK_CC" })

        // Query Airtel by (cardMasked, yearMonth)
        val airtelTxns = repository.getTransactionsByCardAndMonthRaw("653036******6930", "2026-10")
        assertEquals(53, airtelTxns.size)
        assertTrue(airtelTxns.all { it.statementId == "653036******6930_2026-10" })

        // Query non-existent card or month
        val emptyTxns = repository.getTransactionsByCardAndMonthRaw("418688******8158", "2026-09")
        assertTrue(emptyTxns.isEmpty())

        // Check existence
        assertTrue(repository.hasStatement("418688******8158_2026-10"))
        assertTrue(repository.hasStatementForMonth("418688******8158", "2026-10"))
        assertFalse(repository.hasStatementForMonth("418688******8158", "2026-09"))
    }

    @Test
    fun testIdempotentReimportDoesNotDuplicate() = runBlocking {
        val stream1 = javaClass.getResourceAsStream("/fixtures/second_statement_golden.pdf")!!
        val hdfcResult1 = stream1.use { HdfcSwiggyCreditCardParser().parse(it) }

        val stmtId1 = repository.importStatement(hdfcResult1)
        assertEquals("526873XXXXXX3453_2026-10", stmtId1)
        assertEquals(23, fakeDao.transactionsMap.size)

        // Re-import the exact same statement
        val stream2 = javaClass.getResourceAsStream("/fixtures/second_statement_golden.pdf")!!
        val hdfcResult2 = stream2.use { HdfcSwiggyCreditCardParser().parse(it) }

        val stmtId2 = repository.importStatement(hdfcResult2)
        assertEquals(stmtId1, stmtId2)

        // Count must remain 23, NOT 46!
        assertEquals(23, fakeDao.transactionsMap.size)
    }

    @Test
    fun testSummariesAggregationAcrossCards() = runBlocking {
        val horizonStream = javaClass.getResourceAsStream("/fixtures/axis_horizon_golden.pdf")!!
        val horizonResult = horizonStream.use { AxisBankCreditCardParser().parse(it) }
        repository.importStatement(horizonResult)

        val airtelStream = javaClass.getResourceAsStream("/fixtures/axis_airtel_golden.pdf")!!
        val airtelResult = airtelStream.use { AxisBankCreditCardParser().parse(it) }
        repository.importStatement(airtelResult)

        val summaries = repository.getStatementSummariesRaw()
        assertEquals(2, summaries.size)

        val airtelSummary = summaries.first { it.statementId == "653036******6930_2026-10" }
        assertEquals(53, airtelSummary.transactionCount)
        assertEquals(10249.48, airtelSummary.totalDebits, 0.01)
        assertEquals(13669.49, airtelSummary.totalCredits, 0.01)
        assertEquals(10009.48, airtelSummary.statementTotalDue, 0.01)
        assertTrue(airtelSummary.isReconciled)

        val horizonSummary = summaries.first { it.statementId == "418688******8158_2026-10" }
        assertEquals(21, horizonSummary.transactionCount)
        assertEquals(25338.35, horizonSummary.totalDebits, 0.01)
        assertEquals(19013.31, horizonSummary.totalCredits, 0.01)
        assertEquals(25338.35, horizonSummary.statementTotalDue, 0.01)
        assertTrue(horizonSummary.isReconciled)
    }

    @Test
    fun testDeleteStatement() = runBlocking {
        val horizonStream = javaClass.getResourceAsStream("/fixtures/axis_horizon_golden.pdf")!!
        repository.importStatement(horizonStream.use { AxisBankCreditCardParser().parse(it) })

        val airtelStream = javaClass.getResourceAsStream("/fixtures/axis_airtel_golden.pdf")!!
        repository.importStatement(airtelStream.use { AxisBankCreditCardParser().parse(it) })

        assertEquals(74, fakeDao.transactionsMap.size)

        repository.deleteStatement("418688******8158_2026-10")

        assertEquals(53, fakeDao.transactionsMap.size)
        assertFalse(repository.hasStatement("418688******8158_2026-10"))
        assertTrue(repository.hasStatement("653036******6930_2026-10"))
    }
}
