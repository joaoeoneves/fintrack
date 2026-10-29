@file:Suppress("MatchingDeclarationName") // The route's file also holds its graph builder.

package com.joaoeoneves.fintrack.ui.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.joaoeoneves.fintrack.ui.detail.InvestmentDetailScreen
import kotlinx.serialization.Serializable

/**
 * The investments page (Home's "Invested" row): monthly bars, year and all-time totals, the split per instrument and
 * the selected month's movements. [period] is an encoded `Period` token (a month, or a year).
 */
@Serializable
data class InvestmentDetail(
    val period: String,
)

/** The investments page, pushed on top of whichever tab is current. */
internal fun NavGraphBuilder.investmentRoutes(navController: NavHostController) {
    composable<InvestmentDetail> {
        InvestmentDetailScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onOpenExpense = { id -> navController.navigate(AddEditExpense(expenseId = id)) },
        )
    }
}
