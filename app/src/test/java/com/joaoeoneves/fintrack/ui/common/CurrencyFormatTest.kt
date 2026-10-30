package com.joaoeoneves.fintrack.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

/**
 * Pure JVM tests for [currencyFormatter]/[formatAmount]. Non-breaking spaces (U+00A0, U+202F),
 * which java.text uses as pt-PT's grouping separator and before the suffixed symbol, are
 * normalized to a regular space before comparing.
 */
class CurrencyFormatTest {
    private val enUs: Locale = Locale.US
    private val ptPt: Locale = Locale.forLanguageTag("pt-PT")

    private fun String.normalizeSpaces(): String = replace(' ', ' ').replace(' ', ' ')

    private fun format(
        cents: Long,
        locale: Locale,
    ): String = formatAmount(cents, locale).normalizeSpaces()

    @Test
    fun enUs_eur_prefixesSymbol_withGroupingAndTwoDecimals() {
        assertEquals("€2,170.00", format(217_000L, enUs))
    }

    @Test
    fun ptPt_eur_suffixesSymbol_withCommaDecimals() {
        assertEquals("12,34 €", format(1_234L, ptPt))
    }

    @Test
    fun ptPt_eur_largeAmount_usesSpaceGrouping() {
        assertEquals("1 234 567,89 €", format(123_456_789L, ptPt))
    }

    @Test
    fun ptPt_eur_groupsFromFourDigits_onEveryPlatform() {
        assertEquals("1 234,56 €", format(123_456L, ptPt))
        assertEquals("12 345,67 €", format(1_234_567L, ptPt))
        assertEquals("-1 234,56 €", format(-123_456L, ptPt))
        // Three digits stay ungrouped.
        assertEquals("999,99 €", format(99_999L, ptPt))
    }

    @Test
    fun ptPt_eur_fourDigitGroupingSeparator_isTheLocalesOwn() {
        val raw = formatAmount(123_456L, ptPt)

        assertEquals(
            java.text.DecimalFormatSymbols
                .getInstance(ptPt)
                .groupingSeparator,
            raw[1],
        )
    }

    @Test
    fun enUs_fourDigitGrouping_isUnchanged() {
        assertEquals("€1,234.56", format(123_456L, enUs))
    }

    @Test
    fun ptPt_eur_negative_containsMinusAndAmount() {
        val formatted = format(-1_234L, ptPt)

        assertEquals("-12,34 €", formatted)
    }

    @Test
    fun zero_formatsWithTwoDecimals() {
        assertEquals("€0.00", format(0L, enUs))
        assertEquals("0,00 €", format(0L, ptPt))
    }

    @Test
    fun oneCent_isNotLost() {
        assertEquals("€0.01", format(1L, enUs))
        assertEquals("-€0.01", format(-1L, enUs))
    }

    @Test
    fun formatter_roundsHalfUp_forTwoDecimalCurrencies() {
        val eur = currencyFormatter(enUs)

        assertEquals("€1.01", eur.format(BigDecimal("1.005")).normalizeSpaces())
        assertEquals("€1.00", eur.format(BigDecimal("1.004")).normalizeSpaces())
    }

    @Test
    fun typographicMinus_replacesLeadingHyphen() {
        assertEquals("\u2212€12.34", "-€12.34".withTypographicMinus())
        assertEquals("\u221212,34 €", "-12,34 €".withTypographicMinus())
    }

    @Test
    fun typographicMinus_leavesPositiveUnchanged() {
        assertEquals("€12.34", "€12.34".withTypographicMinus())
        assertEquals("", "".withTypographicMinus())
    }

    @Test
    fun typographicMinus_onlyFirstHyphenChanges() {
        assertEquals("\u2212€1-2", "-€1-2".withTypographicMinus())
    }

    @Test
    fun typographicMinus_appliedToFormattedNegative() {
        val plain = formatAmount(-1_234L, enUs)
        assertEquals("\u2212€12.34", plain.withTypographicMinus())
        assertEquals("\u2212", MINUS_SIGN)
    }

    @Test
    fun formatAmount_staysPlainAsciiMinus() {
        for (locale in listOf(enUs, ptPt)) {
            val formatted = formatAmount(-1_234L, locale)
            assertEquals(true, formatted.contains('-'))
            assertEquals(false, formatted.contains(MINUS_SIGN))
        }
    }

    @Test
    fun currency_isAlwaysEuro_whateverTheLocale() {
        assertEquals("€12.34", format(1_234L, Locale.UK))
        assertEquals("€12.34", format(1_234L, Locale.JAPAN))
        assertEquals("€12.34", format(1_234L, Locale.US))
    }
}
