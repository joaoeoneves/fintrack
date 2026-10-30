package com.joaoeoneves.fintrack.ui.designsystem

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The add pill wired the way Spending and Income wire it (hoisted [PillVisibility], `nestedScroll` before
 * `verticalScroll`, [BindTo]) around a long scrolling column. The tabs' Hilt view models are not constructible on
 * the JVM, so the shared wiring is exercised here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AddPillScrollTest {
    @get:Rule
    val rule = createComposeRule()

    private val pillTag = "scroller"
    private val addExpense = "Add expense"

    @Composable
    private fun ScrollingTab() {
        val pill = remember { PillVisibility() }
        val scrollState = rememberScrollState()
        pill.BindTo(scrollState)
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(pill.connection)
                    .verticalScroll(scrollState)
                    .testTag(pillTag),
            ) {
                repeat(CONTENT_BLOCKS) { Box(Modifier.height(BLOCK_HEIGHT)) }
            }
            AddTransactionPill(
                visible = pill.visible,
                onAddExpense = {},
                onAddIncome = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    private fun setTab(
        darkTheme: Boolean = true,
        reducedMotion: Boolean? = null,
    ) = rule.setContent { FinTrackTheme(darkTheme = darkTheme, reducedMotion = reducedMotion) { ScrollingTab() } }

    @Test
    fun swipeUp_hidesPill_swipeDown_bringsItBack() {
        setTab(reducedMotion = false)
        rule.onNodeWithContentDescription(addExpense).assertIsDisplayed()

        rule.onNodeWithTag(pillTag).performTouchInput { swipeUp(startY = bottom * 0.8f, endY = top * 1.2f + 1f) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(ANIMATION_SETTLE_MS)
        rule.onNodeWithContentDescription(addExpense).assertDoesNotExist()

        rule.onNodeWithTag(pillTag).performTouchInput { swipeDown() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(ANIMATION_SETTLE_MS)
        rule.onNodeWithContentDescription(addExpense).assertIsDisplayed()
    }

    @Test
    fun reducedMotion_pillStaysAfterSwipeUp() {
        setTab(reducedMotion = true)

        rule.onNodeWithTag(pillTag).performTouchInput { swipeUp(startY = bottom * 0.8f, endY = top * 1.2f + 1f) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(ANIMATION_SETTLE_MS)

        rule.onNodeWithContentDescription(addExpense).assertIsDisplayed()
    }

    @Test
    fun lightTheme_rendersPill() {
        setTab(darkTheme = false)

        rule.onNodeWithContentDescription(addExpense).assertIsDisplayed()
        rule.onNodeWithContentDescription("Add income").assertIsDisplayed()
    }

    private companion object {
        const val CONTENT_BLOCKS = 40
        val BLOCK_HEIGHT = 100.dp
        const val ANIMATION_SETTLE_MS = 1_000L
    }
}
