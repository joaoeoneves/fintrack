package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.QuickAddSuggestion
import com.joaoeoneves.fintrack.domain.model.RecurringCandidate
import com.joaoeoneves.fintrack.domain.model.StartStep

/**
 * Bundles Home's navigation callbacks so [DashboardScreen] and its internal `DashboardContent`
 * composable stay under detekt's LongParameterList threshold.
 */
data class DashboardCallbacks(
    /** Opens the new-expense form from the given viewed period. */
    val onAddExpense: (Period) -> Unit,
    /** Opens the new-income form from the given viewed period. */
    val onAddIncome: (Period) -> Unit,
    val onOpenSettings: () -> Unit,
    /** Opens the given expense (by id) for editing; used by the "To review" rows. */
    val onOpenExpense: (String) -> Unit,
    /** Opens the given income entry (by id) for editing; used by the "To review" rows. */
    val onOpenIncome: (String) -> Unit,
    /** Opens the Plan tab with the recurring items list on top. */
    val onOpenRecurring: () -> Unit,
    /** Opens the screen of a "Get started" checklist step. */
    val onOpenStartStep: (StartStep) -> Unit,
    /** Opens the Income tab on the given period (the "Income" card). */
    val onOpenIncomeTab: (Period) -> Unit = {},
    /** Opens the Spending tab on the given period (the "Spending" card). */
    val onOpenSpendingTab: (Period) -> Unit = {},
    /** Opens the investments page on the given period (the "Invested" row). */
    val onOpenInvestments: (Period) -> Unit = {},
    /** Opens the new-expense form prefilled from a quick-add chip; never saves directly. */
    val onQuickAdd: (QuickAddSuggestion) -> Unit = {},
    /** Opens the recurring editor prefilled from a "make it recurring?" suggestion. */
    val onCreateRecurring: (RecurringCandidate) -> Unit = {},
)
