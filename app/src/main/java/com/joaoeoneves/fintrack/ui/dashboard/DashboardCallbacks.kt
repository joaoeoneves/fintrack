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
)
