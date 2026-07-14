package ptech.joaoe.agenticusage.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ptech.joaoe.agenticusage.ui.dashboard.DashboardScreen
import ptech.joaoe.agenticusage.ui.expense.addedit.AddEditExpenseScreen
import ptech.joaoe.agenticusage.ui.expense.list.ExpenseListScreen

@Composable
fun AgenticUsageNavHost(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Dashboard, modifier = modifier) {
        composable<Dashboard> {
            DashboardScreen(
                onOpenList = { timeRange -> navController.navigate(ExpenseList(timeRange)) },
                onAddExpense = { navController.navigate(AddEditExpense()) },
                onSignOut = onSignOut,
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
    }
}
