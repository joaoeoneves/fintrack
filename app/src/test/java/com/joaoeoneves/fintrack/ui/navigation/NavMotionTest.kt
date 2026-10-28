package com.joaoeoneves.fintrack.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NavMotionTest {
    @Test
    fun reduced_isAlwaysFade() {
        val tabs = listOf(null, 0, 1, 3)
        for (from in tabs) {
            for (to in tabs) {
                for (form in listOf(true, false)) {
                    assertEquals(NavMotion.Fade, navMotionFor(from, to, form, reduced = true))
                }
            }
        }
    }

    @Test
    fun tabForward() = assertEquals(NavMotion.TabForward, navMotionFor(0, 2, false, false))

    @Test
    fun tabBack() = assertEquals(NavMotion.TabBack, navMotionFor(3, 1, false, false))

    @Test
    fun sameTab_isFade() = assertEquals(NavMotion.Fade, navMotionFor(2, 2, false, false))

    @Test
    fun pushWithoutForm_isPush() = assertEquals(NavMotion.Push, navMotionFor(null, null, false, false))

    @Test
    fun pushWithForm_isPushForm() = assertEquals(NavMotion.PushForm, navMotionFor(null, null, true, false))

    @Test
    fun tabToForm_isPushForm() = assertEquals(NavMotion.PushForm, navMotionFor(1, null, true, false))

    @Test
    fun tabToNonTab_isNeverATabSwitch() {
        for (form in listOf(true, false)) {
            val motion = navMotionFor(1, null, form, false)
            assertNotEquals(NavMotion.TabForward, motion)
            assertNotEquals(NavMotion.TabBack, motion)
        }
    }
}
