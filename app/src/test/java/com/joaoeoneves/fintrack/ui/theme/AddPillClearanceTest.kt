package com.joaoeoneves.fintrack.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bottom padding that keeps list rows clear of the floating add pill. */
class AddPillClearanceTest {
    @Test
    fun clearance_coversPillHeightAndMargin() {
        assertTrue(Dimens.AddPillClearance >= Dimens.AddPillHeight + Dimens.AddPillBottomMargin)
    }

    @Test
    fun clearance_isSumOfItsParts() {
        assertEquals(Dimens.AddPillHeight + Dimens.AddPillBottomMargin + Dimens.Space32, Dimens.AddPillClearance)
    }
}
