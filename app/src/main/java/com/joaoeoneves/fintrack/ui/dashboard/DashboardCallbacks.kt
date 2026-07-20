package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.TimeRange

/**
 * Bundles the Dashboard's navigation/action callbacks so [DashboardScreen] and its internal
 * `DashboardContent` composable stay under detekt's LongParameterList threshold as more entry
 * points get added.
 */
data class DashboardCallbacks(
    val onOpenList: (TimeRange) -> Unit,
    val onAddExpense: () -> Unit,
    val onOpenIncomeList: (TimeRange) -> Unit,
    val onAddIncome: () -> Unit,
    val onOpenSettings: () -> Unit,
)
