package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the shared name/note length limits in `FieldLimits.kt`. */
class FieldLimitsTest {
    /** A non-BMP emoji: one code point, two UTF-16 chars. */
    private val emoji = "😀"

    @Test
    fun limits_matchFirestoreRules() {
        assertEquals(200, MAX_NAME_LENGTH)
        assertEquals(2000, MAX_NOTE_LENGTH)
    }

    @Test
    fun codePointLength_countsSurrogatePairsOnce() {
        assertEquals(0, "".codePointLength())
        assertEquals(3, "abc".codePointLength())
        assertEquals(2, (emoji + emoji).codePointLength())
        assertEquals(4, (emoji + emoji).length)
    }

    // ---- limitInput ----

    @Test
    fun limitInput_proposedWithinLimit_isAccepted() {
        assertEquals("abcd", limitInput(current = "abc", proposed = "abcd", max = 5))
    }

    @Test
    fun limitInput_proposedExactlyAtLimit_isAccepted() {
        assertEquals("abcde", limitInput(current = "abcd", proposed = "abcde", max = 5))
    }

    @Test
    fun limitInput_typingPastLimit_whenAlreadyAtLimit_keepsCurrent() {
        assertEquals("abcde", limitInput(current = "abcde", proposed = "abcdef", max = 5))
    }

    @Test
    fun limitInput_currentAlreadyOverLimit_keepsCurrent() {
        assertEquals("abcdefg", limitInput(current = "abcdefg", proposed = "abcdefgh", max = 5))
    }

    @Test
    fun limitInput_deletingWhileOverLimit_isAcceptedOnceWithinLimit() {
        assertEquals("abcd", limitInput(current = "abcdefg", proposed = "abcd", max = 5))
    }

    @Test
    fun limitInput_pasteOvershootingLimit_isTruncatedToMax() {
        assertEquals("abcde", limitInput(current = "ab", proposed = "abcdefghij", max = 5))
    }

    @Test
    fun limitInput_truncation_neverSplitsASurrogatePair() {
        val proposed = "ab" + emoji.repeat(5)

        val result = limitInput(current = "", proposed = proposed, max = 4)

        assertEquals("ab" + emoji + emoji, result)
        assertEquals(4, result.codePointLength())
        assertEquals(6, result.length)
    }

    @Test
    fun limitInput_emojisCountedAsCodePoints_notUtf16Chars() {
        val atLimit = emoji.repeat(5)

        assertEquals(atLimit, limitInput(current = emoji.repeat(4), proposed = atLimit, max = 5))
        assertEquals(atLimit, limitInput(current = atLimit, proposed = atLimit + "a", max = 5))
    }

    @Test
    fun limitInput_maxZero_onlyAcceptsEmpty() {
        assertEquals("", limitInput(current = "", proposed = "a", max = 0))
        assertEquals("", limitInput(current = "", proposed = "", max = 0))
    }

    // ---- shouldShowLengthCounter ----

    @Test
    fun shouldShowLengthCounter_name_appearsOnlyAbove80Percent() {
        assertFalse(shouldShowLengthCounter(0, MAX_NAME_LENGTH))
        assertFalse(shouldShowLengthCounter(160, MAX_NAME_LENGTH))
        assertTrue(shouldShowLengthCounter(161, MAX_NAME_LENGTH))
        assertTrue(shouldShowLengthCounter(200, MAX_NAME_LENGTH))
    }

    @Test
    fun shouldShowLengthCounter_note_appearsOnlyAbove80Percent() {
        assertFalse(shouldShowLengthCounter(1600, MAX_NOTE_LENGTH))
        assertTrue(shouldShowLengthCounter(1601, MAX_NOTE_LENGTH))
    }
}
