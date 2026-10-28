package com.joaoeoneves.fintrack.ui.designsystem

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private sealed interface Ui {
    data object Loading : Ui

    data class Content(
        val text: String,
    ) : Ui

    data object Error : Ui
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class StateCrossfadeTest {
    @get:Rule
    val rule = createComposeRule()

    private var state by mutableStateOf<Ui>(Ui.Loading)

    private fun show(reduced: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FinTrackTheme {
                CompositionLocalProvider(LocalReducedMotion provides reduced) {
                    StateCrossfade(state) { ui ->
                        when (ui) {
                            Ui.Loading -> Skeleton { SkeletonLine(width = 100.dp) }
                            is Ui.Content -> Text(ui.text)
                            Ui.Error -> Text("error")
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun update(next: Ui) {
        rule.runOnUiThread {
            state = next
            Snapshot.sendApplyNotifications()
        }
    }

    private fun skeletons() = rule.onAllNodesWithTag(TestTags.SKELETON).fetchSemanticsNodes().size

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun loading_showsSkeleton() {
        show(reduced = false)
        assertEquals(1, skeletons())
    }

    @Test
    fun loadingToContent_removesSkeletonAfterFade() {
        show(reduced = false)
        update(Ui.Content("hello"))
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(FADE_MS + FRAME_SLACK_MS)
        assertEquals(0, skeletons())
        assertEquals(1, count("hello"))
    }

    @Test
    fun loadingToContent_keepsSkeletonMidFade() {
        show(reduced = false)
        update(Ui.Content("hello"))
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(FADE_MS / 3)
        assertEquals(1, skeletons())
    }

    @Test
    fun loadingToError_removesSkeletonAfterFade() {
        show(reduced = false)
        update(Ui.Error)
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(FADE_MS + FRAME_SLACK_MS)
        assertEquals(0, skeletons())
        assertEquals(1, count("error"))
    }

    @Test
    fun reducedMotion_switchesInstantly() {
        show(reduced = true)
        assertEquals(1, skeletons())
        update(Ui.Content("hello"))
        // Well inside the 150 ms fade window: nothing is animating.
        rule.mainClock.advanceTimeBy(SNAP_MS)
        assertEquals(0, skeletons())
        assertEquals(1, count("hello"))
    }

    @Test
    fun contentUpdateWithSameClass_doesNotRefade() {
        show(reduced = false)
        update(Ui.Content("one"))
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(FADE_MS + FRAME_SLACK_MS)

        update(Ui.Content("two"))
        rule.mainClock.advanceTimeBy(SNAP_MS)

        // No transition: the old text is gone and only one copy of the new text is composed straight away.
        assertEquals(0, count("one"))
        assertEquals(1, count("two"))
    }

    private companion object {
        const val FADE_MS = 150L
        const val FRAME_SLACK_MS = 100L
        const val SNAP_MS = 50L
    }
}
