package com.moneymanager.app.domain.statement

import com.moneymanager.app.domain.statement.core.StatementEntityMapper
import com.moneymanager.app.domain.statement.parsers.axis.AxisBankCreditCardParser
import com.moneymanager.app.domain.statement.parsers.hdfc.HdfcSwiggyCreditCardParser
import com.moneymanager.app.domain.statement.parsers.icici.IciciAmazonPayCreditCardParser
import org.junit.Assert.*
import org.junit.Test
import java.io.InputStream

class StatementEntityMapperTest {

    @Test
    fun testMapAxisHorizonStatement() {
        val stream = javaClass.getResourceAsStream("/fixtures/axis_horizon_golden.pdf")!!
        val result = stream.use { AxisBankCreditCardParser().parse(it) }

        val entities = StatementEntityMapper.toEntities(result)
        assertEquals(21, entities.size)

        val first = entities.first()
        assertEquals("418688******8158_2026-10", first.statementId)
        assertEquals("418688******8158", first.accountOrCardNumberMasked)
        assertEquals("2026-10", first.statementYearMonth)
        assertEquals("AXIS_BANK_CC", first.bankId)
        assertEquals("Axis Bank", first.bankName)
        assertEquals("PRITESH RANJAN", first.accountHolderName)
        assertEquals(25338.35, first.statementTotalDue, 0.01)
        assertEquals(507.00, first.statementMinDue!!, 0.01)
        assertEquals("LOYALTY_POINTS", first.rewardType)
        assertEquals(7686.0, first.rewardClosingBalance!!, 0.01)
        assertTrue(first.isReconciled)

        // Line item check
        assertEquals("418688******8158_2026-10_1", first.uid)
        assertEquals(1, first.serialNumber)
        assertEquals(134.50, first.amount, 0.01)
        assertEquals(134.50, first.debit!!, 0.01)
        assertNull(first.credit)
        assertFalse(first.isCredit)
        assertEquals("BLINKIT,GURGAON", first.cleanNarration)
    }

    @Test
    fun testMapAxisAirtelStatement() {
        val stream = javaClass.getResourceAsStream("/fixtures/axis_airtel_golden.pdf")!!
        val result = stream.use { AxisBankCreditCardParser().parse(it) }

        val entities = StatementEntityMapper.toEntities(result)
        assertEquals(53, entities.size)

        val first = entities.first()
        assertEquals("653036******6930_2026-10", first.statementId)
        assertEquals("653036******6930", first.accountOrCardNumberMasked)
        assertEquals("2026-10", first.statementYearMonth)
        assertEquals(10009.48, first.statementTotalDue, 0.01)
        assertEquals(201.00, first.statementMinDue!!, 0.01)
        assertEquals("IN_STATEMENT_CASHBACK", first.rewardType)
        assertEquals(414.00, first.rewardEarned!!, 0.01)
        assertEquals(240.00, first.rewardRedeemedOrCredited!!, 0.01)
        assertTrue(first.isReconciled)
    }

    @Test
    fun testMapHdfcSwiggyStatement() {
        val stream = javaClass.getResourceAsStream("/fixtures/second_statement_golden.pdf")!!
        val result = stream.use { HdfcSwiggyCreditCardParser().parse(it) }

        val entities = StatementEntityMapper.toEntities(result)
        assertEquals(23, entities.size)

        val first = entities.first()
        assertEquals("526873XXXXXX3453_2026-10", first.statementId)
        assertEquals("526873XXXXXX3453", first.accountOrCardNumberMasked)
        assertEquals("2026-10", first.statementYearMonth)
        assertEquals("HDFC_SWIGGY_CC", first.bankId)
        assertEquals(5177.00, first.statementTotalDue, 0.01)
        assertEquals("IN_STATEMENT_CASHBACK", first.rewardType)
        assertEquals(502.80, first.rewardEarned!!, 0.01)
        assertTrue(first.isReconciled)
    }

    @Test
    fun testMapIciciAmazonPayStatement() {
        val stream = javaClass.getResourceAsStream("/fixtures/icici_amazon_pay_statement_golden.pdf")!!
        val result = stream.use { IciciAmazonPayCreditCardParser().parse(it) }

        val entities = StatementEntityMapper.toEntities(result)
        assertEquals(27, entities.size)

        val first = entities.first()
        assertEquals("4315XXXXXXXX3004_2026-10", first.statementId)
        assertEquals("4315XXXXXXXX3004", first.accountOrCardNumberMasked)
        assertEquals("2026-10", first.statementYearMonth)
        assertEquals("ICICI_AMAZON_PAY_CC", first.bankId)
        assertEquals(20789.41, first.statementTotalDue, 0.01)
        assertEquals("EXTERNAL_WALLET_CASHBACK", first.rewardType)
        assertEquals(776.0, first.rewardEarned!!, 0.01)
        assertTrue(first.isReconciled)
    }
}
