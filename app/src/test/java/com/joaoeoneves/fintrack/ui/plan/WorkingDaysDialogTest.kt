package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class WorkingDaysDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private val feb = YearMonth.of(2026, 2)
    private val state = WorkingDaysState(month = feb, days = 20, vacationDays = 1, calendarDays = 21, holidays = 0)

    private var saved: List<Any>? = null

    private fun show() =
        rule.setContent {
            FinTrackTheme {
                WorkingDaysDialog(
                    state = state,
                    onSave = { months, v, s, o -> saved = listOf(months, v, s, o) },
                    onDismiss = {},
                )
            }
        }

    @Test
    fun showsThreeSteppers_andTheUntilRow() {
        show()

        rule.onNodeWithText("Vacation").assertExists()
        rule.onNodeWithText("Sick leave").assertExists()
        rule.onNodeWithText("Other").assertExists()
        rule.onNodeWithTag(TestTags.DAYS_OFF_UNTIL).assertExists()
    }

    @Test
    fun steppersSaveEachTypeSeparately_forTheOpenedMonthOnly() {
        show()

        rule.onNodeWithContentDescription("More vacation days").performClick()
        rule.onNodeWithContentDescription("More sick days").performClick()
        rule.onNodeWithContentDescription("More sick days").performClick()
        rule.onNodeWithTag(TestTags.DAYS_OFF_SAVE).performClick()

        assertEquals(listOf(listOf(feb), 2, 2, 0), saved)
    }
}
