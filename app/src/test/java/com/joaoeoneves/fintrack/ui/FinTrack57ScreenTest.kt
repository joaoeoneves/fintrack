package com.joaoeoneves.fintrack.ui

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.joaoeoneves.fintrack.MainActivity
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import com.joaoeoneves.fintrack.ui.common.TestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/** The 5.7 interactions (split, refund link, duplicate, multi-select, badges) through the real app, guest mode. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = "en-rUS-w411dp-h891dp")
class FinTrack57ScreenTest {
    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(composeRule)

    @Inject lateinit var expenseRepository: ExpenseRepository

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        FirebaseTestApp.ensureInitialized(context)
        hiltRule.inject()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun str(id: Int) = context.getString(id)

    private fun awaitNode(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        composeRule.waitUntil(WAIT_MS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        return composeRule.onAllNodes(matcher)[0]
    }

    private fun awaitTag(tag: String) = awaitNode(hasTestTag(tag))

    private fun awaitText(text: String) = awaitNode(hasText(text, substring = true))

    private fun isShown(text: String) = composeRule.onAllNodesWithText(text, true).fetchSemanticsNodes().isNotEmpty()

    private fun seed(
        name: String,
        cents: Long,
        refundOf: String? = null,
    ): String =
        runBlocking {
            val noon = ZonedDateTime.now(ZoneId.systemDefault()).withHour(12).toInstant()
            expenseRepository
                .addExpense(
                    Expense(
                        "",
                        name,
                        cents,
                        BuiltInCategories.SHOPPING,
                        noon,
                        null,
                        isRefund = refundOf != null,
                        refundOf = refundOf,
                    ),
                ).getOrThrow()
        }

    private fun enterGuest() {
        awaitTag(TestTags.CONTINUE_AS_GUEST).performClick()
        awaitTag(TestTags.NAV_HOME)
    }

    private fun openNewExpenseForm() {
        awaitTag(TestTags.DASHBOARD_ADD_EXPENSE).performClick()
        awaitText(str(R.string.expense_field_category))
    }

    private fun openActivity() {
        awaitTag(TestTags.NAV_ACTIVITY).performClick()
    }

    private fun clickText(text: String) = awaitNode(hasText(text) and hasClickAction()).performClick()

    @Test
    fun form_moreOptions_isCollapsed_thenRevealsSplit_withPartsAndFooter() {
        enterGuest()
        openNewExpenseForm()
        assertTrue(!isShown(str(R.string.expense_split_switch)))

        clickText(str(R.string.expense_more_options))
        awaitText(str(R.string.expense_split_switch)).performScrollTo().performClick()

        awaitText(str(R.string.expense_split_add_part))
        // Nothing is typed yet, so the whole (zero) amount is assigned: the footer reads as settled.
        assertTrue(isShown("0"))
        assertTrue(isShown(str(R.string.expense_split_add_part)))
    }

    @Test
    fun form_duplicate_isOnlyInEditMode_andRefundOfSheetListsCandidates() {
        enterGuest()
        seed("Headphones", 10_000L)
        openNewExpenseForm()
        assertTrue(!isShown(str(R.string.action_duplicate)))

        awaitNode(hasText(str(R.string.expense_field_refund))).performScrollTo()
        // The refund switch is the toggleable row; clicking its label row turns it on.
        awaitNode(hasText(str(R.string.expense_field_refund)) and hasClickAction()).performClick()
        awaitText(str(R.string.expense_refund_of)).performScrollTo().performClick()

        awaitText("Headphones")
        awaitText(str(R.string.expense_refund_of_none))
    }

    @Test
    fun edit_showsDuplicate_butNoRecordRefund_forAnExpense() {
        enterGuest()
        seed("Headphones", 10_000L)
        openActivity()

        awaitText("Headphones").performScrollTo().performClick()

        awaitText(str(R.string.action_duplicate))
        assertTrue(!isShown("Record refund"))
        assertTrue(!isShown(str(R.string.expense_more_options)))
    }

    @Test
    fun activity_longPress_opensBar_backClears_andDeleteOffersUndoSnackbar() {
        enterGuest()
        seed("Headphones", 10_000L)
        seed("Shoes", 5_000L)
        openActivity()

        awaitText("Headphones").performTouchInput { longClick() }
        awaitText("1 selected")
        awaitNode(hasText("Shoes", substring = true)).performClick()
        awaitText("2 selected")

        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(WAIT_MS) { !isShown("selected") }

        awaitText("Headphones").performTouchInput { longClick() }
        awaitText("1 selected")
        clickText(str(R.string.selection_delete))

        awaitText("1 expense deleted")
        awaitText(str(R.string.action_undo))
        composeRule.waitUntil(WAIT_MS) { !isShown("selected") }
    }

    @Test
    fun activity_row_showsRefundedBadge_forALinkedRefund() {
        enterGuest()
        val original = seed("Headphones", 10_000L)
        seed("Return", 5_000L, refundOf = original)
        openActivity()

        awaitText("Refunded 50%")
    }

    @Test
    fun spending_tab_flagsACategoryAboveItsAverage() {
        enterGuest()
        runBlocking {
            listOf(1L, 2L, 3L).forEach { monthsBack ->
                val date = ZonedDateTime.now(ZoneId.systemDefault()).withDayOfMonth(10).minusMonths(monthsBack)
                expenseRepository.addExpense(
                    Expense("", "Old $monthsBack", 1_000L, BuiltInCategories.SHOPPING, date.toInstant(), null),
                )
            }
            // Mid-month: a current expense dated in the past of this month is still in the month.
            val first = ZonedDateTime.now(ZoneId.systemDefault()).withDayOfMonth(1).withHour(12)
            expenseRepository.addExpense(
                Expense("", "Big", 9_000L, BuiltInCategories.SHOPPING, first.toInstant(), null),
            )
        }
        awaitTag(TestTags.NAV_SPENDING).performClick()

        awaitText(str(R.string.insights_by_category))
        val above = hasContentDescription(str(R.string.breakdown_above_average), substring = true)
        composeRule.onAllNodes(hasScrollAction())[0].performScrollToNode(above)
        awaitNode(above)
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
