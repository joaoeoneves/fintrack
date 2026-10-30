package com.joaoeoneves.fintrack.ui.activity.analytics

import android.app.Application
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/**
 * Tapping a Year bar opens that month. The caption line above the chart is cleared from semantics (decorative), so its
 * wording cannot be asserted from a JVM compose test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS")
class EvolutionCaptionsTest {
    @get:Rule
    val rule = createComposeRule()

    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val evolution = Evolution(listOf(oct, nov), listOf(100_000L, 0L), highlighted = setOf(0, 1))
    private val opened = mutableListOf<YearMonth>()

    private fun show(captions: EvolutionCaptions?) {
        rule.setContent {
            FinTrackTheme { EvolutionSection(evolution, captions = captions, onMonthClick = { opened += it }) }
        }
    }

    private fun tapBar(index: Int) {
        rule.onAllNodes(hasContentDescription(": ", substring = true) and hasClickAction())[index].performClick()
        rule.waitForIdle()
    }

    @Test
    fun tappingABar_opensThatMonth_eachTime() {
        show(EvolutionCaptions(R.string.evolution_caption_spent))

        tapBar(0)
        tapBar(1)

        assertEquals(listOf(oct, nov), opened)
    }

    @Test
    fun withExpectedIncomeCaptions_tappingStillOpensTheMonth() {
        show(
            EvolutionCaptions(
                labelRes = R.string.evolution_caption_received,
                expectedLabelRes = R.string.evolution_caption_received_expected,
                expectedByMonth = mapOf(oct to 120_000L),
            ),
        )

        tapBar(0)

        assertEquals(listOf(oct), opened)
    }
}
