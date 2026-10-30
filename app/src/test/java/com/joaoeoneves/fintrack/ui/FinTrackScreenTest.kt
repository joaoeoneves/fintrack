package com.joaoeoneves.fintrack.ui

import android.content.Context
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.joaoeoneves.fintrack.MainActivity
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.dashboard.DashboardTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import javax.inject.Inject

/** Runs the main app flows end to end on the JVM (guest mode, fake sign-in), replacing emulator flows. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = "en-rUS-w411dp-h891dp")
class FinTrackScreenTest {
    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(composeRule)

    @Inject lateinit var incomeRepository: IncomeRepository

    @Inject lateinit var expenseRepository: ExpenseRepository

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        FirebaseTestApp.ensureInitialized(context)
        hiltRule.inject()
        // The default language preference is SYSTEM, which matches the (empty) AppCompat override,
        // so MainActivity never calls setApplicationLocales/recreate; nothing to seed.
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun str(id: Int) = context.getString(id)

    private fun awaitTag(tag: String) = awaitNode(hasTestTag(tag))

    private fun awaitNode(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        composeRule.waitUntil(WAIT_MS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        return composeRule.onAllNodes(matcher)[0]
    }

    private fun awaitText(text: String) = awaitNode(hasText(text, substring = true))

    private fun awaitGone(text: String) {
        composeRule.waitUntil(WAIT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun guest_can_add_edit_delete_and_undo_an_expense() {
        awaitTag(TestTags.CONTINUE_AS_GUEST).performClick()
        awaitTag(TestTags.NAV_HOME)
        awaitTag(TestTags.DASHBOARD_ADD_EXPENSE).performClick()

        val amount = awaitNode(hasContentDescription(str(R.string.field_amount)) and hasSetTextAction())
        assertFalse(
            "amount field must not expose Password semantics",
            amount.fetchSemanticsNode().config.contains(SemanticsProperties.Password),
        )
        amount.performTextInput("1234")
        composeRule.waitForIdle()
        val shown = composeRule.onNode(hasContentDescription(str(R.string.field_amount))).fetchSemanticsNode()
        val state = shown.config[SemanticsProperties.StateDescription]
        assert(state.contains("12.34")) { "amount shows '$state'" }

        awaitNode(hasText(str(R.string.expense_field_name)) and hasSetTextAction()).performTextInput(NAME)
        // Pick the first category chip (the default categories are seeded for guests).
        awaitNode(hasText(str(R.string.expense_field_category))).performScrollTo()
        pickFirstCategory()
        awaitNode(hasText(str(R.string.action_save)) and hasClickAction()).performClick()

        // Home no longer lists recent activity: the new row lives in the Activity tab.
        awaitTag(TestTags.NAV_ACTIVITY).performClick()
        // The form's own name field also shows the text until it closes, so wait for the list row.
        // The new row can sit under the floating add pill until it is scrolled into the clear area.
        awaitRow().performScrollTo().performClick()
        awaitNode(hasText(str(R.string.action_delete)) and hasClickAction())
            .performScrollTo()
            .performClick()
        awaitText(str(R.string.action_undo))
        awaitGone(NAME)
        awaitNode(hasText(str(R.string.action_undo)) and hasClickAction()).performClick()
        awaitRow()
    }

    private fun awaitRow() = awaitNode(hasText(NAME, substring = true) and !hasSetTextAction())

    private fun pickFirstCategory() {
        // Chips sit between the "Category" label and the refund switch; click the first selectable one.
        awaitNode(isSelectable()).performClick()
    }

    @Test
    fun income_tab_shows_month_and_year_scale() {
        awaitTag(TestTags.CONTINUE_AS_GUEST).performClick()
        awaitTag(TestTags.NAV_INCOME).performClick()
        awaitText(str(R.string.period_scale_month))
        awaitText(str(R.string.period_scale_year))
    }

    @Test
    fun home_blocks_follow_the_5_5_order_without_recent_activity() {
        awaitTag(TestTags.CONTINUE_AS_GUEST).performClick()
        awaitTag(TestTags.NAV_HOME)
        runBlocking {
            incomeRepository.addIncome(Income("", "Pay", 100_000L, Instant.now(), null, needsReview = true))
            expenseRepository.addExpense(
                Expense("", "Coffee", 1_000L, BuiltInCategories.SHOPPING, Instant.now(), null),
            )
            expenseRepository.addExpense(
                Expense("", "ETF", 10_000L, BuiltInCategories.INVESTMENTS, Instant.now(), null),
            )
        }

        val review = awaitTag(TestTags.REVIEW_INBOX).y()
        val income = awaitTag(DashboardTags.INCOME_CARD).y()
        val spending = awaitTag(DashboardTags.SPENDING_CARD).y()
        val invested = awaitTag(DashboardTags.INVESTED_ROW).y()

        assertTrue("review $review < income $income", review < income)
        assertTrue("income < spending", income < spending)
        assertTrue("spending < invested", spending < invested)
        // 10 000 of 100 000 received: the rate shows next to the amount.
        awaitText("10%")
        assertTrue(
            "Home lists no recent activity",
            composeRule.onAllNodes(hasText("Coffee", substring = true)).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun settings_has_no_currency_motion_or_import_rows() {
        awaitTag(TestTags.CONTINUE_AS_GUEST).performClick()
        awaitTag(TestTags.ACCOUNT_AVATAR).performClick()
        awaitText(str(R.string.settings_theme))

        awaitText(str(R.string.settings_export_csv))
        assertGone("Currency")
        assertGone("Reduced motion")
        assertGone("Import")
    }

    @Test
    fun recurring_form_has_no_automatic_posting_switch() {
        awaitTag(TestTags.CONTINUE_AS_GUEST).performClick()
        awaitText(str(R.string.recurring_empty_cta)).performClick()
        awaitTag(TestTags.RECURRING_ADD).performClick()
        awaitText(str(R.string.recurring_day_of_month))

        assertGone("utomatic")
        assertTrue(
            "no switch on the new-recurring form",
            composeRule.onAllNodes(isToggleable()).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun SemanticsNodeInteraction.y() = fetchSemanticsNode().positionInRoot.y

    private fun assertGone(text: String) =
        assertTrue(
            "'$text' must not be shown",
            composeRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isEmpty(),
        )

    private companion object {
        const val WAIT_MS = 5_000L
        const val NAME = "Coffee"
    }
}
