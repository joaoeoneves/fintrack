package com.joaoeoneves.fintrack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.joaoeoneves.fintrack.ui.settings.SettingsScreen
import com.joaoeoneves.fintrack.ui.settings.categories.CategoriesScreen

@Composable
fun FinTrackNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Dashboard, modifier = modifier) {
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
                    ),
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
        composable<Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenCategories = { navController.navigate(Categories) },
            )
        }
        composable<Categories> {
            CategoriesScreen(onBack = { navController.popBackStack() })
        }
    }
}
