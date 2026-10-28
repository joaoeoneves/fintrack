package com.joaoeoneves.fintrack.ui.designsystem

import android.app.Application
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import com.joaoeoneves.fintrack.ui.common.SwipeToDeleteRow
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MotionComponentsSemanticsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun content(
        reduced: Boolean,
        body: @Composable () -> Unit,
    ) = rule.setContent {
        FinTrackTheme { CompositionLocalProvider(LocalReducedMotion provides reduced) { body() } }
    }

    private fun hasFormatted(text: String) =
        SemanticsMatcher("text == $text") {
            it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { s -> s.text } == text
        }

    @Test
    fun animatedAmount_reducedMotion_visibleAndSemanticsAreFinalAtOnce() {
        var cents by mutableLongStateOf(0L)
        rule.mainClock.autoAdvance = false
        content(reduced = true) {
            AnimatedAmountText(cents, TextStyle.Default, Modifier.testTag("amt"), format = { "v=$it" })
        }
        rule.onNodeWithTag("amt").assert(hasFormatted("v=0"))

        rule.runOnUiThread {
            cents = 12_345L
            Snapshot.sendApplyNotifications()
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithTag("amt").assert(hasFormatted("v=12345"))
        rule.onNodeWithText("v=12345", useUnmergedTree = true).assertExists()
    }

    @Test
    fun animatedAmount_normalMotion_semanticsShowFinalTextWhileCounting() {
        var cents by mutableLongStateOf(0L)
        rule.mainClock.autoAdvance = false
        content(reduced = false) {
            AnimatedAmountText(cents, TextStyle.Default, Modifier.testTag("amt"), format = { "v=$it" })
        }
        rule.runOnUiThread {
            cents = 12_345L
            Snapshot.sendApplyNotifications()
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithTag("amt").assert(hasFormatted("v=12345"))
    }

    @Test
    fun thinProgressBar_rangeInfoIsClampedTarget() {
        content(reduced = true) {
            Row {
                ThinProgressBar(progress = 1.7f, modifier = Modifier.testTag("over"))
                ThinProgressBar(progress = -0.3f, modifier = Modifier.testTag("under"))
                ThinProgressBar(progress = 0.4f, modifier = Modifier.testTag("mid"))
            }
        }
        assertRange("over", 1f)
        assertRange("under", 0f)
        assertRange("mid", 0.4f)
    }

    @Test
    fun thinProgressBar_rangeInfoIsTargetEvenBeforeDrawInFinishes() {
        rule.mainClock.autoAdvance = false
        content(reduced = false) { ThinProgressBar(progress = 0.6f, modifier = Modifier.testTag("bar")) }
        assertRange("bar", 0.6f)
    }

    @Test
    fun progressRing_exposesProgressSemantics() {
        content(reduced = true) { ProgressRing(progress = 0.25f, modifier = Modifier.testTag("ring")) }
        assertRange("ring", 0.25f)
    }

    @Test
    fun swipeToDeleteRow_customActionInvokesOnDelete() {
        var deleted = 0
        content(reduced = true) {
            SwipeToDeleteRow(
                onDelete = { deleted++ },
                deleteLabel = "Remove row",
                modifier = Modifier.testTag("row"),
            ) { Text("content") }
        }
        val actions =
            rule.onNodeWithTag("row").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Remove row"), actions.map { it.label })

        rule.runOnUiThread { actions.single().action() }

        assertEquals(1, deleted)
    }

    private fun assertRange(
        tag: String,
        expected: Float,
    ) {
        val info = rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(ProgressBarRangeInfo(expected, 0f..1f), info)
    }
}
