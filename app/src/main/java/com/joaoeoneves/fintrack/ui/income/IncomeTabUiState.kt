package com.joaoeoneves.fintrack.ui.income

import com.joaoeoneves.fintrack.domain.model.IncomeMonth
import com.joaoeoneves.fintrack.domain.model.IncomeRow
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.YearIncomeOutlook
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityHistory
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.plan.IncomeLineEditorState
import com.joaoeoneves.fintrack.ui.plan.IncomeReceipts
import com.joaoeoneves.fintrack.ui.plan.WorkingDaysState

sealed interface IncomeTabUiState {
    data object Loading : IncomeTabUiState

    /**
     * @property period the selected month or year.
     * @property month the expected-vs-received rows of [period]'s month; `null` on the Year scale.
     * @property history the bars and per-source figures of [period] (`null` until they have loaded).
     * @property editor the open manual-line sheet, if any.
     * @property hasProfile whether a salary profile already generates income lines.
     * @property canAddIncomeLine whether the manual-line cap has not been reached yet.
     */
    data class Content(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val month: IncomeMonthView?,
        val history: ActivityHistory?,
        val editor: IncomeLineEditorState?,
        val hasProfile: Boolean,
        val canAddIncomeLine: Boolean,
        /** The meal card's plafond, spent and balance over [period]; `null` when the meal card does not apply. */
        val mealCard: MealCardView? = null,
        /** On a current or future Year: each month received or expected, and the totals. */
        val outlook: YearIncomeOutlook? = null,
    ) : IncomeTabUiState

    data class Error(
        val message: UiText,
    ) : IncomeTabUiState
}

/** What the Month scale shows: one row per salary component or manual line, the working days and the receipts. */
data class IncomeMonthView(
    val incomeRows: List<IncomeRow>,
    val workingDays: WorkingDaysState,
    val receipts: IncomeReceipts,
    val duplicateKeys: Set<String>,
    val incomeMonth: IncomeMonth,
)
