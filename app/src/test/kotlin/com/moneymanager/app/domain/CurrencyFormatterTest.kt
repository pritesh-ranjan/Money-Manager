package com.moneymanager.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyFormatterTest {

    @Test
    fun testIndianNumberFormatting() {
        assertEquals("0.00", CurrencyFormatter.formatIndianNumber(0.0))
        assertEquals("20.00", CurrencyFormatter.formatIndianNumber(20.0))
        assertEquals("1,200.00", CurrencyFormatter.formatIndianNumber(1200.0))
        assertEquals("14,263.85", CurrencyFormatter.formatIndianNumber(14263.85))
        assertEquals("45,461.44", CurrencyFormatter.formatIndianNumber(45461.44))
        assertEquals("1,12,342.37", CurrencyFormatter.formatIndianNumber(112342.37))
        assertEquals("-98,078.52", CurrencyFormatter.formatIndianNumber(-98078.52))
        assertEquals("1,00,00,000.00", CurrencyFormatter.formatIndianNumber(10000000.0))
    }

    @Test
    fun testAmountWithSymbol() {
        assertEquals("₹ 1,12,342.37", CurrencyFormatter.formatAmountWithSymbol(112342.37, "₹", spaceAfterSymbol = true))
        assertEquals("-₹ 98,078.52", CurrencyFormatter.formatAmountWithSymbol(-98078.52, "₹", spaceAfterSymbol = true))
        assertEquals("+₹ 14,263.85", CurrencyFormatter.formatAmountWithSymbol(14263.85, "₹", showPlusForPositive = true, spaceAfterSymbol = true))
        assertEquals("₹0.00", CurrencyFormatter.formatAmountWithSymbol(0.0, "₹", spaceAfterSymbol = false))
    }
}
