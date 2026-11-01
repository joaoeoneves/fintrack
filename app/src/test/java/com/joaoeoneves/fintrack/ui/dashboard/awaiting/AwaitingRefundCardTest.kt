package com.joaoeoneves.fintrack.ui.dashboard.awaiting

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.joaoeoneves.fintrack.domain.model.OwedRefund
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en")
class AwaitingRefundCardTest {
    @get:Rule
    val rule = createComposeRule()

    private val expense =
        testExpense("Dentist", 10_000, Instant.parse("2026-10-10T12:00:00Z")).copy(awaitingRefund = true)
    private val owed = OwedRefund(expense, refundedCents = 2_500, remainingCents = 7_500)

    private fun show(model: AwaitingRefundModel) = rule.setContent { FinTrackTheme { AwaitingRefundCard(model) } }

    @Test
    fun `shows the total, the row with what is left and the record action`() {
        show(AwaitingRefundModel(AwaitingRefundUiState.Visible(listOf(owed), 1, 7_500)))

        rule.onNodeWithTag(TestTags.AWAITING_REFUND).assertIsDisplayed()
        rule.onNodeWithText("Awaiting refund").assertIsDisplayed()
        rule.onNode(hasText("still owed", substring = true)).assertIsDisplayed()
        rule.onNode(hasText("left", substring = true)).assertIsDisplayed()
        rule.onNodeWithText("Dentist", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Record refund").assertIsDisplayed()
    }

    @Test
    fun `record refund hands the owed expense to the callback, the row opens the expense`() {
        var recorded: OwedRefund? = null
        var opened: String? = null
        show(
            AwaitingRefundModel(
                AwaitingRefundUiState.Visible(listOf(owed), 1, 7_500),
                onOpenExpense = { opened = it },
                onRecordRefund = { recorded = it },
            ),
        )

        rule.onNodeWithText("Record refund").performClick()
        assertEquals(owed, recorded)
        assertEquals(null, opened)

        rule.onNodeWithText("Dentist", substring = true).performClick()
        assertEquals("Dentist", opened)
    }

    @Test
    fun `lists and N more when there are more owed than rows`() {
        show(AwaitingRefundModel(AwaitingRefundUiState.Visible(listOf(owed), 4, 9_000)))

        rule.onNodeWithText("and 3 more").assertIsDisplayed()
    }

    @Test
    fun `renders nothing while hidden`() {
        show(AwaitingRefundModel(AwaitingRefundUiState.Hidden))

        rule.onNodeWithTag(TestTags.AWAITING_REFUND).assertDoesNotExist()
    }
}
