package com.joaoeoneves.fintrack.ui.designsystem

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SegmentedTextTabsReducedMotionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun reducedMotion_selectionIsFinalWithoutAdvancingClock() {
        var selected by mutableStateOf("A")
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FinTrackTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    SegmentedTextTabs(
                        options = listOf("A", "B", "C"),
                        selected = selected,
                        label = { it },
                        onSelect = { selected = it },
                    )
                }
            }
        }
        rule.onNodeWithText("A").assertIsSelected()

        rule.onNodeWithText("C").performClick()
        // One frame applies the state change; no further animation time is needed for it to be final.
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithText("C").assertIsSelected()
        rule.onNodeWithText("A").assertIsNotSelected()
    }
}
