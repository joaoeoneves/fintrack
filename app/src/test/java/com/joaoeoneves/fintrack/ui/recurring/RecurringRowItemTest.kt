package com.joaoeoneves.fintrack.ui.recurring

import android.app.Application
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class RecurringRowItemTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(
        active: Boolean = true,
        ended: Boolean = false,
        nextDue: LocalDate? = day(2026, 4, 15),
    ) = rule.setContent {
        FinTrackTheme {
            val item = recurringItem(active = active, endDate = day(2026, 12, 31))
            RecurringRowItem(RecurringRow(item, null, nextDue, ended), RecurringListActions({}, {}, {}))
        }
    }

    @Test
    fun activeItemWithEndDate_showsNextDueThenEndMonth() {
        show()

        rule.onNode(hasText("ends", substring = true) and hasText("2026", substring = true)).assertExists()
        rule.onNode(hasText("Next", substring = true, ignoreCase = true)).assertExists()
    }

    @Test
    fun pausedItem_showsPaused_notTheEnd() {
        show(active = false)

        rule.onNodeWithText("Paused", substring = true).assertExists()
        rule.onNode(hasText("ends ", substring = true)).assertDoesNotExist()
    }

    @Test
    fun endedItem_showsEnded_notTheEnd() {
        show(ended = true, nextDue = null)

        rule.onNodeWithText("Ended", substring = true).assertExists()
        rule.onNode(hasText("ends ", substring = true)).assertDoesNotExist()
    }
}
