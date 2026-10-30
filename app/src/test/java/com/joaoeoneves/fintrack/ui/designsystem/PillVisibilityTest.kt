package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PillVisibilityTest {
    private fun PillVisibility.scroll(dy: Float) {
        connection.onPreScroll(Offset(0f, dy), NestedScrollSource.UserInput)
    }

    @Test
    fun startsVisible() = assertTrue(PillVisibility().visible)

    @Test
    fun scrollingDown_hides() {
        val pill = PillVisibility()
        pill.scroll(-40f)

        assertFalse(pill.visible)
    }

    @Test
    fun scrollingUp_showsAgain() {
        val pill = PillVisibility()
        pill.scroll(-40f)
        pill.scroll(40f)

        assertTrue(pill.visible)
    }

    @Test
    fun scrollingDown_atTheEnd_staysVisible() {
        val pill = PillVisibility()
        pill.canScrollForward = { false }
        pill.scroll(-40f)

        assertTrue(pill.visible)
    }

    @Test
    fun tinyDeltas_belowThreshold_areIgnored() {
        val pill = PillVisibility()
        pill.scroll(-2f)
        assertTrue(pill.visible)

        pill.scroll(-40f)
        pill.scroll(2f)
        assertFalse(pill.visible)
    }

    @Test
    fun show_bringsBackAHiddenPill() {
        val pill = PillVisibility()
        pill.scroll(-40f)
        pill.show()

        assertTrue(pill.visible)
    }
}
