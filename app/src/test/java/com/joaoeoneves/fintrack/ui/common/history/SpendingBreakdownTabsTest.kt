package com.joaoeoneves.fintrack.ui.common.history

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryTotal
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.EnvelopeProgress
import com.joaoeoneves.fintrack.domain.model.EnvelopesSummary
import com.joaoeoneves.fintrack.domain.model.SpendingSplit
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityAnalytics
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityBreakdown
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SpendingBreakdownTabsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(withEnvelopes: Boolean) {
        val group = Envelope("g1", "Essentials", 0)
        val summary =
            if (withEnvelopes) {
                EnvelopesSummary(listOf(EnvelopeProgress(group, null, 5_000L, emptyList())), emptyList(), 0L)
            } else {
                EnvelopesSummary(emptyList(), emptyList(), 0L)
            }
        val food = BuiltInCategories.FOOD.copy(groupId = if (withEnvelopes) "g1" else null)
        val analytics =
            ActivityAnalytics(
                spentCents = 5_000L,
                split = SpendingSplit(0L, 5_000L, 0L),
                breakdown = ActivityBreakdown(listOf(CategoryTotal(food, 5_000L)), true, summary),
                envelopeChips = emptyList(),
            )
        rule.setContent { FinTrackTheme { SpendingHistory(analytics, history = null, onCategoryClick = {}) } }
    }

    @Test
    fun withoutEnvelopes_noCategoryGroupTabs() {
        show(withEnvelopes = false)

        rule.onNodeWithText("Category").assertDoesNotExist()
        rule.onNodeWithText("Group").assertDoesNotExist()
    }

    @Test
    fun withEnvelopes_tabsShow_andGroupSwitchesToTheGroupView() {
        show(withEnvelopes = true)

        rule.onNodeWithText("Category").assertExists()
        rule.onNodeWithText("Essentials").assertDoesNotExist()

        rule.onNodeWithText("Group").performClick()

        rule.onNodeWithText("Essentials").assertExists()
    }
}
