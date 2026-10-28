package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.common.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant

/**
 * Instrumented Compose UI tests for Home's (the [DashboardScreen]) entry points: the floating
 * "Add expense"/"Add income" pill, the merged "Recent activity" section with its "See all" link
 * (opens the Activity tab on the current period), and tapping a recent row to open that entry.
 *
 * Exercises the real [DashboardScreen] with a [DashboardViewModel] built directly against in-memory
 * fakes (no Hilt, no navigation graph), same pattern as the JVM dashboard ViewModel tests. The
 * add pill sits in the Scaffold FAB slot outside the LazyColumn and is clicked directly; the
 * "Recent activity" section is inside the LazyColumn and may need [scrollToNode] first.
 */
@RunWith(AndroidJUnit4::class)
class DashboardAddExpenseEntryPointTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun expense(
        id: String,
        amountCents: Long = 1_000L,
        date: Instant = Instant.now(),
    ) = Expense(
        id = id,
        name = "Expense $id",
        amountCents = amountCents,
        category = BuiltInCategories.SHOPPING,
        date = date,
        note = null,
    )

    private fun setDashboardContent(
        expenses: List<Expense> = emptyList(),
        onOpenActivity: (Period) -> Unit = {},
        onAddExpense: () -> Unit = {},
        onAddIncome: () -> Unit = {},
        onOpenExpense: (String) -> Unit = {},
    ) {
        val viewModel =
            dashboardViewModel(
                FakeExpenseRepository(expenses),
                FakeBudgetRepository(),
                FakeIncomeRepository(),
                SavedStateHandle(),
                Clock.systemDefaultZone(),
            )
        composeTestRule.setContent {
            DashboardScreen(
                callbacks =
                    DashboardCallbacks(
                        onAddExpense = onAddExpense,
                        onAddIncome = onAddIncome,
                        onOpenSettings = {},
                        onOpenExpense = onOpenExpense,
                        onOpenIncome = {},
                        onOpenActivity = onOpenActivity,
                        onOpenPlan = {},
                        onOpenRecurring = {},
                        onOpenStartStep = {},
                    ),
                viewModel = viewModel,
            )
        }
        composeTestRule.waitForIdle()
    }

    /**
     * Scrolls the Dashboard's LazyColumn until a node matching [matcher] is composed/visible,
     * then returns it. Necessary on real devices where the Dashboard content is taller than the
     * viewport, so later items ("Recent expenses" card, "Recent income" card) aren't composed by
     * default.
     */
    private fun scrollToNode(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(matcher)
    }

    @Test
    fun addExpenseButton_invokesOnAddExpense_whenNoExpensesInRange() {
        var addExpenseClicked = false
        setDashboardContent(
            expenses = emptyList(),
            onAddExpense = { addExpenseClicked = true },
        )

        assertEquals(false, addExpenseClicked)
        composeTestRule.onNodeWithContentDescription("Add expense").performClick()

        assertEquals(true, addExpenseClicked)
    }

    @Test
    fun addExpenseButton_invokesOnAddExpense_whenExpensesArePresent() {
        var addExpenseClicked = false
        setDashboardContent(
            expenses = listOf(expense(id = "e1")),
            onAddExpense = { addExpenseClicked = true },
        )

        composeTestRule.onNodeWithContentDescription("Add expense").performClick()

        assertEquals(true, addExpenseClicked)
    }

    @Test
    fun addIncomeButton_invokesOnAddIncome() {
        var addIncomeClicked = false
        setDashboardContent(onAddIncome = { addIncomeClicked = true })

        composeTestRule.onNodeWithContentDescription("Add income").performClick()

        assertEquals(true, addIncomeClicked)
    }

    @Test
    fun recentActivity_rendersEmptyStateText_andNoSeeAll_whenNothingInRange() {
        setDashboardContent(expenses = emptyList())
        scrollToNode(hasText("Recent activity"))

        composeTestRule.onNodeWithText("No activity yet").assertExists()
        composeTestRule.onNodeWithTag(TestTags.SEE_ALL_ACTIVITY).assertDoesNotExist()
    }

    @Test
    fun recentActivityTitle_tap_opensActivityOnCurrentMonth() {
        var openedWith: Period? = null
        setDashboardContent(
            expenses = listOf(expense(id = "e1")),
            onOpenActivity = { period -> openedWith = period },
        )
        scrollToNode(hasText("Recent activity"))

        assertNull(openedWith)
        composeTestRule.onNodeWithText("Recent activity").performClick()

        assertEquals(Period.CurrentMonth, openedWith)
    }

    @Test
    fun seeAll_tap_opensActivityOnCurrentMonth() {
        var openedWith: Period? = null
        setDashboardContent(
            expenses = listOf(expense(id = "e1")),
            onOpenActivity = { period -> openedWith = period },
        )
        scrollToNode(hasTestTag(TestTags.SEE_ALL_ACTIVITY))

        composeTestRule.onNodeWithTag(TestTags.SEE_ALL_ACTIVITY).performClick()

        assertEquals(Period.CurrentMonth, openedWith)
    }

    @Test
    fun recentExpenseRow_tap_invokesOnOpenExpense_withThatExpensesId_notOnOpenActivity() {
        var openedExpenseId: String? = null
        var openedActivityWith: Period? = null
        setDashboardContent(
            expenses = listOf(expense(id = "e1")),
            onOpenExpense = { id -> openedExpenseId = id },
            onOpenActivity = { period -> openedActivityWith = period },
        )
        scrollToNode(hasText("Expense e1"))

        composeTestRule.onNodeWithText("Expense e1").performClick()

        assertEquals("e1", openedExpenseId)
        assertNull(openedActivityWith)
    }
}
