package ptech.joaoe.agenticusage.ui.expense.addedit

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
}
