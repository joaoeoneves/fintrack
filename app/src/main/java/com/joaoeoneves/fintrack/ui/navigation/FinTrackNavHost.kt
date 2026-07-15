package com.joaoeoneves.fintrack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.joaoeoneves.fintrack.ui.dashboard.DashboardScreen
import com.joaoeoneves.fintrack.ui.expense.addedit.AddEditExpenseScreen
import com.joaoeoneves.fintrack.ui.expense.list.ExpenseListScreen
import com.joaoeoneves.fintrack.ui.income.addedit.AddEditIncomeScreen
import com.joaoeoneves.fintrack.ui.income.list.IncomeListScreen

@Composable
fun FinTrackNavHost(
    onSignOut: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Dashboard, modifier = modifier) {
        composable<Dashboard> {
            DashboardScreen(
                onOpenList = { timeRange -> navController.navigate(ExpenseList(timeRange)) },
                onAddExpense = { navController.navigate(AddEditExpense()) },
                onOpenIncomeList = { timeRange -> navController.navigate(IncomeList(timeRange)) },
                onAddIncome = { navController.navigate(AddEditIncome()) },
                onSignOut = onSignOut,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
            )
        }
        composable<ExpenseList> {
            ExpenseListScreen(
                onBack = { navController.popBackStack() },
                onOpenExpense = { id -> navController.navigate(AddEditExpense(expenseId = id)) },
            )
        }
        composable<AddEditExpense> {
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
        composable<AddEditIncome> {
            AddEditIncomeScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
