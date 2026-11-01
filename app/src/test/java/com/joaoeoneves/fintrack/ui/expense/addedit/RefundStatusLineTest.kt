package com.joaoeoneves.fintrack.ui.expense.addedit

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.joaoeoneves.fintrack.domain.model.RefundStatus
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en")
class RefundStatusLineTest {
    @get:Rule
    val rule = createComposeRule()

    private fun form(
        awaiting: Boolean,
        refunded: Long,
    ) = ExpenseFormState(
        isEditMode = true,
        awaitingRefund = awaiting,
        refundStatus = RefundStatus(refunded, 10_000L),
    )

    private fun show(form: ExpenseFormState) = rule.setContent { FinTrackTheme { RefundStatusLine(form) } }

    @Test
    fun `awaiting and partly refunded shows what is left`() {
        show(form(awaiting = true, refunded = 2_500))

        rule.onNodeWithText("Refunded 25%", substring = true).assertIsDisplayed()
        rule.onNodeWithText("left", substring = true).assertIsDisplayed()
    }

    @Test
    fun `awaiting but fully refunded falls back to the real-spend text`() {
        show(form(awaiting = true, refunded = 10_000))

        rule.onNodeWithText("real spend", substring = true).assertIsDisplayed()
    }

    @Test
    fun `not awaiting keeps the real-spend text`() {
        show(form(awaiting = false, refunded = 2_500))

        rule.onNodeWithText("real spend", substring = true).assertIsDisplayed()
    }
}
