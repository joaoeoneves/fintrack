package com.joaoeoneves.fintrack.ui.expense.list

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.TimeRange
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Instrumented Compose UI tests confirming [ExpenseListScreen] has no add-expense control of its
 * own -- the add-expense entry point now lives exclusively on the Dashboard's "Recent expenses"
 * card header (see [com.joaoeoneves.fintrack.ui.dashboard.DashboardAddExpenseEntryPointTest]).
 * `ExpenseListScreen`'s top bar is back to just back-navigation, the same shape as
 * `IncomeListScreen`'s top bar, both when the list is populated and when it's empty.
 *
 * Exercises the real [ExpenseListScreen] composable with an [ExpenseListViewModel] constructed
 * directly against an in-memory fake (bypassing Hilt entirely, same pattern as the JVM
 * [ExpenseListViewModelTest] uses).
 */
@RunWith(AndroidJUnit4::class)
class ExpenseListAddExpenseButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val now = Instant.now()

    private fun expense(
        id: String,
        name: String = id,
        amountCents: Long = 1_000L,
    ) = Expense(
        id = id,
        name = name,
        amountCents = amountCents,
        category = ExpenseCategory.SHOPPING,
        date = now,
        note = null,
    )

    private fun setExpenseListContent(expenses: List<Expense> = emptyList()) {
        val viewModel =
            ExpenseListViewModel(
                FakeExpenseRepository(expenses),
                SavedStateHandle(mapOf("timeRange" to TimeRange.ONE_MONTH)),
            )
        composeTestRule.setContent {
            ExpenseListScreen(
                onBack = {},
                onOpenExpense = {},
                viewModel = viewModel,
            )
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("All expenses").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun expenseListScreen_hasNoAddExpenseControl_whenListIsPopulated() {
        setExpenseListContent(expenses = listOf(expense(id = "e1", name = "Groceries")))

        composeTestRule.onNodeWithText("Groceries").assertExists()
        composeTestRule.onNodeWithContentDescription("Add expense").assertDoesNotExist()
    }

    @Test
    fun expenseListScreen_hasNoAddExpenseControl_whenListIsEmpty_noExpensesInRange() {
        setExpenseListContent(expenses = emptyList())

        composeTestRule.onNodeWithText("No expenses in this period yet.").assertExists()
        composeTestRule.onNodeWithContentDescription("Add expense").assertDoesNotExist()
    }
}
