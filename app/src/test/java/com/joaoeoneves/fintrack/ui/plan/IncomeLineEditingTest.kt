package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomeLineEditingTest {
    private val required = UiText.StringResource(R.string.error_plan_label_required)
    private val tooLong = UiText.StringResource(R.string.error_plan_label_too_long)

    @Test
    fun emptyLabel_isRequired() = assertEquals(required, validateIncomeLineLabel(""))

    @Test
    fun whitespaceOnlyLabel_isRequired() = assertEquals(required, validateIncomeLineLabel(" \t\n  "))

    @Test
    fun normalLabel_isValid() = assertNull(validateIncomeLineLabel("Salary"))

    @Test
    fun fiftyCharacters_isValid() = assertNull(validateIncomeLineLabel("a".repeat(50)))

    @Test
    fun fiftyOneCharacters_isTooLong() = assertEquals(tooLong, validateIncomeLineLabel("a".repeat(51)))

    @Test
    fun surroundingWhitespace_isTrimmedBeforeCounting() {
        assertNull(validateIncomeLineLabel("  " + "a".repeat(50) + "  "))
    }

    @Test
    fun fiftyEmoji_countAsFiftyCodePoints_andAreValid() = assertNull(validateIncomeLineLabel("💰".repeat(50)))

    @Test
    fun fiftyOneEmoji_areTooLong() = assertEquals(tooLong, validateIncomeLineLabel("💰".repeat(51)))
}
