package com.joaoeoneves.fintrack.ui.plan.salary

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The "Advanced" row hides its field until tapped, but never hides a value that is already set. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS")
class AdvancedSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(initiallyExpanded: Boolean) {
        rule.setContent { FinTrackTheme { AdvancedSection(initiallyExpanded) { Text("Inner field") } } }
    }

    private fun innerShown() = rule.onAllNodes(hasText("Inner field")).fetchSemanticsNodes().size

    @Test
    fun collapsedByDefault_whenNothingIsSet() {
        show(initiallyExpanded = false)

        assertEquals(0, innerShown())
    }

    @Test
    fun opensOnTap_andClosesAgain() {
        show(initiallyExpanded = false)

        rule.onNodeWithText("Advanced").performClick()
        assertEquals(1, innerShown())
        rule.onNodeWithText("Advanced").performClick()
        assertEquals(0, innerShown())
    }

    @Test
    fun autoExpanded_whenAValueIsAlreadySet() {
        show(initiallyExpanded = true)

        assertEquals(1, innerShown())
    }
}
