package com.joaoeoneves.fintrack.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [amountMatchesQuery] and [containsIgnoreCase]. */
class SearchMatchingTest {
    @Test
    fun amount_exactDecimalWithDot_matches() {
        assertTrue(amountMatchesQuery(1_234L, "12.34"))
    }

    @Test
    fun amount_exactDecimalWithComma_matches() {
        assertTrue(amountMatchesQuery(1_234L, "12,34"))
    }

    @Test
    fun amount_integerPart_matches() {
        assertTrue(amountMatchesQuery(1_234L, "12"))
    }

    @Test
    fun amount_partialSubstring_matchesLargerAmount() {
        // 11200 cents -> "112.00", which contains "12".
        assertTrue(amountMatchesQuery(11_200L, "12"))
    }

    @Test
    fun amount_trailingSeparator_matches() {
        assertTrue(amountMatchesQuery(1_234L, "12."))
        assertTrue(amountMatchesQuery(1_234L, "12,"))
    }

    @Test
    fun amount_surroundingWhitespace_isTrimmed() {
        assertTrue(amountMatchesQuery(1_234L, "  12.34 "))
    }

    @Test
    fun amount_smallValues_areZeroPadded() {
        assertTrue(amountMatchesQuery(5L, "0.05"))
        assertTrue(amountMatchesQuery(0L, "0.00"))
    }

    @Test
    fun amount_nonNumericQuery_neverMatches() {
        assertFalse(amountMatchesQuery(1_234L, "abc"))
        assertFalse(amountMatchesQuery(1_234L, "12a"))
        assertFalse(amountMatchesQuery(1_234L, "-12"))
    }

    @Test
    fun amount_moreThanTwoFractionDigits_neverMatches() {
        assertFalse(amountMatchesQuery(1_234L, "12.345"))
    }

    @Test
    fun amount_groupingSeparators_areUnderstood() {
        assertTrue(amountMatchesQuery(123_400L, "1,234.00"))
        assertTrue(amountMatchesQuery(123_450L, "1.234,50"))
        assertTrue(amountMatchesQuery(123_456_700L, "1.234.567"))
        // A lone '.' + exactly three digits is grouping: "1.234" is 1234, not 1.234.
        assertTrue(amountMatchesQuery(123_400L, "1.234"))
        assertFalse(amountMatchesQuery(123L, "1.234"))
    }

    @Test
    fun amount_loneCommaIsDecimal_evenWithThreeDigits() {
        assertFalse(amountMatchesQuery(123_400L, "1,234"))
        assertTrue(amountMatchesQuery(1_250L, "12,5"))
    }

    @Test
    fun amount_currencySignAndSpaces_areIgnored() {
        assertTrue(amountMatchesQuery(1_250L, "12,50 €"))
        assertTrue(amountMatchesQuery(1_250L, "€12.50"))
        assertTrue(amountMatchesQuery(123_450L, "1\u00A0234,50\u00A0€"))
        assertTrue(amountMatchesQuery(123_450L, "1 234,50"))
        assertFalse(amountMatchesQuery(1_250L, "€"))
    }

    @Test
    fun amount_emptyOrBlankQuery_neverMatches() {
        assertFalse(amountMatchesQuery(1_234L, ""))
        assertFalse(amountMatchesQuery(1_234L, "   "))
        assertFalse(amountMatchesQuery(1_234L, "."))
    }

    @Test
    fun amount_nonMatchingNumber_doesNotMatch() {
        assertFalse(amountMatchesQuery(1_234L, "99"))
        assertFalse(amountMatchesQuery(1_234L, "12.35"))
    }

    @Test
    fun amount_negativeCents_refund_matchesByMagnitude() {
        assertTrue(amountMatchesQuery(-1_234L, "12.34"))
        assertTrue(amountMatchesQuery(-1_234L, "12,34"))
    }

    @Test
    fun containsIgnoreCase_matchesCaseInsensitively_andNullNeverMatches() {
        assertTrue("Morning Coffee".containsIgnoreCase("COFFEE"))
        assertFalse("Morning Coffee".containsIgnoreCase("tea"))
        assertFalse(null.containsIgnoreCase("anything"))
        assertTrue("x".containsIgnoreCase(""))
    }
}
