package com.moneymanager.app.domain

import java.util.Locale

object CurrencyFormatter {

    /**
     * Formats a double into the Indian numbering system: #,##,##0.00
     * e.g. 112342.37 -> "1,12,342.37"
     *      -98078.52 -> "-98,078.52"
     *      0.0 -> "0.00"
     */
    fun formatIndianNumber(amount: Double, decimals: Int = 2): String {
        val isNegative = amount < 0
        val absAmount = Math.abs(amount)

        val formattedDec = String.format(Locale.US, "%.${decimals}f", absAmount)
        val parts = formattedDec.split(".")
        val integerPart = parts[0]
        val fractionPart = if (parts.size > 1 && decimals > 0) "." + parts[1] else ""

        val formattedInteger = if (integerPart.length <= 3) {
            integerPart
        } else {
            val lastThree = integerPart.substring(integerPart.length - 3)
            val rest = integerPart.substring(0, integerPart.length - 3)
            val sb = StringBuilder()
            var count = 0
            for (i in rest.length - 1 downTo 0) {
                sb.append(rest[i])
                count++
                if (count == 2 && i != 0) {
                    sb.append(",")
                    count = 0
                }
            }
            sb.reverse().append(",").append(lastThree).toString()
        }

        return (if (isNegative) "-" else "") + formattedInteger + fractionPart
    }

    /**
     * Formats an amount with currency symbol.
     * e.g. 112342.37 with "₹" -> "₹ 1,12,342.37"
     *      -98078.52 with "₹" -> "-₹ 98,078.52"
     */
    fun formatAmountWithSymbol(
        amount: Double,
        symbol: String = "₹",
        showPlusForPositive: Boolean = false,
        spaceAfterSymbol: Boolean = true
    ): String {
        val isNegative = amount < 0
        val absFormatted = formatIndianNumber(Math.abs(amount))
        val space = if (spaceAfterSymbol) " " else ""

        return when {
            isNegative -> "-$symbol$space$absFormatted"
            showPlusForPositive && amount > 0 -> "+$symbol$space$absFormatted"
            else -> "$symbol$space$absFormatted"
        }
    }
}
