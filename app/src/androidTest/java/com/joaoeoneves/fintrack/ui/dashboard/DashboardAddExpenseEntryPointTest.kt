package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Period
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant

/**
 * Instrumented Compose UI tests for Home's (the [DashboardScreen]) entry points: the floating
 * "Add expense"/"Add income" pill. Exercises the real screen with a [DashboardViewModel] built
 * directly against in-memory fakes (no Hilt, no navigation graph).
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
        onAddExpense: (Period) -> Unit = {},
        onAddIncome: (Period) -> Unit = {},
        onOpenExpense: (String) -> Unit = {},
    ) {
        val viewModel =
            dashboardViewModel(
                DashboardRepositories(
                    expenseRepository = FakeExpenseRepository(expenses),
                    incomeRepository = FakeIncomeRepository(),
                    categoryRepository = FakeCategoryRepository(),
                ),
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
                        onOpenRecurring = {},
                        onOpenStartStep = {},
                    ),
                viewModel = viewModel,
            )
        }
        composeTestRule.waitForIdle()
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
}
