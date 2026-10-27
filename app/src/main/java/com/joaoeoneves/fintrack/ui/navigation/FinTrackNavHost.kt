package com.joaoeoneves.fintrack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.ui.dashboard.DashboardCallbacks
import com.joaoeoneves.fintrack.ui.dashboard.DashboardScreen
import com.joaoeoneves.fintrack.ui.expense.addedit.AddEditExpenseScreen
import com.joaoeoneves.fintrack.ui.expense.list.ExpenseListScreen
import com.joaoeoneves.fintrack.ui.income.addedit.AddEditIncomeScreen
import com.joaoeoneves.fintrack.ui.income.list.IncomeListScreen
import com.joaoeoneves.fintrack.ui.plan.MonthlyPlanScreen
import com.joaoeoneves.fintrack.ui.recurring.AddEditRecurringScreen
import com.joaoeoneves.fintrack.ui.recurring.RecurringListScreen
import com.joaoeoneves.fintrack.ui.settings.SettingsScreen
import com.joaoeoneves.fintrack.ui.settings.categories.CategoriesScreen
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion

@Composable
fun FinTrackNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val reducedMotion = LocalReducedMotion.current
    val transitions = remember(reducedMotion) { NavTransitions(reducedMotion) }
    NavHost(
        navController = navController,
        startDestination = Dashboard,
        modifier = modifier,
        enterTransition = { transitions.enter() },
        exitTransition = {
            if (targetState.destination.isForm()) transitions.exitUnderForm() else transitions.exit()
        },
        popEnterTransition = {
            if (initialState.destination.isForm()) transitions.popEnterUnderForm() else transitions.popEnter()
        },
        popExitTransition = { transitions.popExit() },
    ) {
        composable<Dashboard> {
            DashboardScreen(
                callbacks =
                    DashboardCallbacks(
                        onOpenList = { period -> navController.navigate(ExpenseList(period.encode())) },
                        onAddExpense = { navController.navigate(AddEditExpense()) },
                        onOpenIncomeList = { period -> navController.navigate(IncomeList(period.encode())) },
                        onAddIncome = { navController.navigate(AddEditIncome()) },
                        onOpenSettings = { navController.navigate(Settings) },
                        onOpenExpense = { id -> navController.navigate(AddEditExpense(expenseId = id)) },
                        onOpenIncome = { id -> navController.navigate(AddEditIncome(incomeId = id)) },
                        onOpenMonthlyPlan = { navController.navigate(MonthlyPlan) },
                        onOpenRecurring = { navController.navigate(RecurringList) },
                    ),
            )
        }
        transactionRoutes(navController, transitions)
        composable<Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenCategories = { navController.navigate(Categories) },
                onOpenRecurring = { navController.navigate(RecurringList) },
            )
        }
        composable<MonthlyPlan> {
            MonthlyPlanScreen(onBack = { navController.popBackStack() })
        }
        recurringRoutes(navController, transitions)
        composable<Categories> {
            CategoriesScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** Add/edit forms enter from the bottom; the screen beneath them fades instead of sliding sideways. */
private fun NavDestination.isForm(): Boolean {
    val forms = listOf(hasRoute<AddEditExpense>(), hasRoute<AddEditIncome>(), hasRoute<AddEditRecurring>())
    return forms.any { it }
}

private fun NavGraphBuilder.recurringRoutes(
    navController: NavHostController,
    transitions: NavTransitions,
) {
    composable<RecurringList> {
        RecurringListScreen(
            onBack = { navController.popBackStack() },
            onOpenEditor = { id -> navController.navigate(AddEditRecurring(recurringId = id)) },
        )
    }
    composable<AddEditRecurring>(
        enterTransition = { transitions.enterFromBottom() },
        popExitTransition = { transitions.popExitToBottom() },
    ) {
        AddEditRecurringScreen(
            onSaved = { navController.popBackStack() },
            onCancel = { navController.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.transactionRoutes(
    navController: NavHostController,
    transitions: NavTransitions,
) {
    composable<ExpenseList> {
        ExpenseListScreen(
            onBack = { navController.popBackStack() },
            onOpenExpense = { id -> navController.navigate(AddEditExpense(expenseId = id)) },
        )
    }
    composable<AddEditExpense>(
        enterTransition = { transitions.enterFromBottom() },
        popExitTransition = { transitions.popExitToBottom() },
    ) {
        AddEditExpenseScreen(
            onSaved = { navController.popBackStack() },
            onCancel = { navController.popBackStack() },
        )
    }
    composable<IncomeList> {
        IncomeListScreen(
            onBack = { navController.popBackStack() },
            onOpenIncome = { id -> navController.navigate(AddEditIncome(incomeId = id)) },
        )
    }
    composable<AddEditIncome>(
        enterTransition = { transitions.enterFromBottom() },
        popExitTransition = { transitions.popExitToBottom() },
    ) {
        AddEditIncomeScreen(
            onSaved = { navController.popBackStack() },
            onCancel = { navController.popBackStack() },
        )
    }
}
