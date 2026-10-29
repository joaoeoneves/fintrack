package com.joaoeoneves.fintrack.ui.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.joaoeoneves.fintrack.ui.detail.DetailActions
import com.joaoeoneves.fintrack.ui.detail.DetailEditTarget
import com.joaoeoneves.fintrack.ui.detail.DetailScreen

/** The category and income source detail pages, pushed on top of whichever tab is current. */
internal fun NavGraphBuilder.detailRoutes(navController: NavHostController) {
    val actions =
        DetailActions(
            onOpenExpense = { id -> navController.navigate(AddEditExpense(expenseId = id)) },
            onOpenIncome = { id -> navController.navigate(AddEditIncome(incomeId = id)) },
            onEdit = { target ->
                when (target) {
                    is DetailEditTarget.IncomeLines ->
                        navController.navigateToTabWith(IncomeHome(period = target.month))
                    else -> navController.navigate(target.route())
                }
            },
        )
    composable<CategoryDetail> {
        DetailScreen(onBack = dropUnlessResumed { navController.popBackStack() }, actions = actions)
    }
    composable<IncomeSourceDetail> {
        DetailScreen(onBack = dropUnlessResumed { navController.popBackStack() }, actions = actions)
    }
}

/** The editor a detail page's "Edit" action leads to. */
private fun DetailEditTarget.route(): Any =
    when (this) {
        is DetailEditTarget.CategoryEditor -> Categories(editId = id)
        DetailEditTarget.SalaryProfile -> EditSalaryProfile
        is DetailEditTarget.IncomeLines -> IncomeHome(period = month)
    }
