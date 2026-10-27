package com.joaoeoneves.fintrack.ui.common

import com.joaoeoneves.fintrack.domain.model.CurrencyOption
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
        currency: CurrencyOption,
        locale: Locale,
    ): String = formatAmount(cents, currency, locale).normalizeSpaces()

    @Test
    fun enUs_eur_prefixesSymbol_withGroupingAndTwoDecimals() {
        assertEquals("€2,170.00", format(217_000L, CurrencyOption.EUR, enUs))
    }

    @Test
    fun enUs_usd_negative_hasLeadingMinus() {
        assertEquals("-$1,330.40", format(-133_040L, CurrencyOption.USD, enUs))
    }

    @Test
    fun ptPt_eur_suffixesSymbol_withCommaDecimals() {
        assertEquals("12,34 €", format(1_234L, CurrencyOption.EUR, ptPt))
    }

    @Test
    fun ptPt_eur_largeAmount_usesSpaceGrouping() {
        assertEquals("1 234 567,89 €", format(123_456_789L, CurrencyOption.EUR, ptPt))
    }

    @Test
    fun ptPt_eur_fourDigitAmount_keepsDecimalsAndSymbol() {
        // Don't pin 4-digit grouping: JDK and ICU differ on pt-PT's minimum grouping digits.
        val formatted = format(123_456L, CurrencyOption.EUR, ptPt)

        assertEquals(true, formatted.endsWith(",56 €"))
        assertEquals("123456", formatted.filter { it.isDigit() }.take(6))
    }

    @Test
    fun ptPt_eur_negative_containsMinusAndAmount() {
        val formatted = format(-1_234L, CurrencyOption.EUR, ptPt)

        assertEquals("-12,34 €", formatted)
    }

    @Test
    fun zero_formatsWithCurrencyFractionDigits() {
        assertEquals("€0.00", format(0L, CurrencyOption.EUR, enUs))
        assertEquals("$0.00", format(0L, CurrencyOption.USD, enUs))
        assertEquals("0,00 €", format(0L, CurrencyOption.EUR, ptPt))
        assertEquals("¥0", format(0L, CurrencyOption.JPY, enUs))
    }

    @Test
    fun oneCent_isNotLost() {
        assertEquals("€0.01", format(1L, CurrencyOption.EUR, enUs))
        assertEquals("-€0.01", format(-1L, CurrencyOption.EUR, enUs))
    }

    @Test
    fun jpy_hasNoDecimals_andRoundsHalfUp() {
        assertEquals("¥123", format(12_349L, CurrencyOption.JPY, enUs))
        assertEquals("¥124", format(12_350L, CurrencyOption.JPY, enUs))
        // HALF_EVEN would give ¥122 here; HALF_UP must give ¥123.
        assertEquals("¥123", format(12_250L, CurrencyOption.JPY, enUs))
        assertEquals("¥1,000", format(100_000L, CurrencyOption.JPY, enUs))
    }

    @Test
    fun jpy_negative_roundsHalfUpAwayFromZero() {
        assertEquals("-¥124", format(-12_350L, CurrencyOption.JPY, enUs))
    }

    @Test
    fun formatter_fractionDigits_followCurrency() {
        val eur = currencyFormatter(enUs, CurrencyOption.EUR)
        val jpy = currencyFormatter(enUs, CurrencyOption.JPY)

        assertEquals(2, eur.minimumFractionDigits)
        assertEquals(2, eur.maximumFractionDigits)
        assertEquals(0, jpy.minimumFractionDigits)
        assertEquals(0, jpy.maximumFractionDigits)
    }

    @Test
    fun formatter_roundsHalfUp_forTwoDecimalCurrencies() {
        val eur = currencyFormatter(enUs, CurrencyOption.EUR)

        assertEquals("€1.01", eur.format(BigDecimal("1.005")).normalizeSpaces())
        assertEquals("€1.00", eur.format(BigDecimal("1.004")).normalizeSpaces())
    }

    @Test
    fun formatter_usesSelectedCurrency_notLocaleDefaultCurrency() {
        assertEquals("£12.34", format(1_234L, CurrencyOption.GBP, enUs))
        assertEquals(true, format(1_234L, CurrencyOption.USD, ptPt).startsWith("12,34"))
    }

    @Test
    fun everyCurrency_formatsWithoutThrowing_andContainsDigits() {
        for (currency in CurrencyOption.entries) {
            for (locale in listOf(enUs, ptPt)) {
                val formatted = format(123_456L, currency, locale)
                assertEquals("$currency/$locale -> $formatted", true, formatted.any { it.isDigit() })
            }
        }
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
        val plain = formatAmount(-1_234L, CurrencyOption.EUR, enUs)
        assertEquals("\u2212€12.34", plain.withTypographicMinus())
        assertEquals("\u2212", MINUS_SIGN)
    }

    @Test
    fun formatAmount_staysPlainAsciiMinus() {
        for (locale in listOf(enUs, ptPt)) {
            val formatted = formatAmount(-1_234L, CurrencyOption.EUR, locale)
            assertEquals(true, formatted.contains('-'))
            assertEquals(false, formatted.contains(MINUS_SIGN))
        }
    }
}
