package com.joaoeoneves.fintrack.ui.designsystem

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class PendingOccurrenceRowTest {
    @get:Rule
    val rule = createComposeRule()

    private val labels = PendingLabels(confirm = "Confirm", more = "More", skip = "Skip it", changeAmount = "Change")

    private fun show(
        onConfirm: (() -> Unit)? = null,
        confirmEnabled: Boolean = true,
        onSkip: (() -> Unit)? = null,
        onChangeAmount: (() -> Unit)? = null,
    ) = rule.setContent {
        FinTrackTheme {
            PendingOccurrenceRow(
                title = "Rent",
                amountText = "10,00",
                statusText = "Due today",
                labels = labels,
                onConfirm = onConfirm,
                confirmEnabled = confirmEnabled,
                onSkip = onSkip,
                onChangeAmount = onChangeAmount,
            )
        }
    }

    @Test
    fun withoutOnConfirm_showsTextButNoControls() {
        show(onSkip = {}, onChangeAmount = {})

        rule.onNodeWithText("Rent").assertExists()
        rule.onNodeWithText("Due today").assertExists()
        rule.onNodeWithTag(TestTags.RECURRING_CONFIRM).assertDoesNotExist()
        rule.onNodeWithTag(TestTags.RECURRING_MORE).assertDoesNotExist()
    }

    @Test
    fun confirmClick_runsOnConfirm() {
        var confirmed = 0
        show(onConfirm = { confirmed++ })

        rule.onNodeWithTag(TestTags.RECURRING_CONFIRM).performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun confirmDisabled_whenNotDue() {
        show(onConfirm = {}, confirmEnabled = false)

        rule.onNodeWithTag(TestTags.RECURRING_CONFIRM).assertIsNotEnabled()
    }

    @Test
    fun noOverflow_whenNeitherSkipNorChangeIsGiven() {
        show(onConfirm = {})

        rule.onNodeWithTag(TestTags.RECURRING_MORE).assertDoesNotExist()
    }

    @Test
    fun overflow_offersSkipAndChange_andEachRunsItsCallback() {
        var skipped = 0
        var changed = 0
        show(onConfirm = {}, onSkip = { skipped++ }, onChangeAmount = { changed++ })

        rule.onNodeWithTag(TestTags.RECURRING_MORE).performClick()
        rule.onNodeWithTag(TestTags.RECURRING_CHANGE).performClick()
        rule.onNodeWithTag(TestTags.RECURRING_MORE).performClick()
        rule.onNodeWithTag(TestTags.RECURRING_SKIP).performClick()

        assertEquals(1, changed)
        assertEquals(1, skipped)
    }

    @Test
    fun overflow_withOnlySkip_hasNoChangeItem() {
        show(onConfirm = {}, onSkip = {})

        rule.onNodeWithTag(TestTags.RECURRING_MORE).performClick()

        rule.onNodeWithTag(TestTags.RECURRING_SKIP).assertExists()
        rule.onNodeWithTag(TestTags.RECURRING_CHANGE).assertDoesNotExist()
    }
}
