package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Instrumented Compose UI tests covering the Dashboard's "Add expense" entry point, which now
 * lives on the "Recent expenses" card header (a `Row` with the section title on
 * `Modifier.weight(1f)` plus an `IconButton(Icons.Default.Add)`), mirroring the existing "Recent
 * Income" card exactly. [ExpenseListScreen] no longer has any add control at all.
 *
 * These exercise the real [DashboardScreen] composable with a [DashboardViewModel] constructed
 * directly against in-memory fakes (bypassing Hilt entirely, same pattern as the JVM
 * `DashboardViewModelExpenseTest`/`DashboardViewModelBudgetTest`/`DashboardViewModelIncomeTest`
 * use) -- no navigation graph or running app is required to prove:
 *  1. Tapping the plus icon (content description "Add expense") on the "Recent expenses" card
 *     header invokes `onAddExpense`, both when `state.recentExpenses` is empty and non-empty.
 *  2. The "Recent expenses" card always renders -- including its empty-state text -- and stays
 *     tappable through to the full expense list even with zero expenses in range.
 *  3. The unrelated "Recent income" plus button is untouched by this change (regression check).
 *
 * NOTE: on a real device screen the Dashboard content (balance card, pie chart, budget rows) can
 * exceed the viewport, so "Recent expenses" / "Add income" may not be composed by the LazyColumn
 * yet without scrolling. Tests that need those nodes scroll the dashboard's own LazyColumn
 * (identified generically via [hasScrollAction]) to the target node first, rather than relying on
 * [androidx.compose.ui.test.junit4.ComposeTestRule.waitUntil], which never scrolls.
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
        category = ExpenseCategory.SHOPPING,
        date = date,
        note = null,
    )

    private fun setDashboardContent(
        expenses: List<Expense> = emptyList(),
        onOpenList: (TimeRange) -> Unit = {},
        onAddExpense: () -> Unit = {},
        onOpenIncomeList: (TimeRange) -> Unit = {},
        onAddIncome: () -> Unit = {},
    ) {
        val viewModel =
            DashboardViewModel(
                FakeExpenseRepository(expenses),
                FakeBudgetRepository(),
                FakeIncomeRepository(),
            )
        composeTestRule.setContent {
            DashboardScreen(
                callbacks =
                    DashboardCallbacks(
                        onOpenList = onOpenList,
                        onAddExpense = onAddExpense,
                        onOpenIncomeList = onOpenIncomeList,
                        onAddIncome = onAddIncome,
                        onOpenSettings = {},
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
    fun addExpenseButton_onRecentExpensesCard_invokesOnAddExpense_whenNoExpensesInRange() {
        var addExpenseClicked = false
        setDashboardContent(
            expenses = emptyList(),
            onAddExpense = { addExpenseClicked = true },
        )
        scrollToNode(hasContentDescription("Add expense"))

        assertEquals(false, addExpenseClicked)
        composeTestRule.onNodeWithContentDescription("Add expense").performClick()

        assertEquals(true, addExpenseClicked)
    }

    @Test
    fun addExpenseButton_onRecentExpensesCard_invokesOnAddExpense_whenExpensesArePresent() {
        var addExpenseClicked = false
        setDashboardContent(
            expenses = listOf(expense(id = "e1")),
            onAddExpense = { addExpenseClicked = true },
        )
        scrollToNode(hasContentDescription("Add expense"))

        assertEquals(false, addExpenseClicked)
        composeTestRule.onNodeWithContentDescription("Add expense").performClick()

        assertEquals(true, addExpenseClicked)
    }

    @Test
    fun recentExpensesCard_rendersEmptyStateText_whenNoExpensesInRange() {
        setDashboardContent(expenses = emptyList())
        scrollToNode(hasText("Recent expenses"))

        // The Recent Expenses card now has its own distinct empty-state string
        // (R.string.dashboard_recent_expenses_empty, "No expenses yet"), separate from
        // DashboardSummary's placeholder (R.string.dashboard_no_expenses, "No expenses in this
        // period yet."). There is exactly one node with this text -- no more ambiguity.
        composeTestRule.onNodeWithText("No expenses yet").assertExists()
    }

    @Test
    fun recentExpensesCard_remainsTappable_whenNoExpensesInRange_andNavigatesToFullList() {
        var openedListWith: TimeRange? = null
        setDashboardContent(
            expenses = emptyList(),
            onOpenList = { range -> openedListWith = range },
        )
        scrollToNode(hasText("Recent expenses"))

        assertNull(openedListWith)
        composeTestRule.onNodeWithText("Recent expenses").performClick()

        assertEquals(TimeRange.ONE_MONTH, openedListWith)
    }

    @Test
    fun recentExpensesCard_stillTappable_whenExpensesArePresent() {
        var openedListWith: TimeRange? = null
        setDashboardContent(
            expenses = listOf(expense(id = "e1")),
            onOpenList = { range -> openedListWith = range },
        )
        scrollToNode(hasText("Recent expenses"))

        composeTestRule.onNodeWithText("Recent expenses").performClick()

        assertEquals(TimeRange.ONE_MONTH, openedListWith)
    }

    @Test
    fun recentIncomeAddButton_isUnaffectedByThisChange_stillInvokesOnAddIncome() {
        var addIncomeClicked = false
        setDashboardContent(onAddIncome = { addIncomeClicked = true })
        scrollToNode(hasContentDescription("Add income"))

        composeTestRule.onNodeWithContentDescription("Add income").performClick()

        assertEquals(true, addIncomeClicked)
    }
}
