package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.Period

/**
 * Bundles the Dashboard's navigation/action callbacks so [DashboardScreen] and its internal
 * `DashboardContent` composable stay under detekt's LongParameterList threshold as more entry
 * points get added.
 */
data class DashboardCallbacks(
    val onOpenList: (Period) -> Unit,
    val onAddExpense: () -> Unit,
    val onOpenIncomeList: (Period) -> Unit,
    val onAddIncome: () -> Unit,
    val onOpenSettings: () -> Unit,
    /** Opens the given expense (by id) for editing; used by the recent-expense rows. */
    val onOpenExpense: (String) -> Unit,
    /** Opens the given income entry (by id) for editing; used by the recent-income rows. */
    val onOpenIncome: (String) -> Unit,
    /** Opens the monthly plan screen. */
    val onOpenMonthlyPlan: () -> Unit,
)
