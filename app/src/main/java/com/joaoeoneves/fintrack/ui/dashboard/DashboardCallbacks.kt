package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.StartStep

/**
 * Bundles Home's navigation callbacks so [DashboardScreen] and its internal `DashboardContent`
 * composable stay under detekt's LongParameterList threshold.
 */
data class DashboardCallbacks(
    val onAddExpense: () -> Unit,
    val onAddIncome: () -> Unit,
    val onOpenSettings: () -> Unit,
    /** Opens the given expense (by id) for editing; used by the recent-activity rows. */
    val onOpenExpense: (String) -> Unit,
    /** Opens the given income entry (by id) for editing; used by the recent-activity rows. */
    val onOpenIncome: (String) -> Unit,
    /** Opens the Activity tab on the given period ("See all"). */
    val onOpenActivity: (Period) -> Unit,
    /** Opens the Plan tab. */
    val onOpenPlan: () -> Unit,
    /** Opens the Plan tab with the recurring items list on top. */
    val onOpenRecurring: () -> Unit,
    /** Opens the screen of a "Get started" checklist step. */
    val onOpenStartStep: (StartStep) -> Unit,
)
