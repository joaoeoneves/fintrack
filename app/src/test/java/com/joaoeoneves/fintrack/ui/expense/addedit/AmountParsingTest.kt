package com.joaoeoneves.fintrack.ui.expense.addedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure function [parseAmountCents].
 */
class AmountParsingTest {
    @Test
    fun validDecimal_withTwoDecimalPlaces_parsesExactCents() {
        val result = parseAmountCents("12.50")

        assertEquals(1_250L, result.getOrThrow())
    }

    @Test
    fun validWholeNumber_parsesAsWholeCents() {
        val result = parseAmountCents("12")

        assertEquals(1_200L, result.getOrThrow())
    }

    @Test
    fun zero_isRejected() {
        val result = parseAmountCents("0")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ArithmeticException)
    }

    @Test
    fun zeroWithDecimals_isRejected() {
        val result = parseAmountCents("0.00")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ArithmeticException)
    }

    @Test
    fun negativeAmount_isRejected() {
        val result = parseAmountCents("-5")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ArithmeticException)
    }

    @Test
    fun negativeDecimalAmount_isRejected() {
        val result = parseAmountCents("-5.25")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ArithmeticException)
    }

    @Test
    fun blank_isRejected() {
        val result = parseAmountCents("")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NumberFormatException)
    }

    @Test
    fun whitespaceOnly_isRejected() {
        val result = parseAmountCents("   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NumberFormatException)
    }

    @Test
    fun garbage_isRejected() {
        val result = parseAmountCents("abc")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NumberFormatException)
    }

    @Test
    fun partiallyNumericGarbage_isRejected() {
        val result = parseAmountCents("12.5a")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NumberFormatException)
    }

    @Test
    fun moreThanTwoDecimalPlaces_roundsHalfUp_roundsDown() {
        // 12.504 -> 1250.4 cents -> HALF_UP rounds to 1250 (down, since .4 < .5)
        val result = parseAmountCents("12.504")

        assertEquals(1_250L, result.getOrThrow())
    }

    @Test
    fun moreThanTwoDecimalPlaces_roundsHalfUp_roundsUp() {
        // 12.505 -> 1250.5 cents -> HALF_UP rounds to 1251 (up, since .5 rounds away from zero)
        val result = parseAmountCents("12.505")

        assertEquals(1_251L, result.getOrThrow())
    }

    @Test
    fun leadingAndTrailingWhitespace_isTrimmedBeforeParsing() {
        val result = parseAmountCents("  12.50  ")

        assertEquals(1_250L, result.getOrThrow())
    }

    @Test
    fun verySmallPositiveAmount_roundsUpToOneCent() {
        // 0.004 rounds HALF_UP to 0.00 cents, which is then rejected as non-positive.
        val result = parseAmountCents("0.004")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ArithmeticException)
    }

    @Test
    fun verySmallPositiveAmount_roundsUpToOneCent_whenAtHalfCentBoundary() {
        // 0.005 rounds HALF_UP to 0.01 -> 1 cent, which is accepted as positive.
        val result = parseAmountCents("0.005")

        assertEquals(1L, result.getOrThrow())
    }

    @Test
    fun leadingPlusSign_isAccepted() {
        val result = parseAmountCents("+12.50")

        assertTrue(result.isSuccess)
        assertEquals(1_250L, result.getOrThrow())
    }

    @Test
    fun largeAmount_parsesCorrectly() {
        val result = parseAmountCents("1000000.99")

        assertEquals(100_000_099L, result.getOrThrow())
    }

    // ---- comma decimal separator ----

    @Test
    fun commaDecimal_parsesExactCents() {
        assertEquals(1_250L, parseAmountCents("12,50").getOrThrow())
    }

    @Test
    fun commaDecimal_withSurroundingWhitespace_isTrimmed() {
        assertEquals(1_250L, parseAmountCents(" 12,50 ").getOrThrow())
    }

    @Test
    fun trailingCommaWithNoFraction_parsesAsWholeAmount() {
        assertEquals(1_200L, parseAmountCents("12,").getOrThrow())
    }

    @Test
    fun trailingDotWithNoFraction_parsesAsWholeAmount() {
        assertEquals(1_200L, parseAmountCents("12.").getOrThrow())
    }

    @Test
    fun bareCommaFraction_parses() {
        assertEquals(50L, parseAmountCents(",5").getOrThrow())
    }

    @Test
    fun bareDotFraction_parses() {
        assertEquals(50L, parseAmountCents(".5").getOrThrow())
    }

    @Test
    fun commaDecimal_moreThanTwoPlaces_roundsHalfUp() {
        assertEquals(1_251L, parseAmountCents("12,505").getOrThrow())
    }

    @Test
    fun negativeCommaDecimal_isRejected() {
        val result = parseAmountCents("-5,25")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ArithmeticException)
    }

    @Test
    fun zeroCommaDecimal_isRejected() {
        assertTrue(parseAmountCents("0,00").isFailure)
    }

    // ---- strict format: inputs BigDecimal alone would accept or misread ----

    @Test
    fun groupedThousandsWithCommaDecimal_isRejected() {
        assertInvalidFormat("1.234,50")
    }

    @Test
    fun groupedThousandsWithDotDecimal_isRejected() {
        assertInvalidFormat("1,234.50")
    }

    @Test
    fun multipleCommas_isRejected() {
        assertInvalidFormat("1,2,3")
    }

    @Test
    fun multipleDots_isRejected() {
        assertInvalidFormat("1.2.3")
    }

    @Test
    fun exponentNotation_isRejected() {
        assertInvalidFormat("1e3")
    }

    @Test
    fun internalWhitespace_isRejected() {
        assertInvalidFormat("12 50")
    }

    @Test
    fun loneSeparator_isRejected() {
        assertInvalidFormat(",")
        assertInvalidFormat(".")
    }

    @Test
    fun loneSign_isRejected() {
        assertInvalidFormat("+")
        assertInvalidFormat("-")
    }

    @Test
    fun currencySymbol_isRejected() {
        assertInvalidFormat("12,50€")
        assertInvalidFormat("$12.50")
    }

    private fun assertInvalidFormat(input: String) {
        val result = parseAmountCents(input)
        assertTrue("expected '$input' to be rejected", result.isFailure)
        assertTrue(
            "expected NumberFormatException for '$input' but got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is NumberFormatException,
        )
    }

    // ---- toAmountInputText ----

    @Test
    fun toAmountInputText_commaSeparator() {
        assertEquals("12,50", 1_250L.toAmountInputText(','))
    }

    @Test
    fun toAmountInputText_subUnitAmount_hasLeadingZero() {
        assertEquals("0,05", 5L.toAmountInputText(','))
    }

    @Test
    fun toAmountInputText_dotSeparator() {
        assertEquals("12.50", 1_250L.toAmountInputText('.'))
    }

    @Test
    fun toAmountInputText_defaultsToDot() {
        assertEquals("12.50", 1_250L.toAmountInputText())
    }

    @Test
    fun toAmountInputText_wholeAmount_keepsTwoFractionDigits() {
        assertEquals("12.00", 1_200L.toAmountInputText())
    }

    @Test
    fun toAmountInputText_zero() {
        assertEquals("0.00", 0L.toAmountInputText())
    }

    @Test
    fun toAmountInputText_negative_keepsSign() {
        assertEquals("-0.50", (-50L).toAmountInputText())
    }

    @Test
    fun toAmountInputText_largeAmount_noGroupingOrExponent() {
        assertEquals("1000000,99", 100_000_099L.toAmountInputText(','))
    }

    @Test
    fun toAmountInputText_roundTripsThroughParse_forBothSeparators() {
        val samples = listOf(1L, 5L, 10L, 99L, 100L, 1_250L, 123_456L, 100_000_099L)
        for (separator in listOf('.', ',')) {
            for (cents in samples) {
                val text = cents.toAmountInputText(separator)
                assertEquals("round trip of $cents via '$text'", cents, parseAmountCents(text).getOrThrow())
            }
        }
    }
}
