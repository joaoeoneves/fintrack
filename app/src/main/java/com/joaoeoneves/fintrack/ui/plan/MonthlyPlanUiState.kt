package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.Earmark
import com.joaoeoneves.fintrack.domain.model.GroupProgress
import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.IncomeRow
import com.joaoeoneves.fintrack.domain.model.LimitGroup
import com.joaoeoneves.fintrack.domain.model.MonthlyPlanSummary
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.PotsSummary
import com.joaoeoneves.fintrack.domain.model.SalaryComponent
import com.joaoeoneves.fintrack.ui.common.UiText
import java.time.LocalDate
import java.time.YearMonth

sealed interface MonthlyPlanUiState {
    data object Loading : MonthlyPlanUiState

    /**
     * @property incomeLines the expected-income lines, in display order.
     * @property incomeRows the same income as one row per salary component or manual line, for [period]'s month.
     * @property allocations one row per category with its planned limit (or none).
     * @property summary the plan's totals; it has no actual income or spend side, only planned amounts.
     * @property period the selected month (month periods only) the [progress] and fixed spending are measured on.
     * @property progress per-category planned limit against what was actually spent in [period].
     * @property canAddIncomeLine whether the line cap has not been reached yet.
     * @property workingDays the working days of [period]'s month, which per-working-day lines multiply by.
     * @property editor the income-line sheet, or `null` when it is closed.
     * @property groups planned vs actual per category group (and the implicit "Other") for [period]'s month.
     * @property limitGroups every category with a limit, filed under its group, for the limits card.
     * @property pots the month's money split into earmarked pots and the base; `null` when not computed.
     * @property receipts payday and which [incomeRows] were already received.
     */
    data class Content(
        val incomeLines: List<PlannedIncome>,
        val incomeRows: List<IncomeRow>,
        val allocations: List<CategoryBudget>,
        val summary: MonthlyPlanSummary,
        val period: Period,
        val canGoToNextMonth: Boolean,
        val progress: List<CategoryBudget>,
        val fixedThisMonthCents: Long = 0L,
        val canAddIncomeLine: Boolean,
        val workingDays: WorkingDaysState,
        val editor: IncomeLineEditorState?,
        val groups: List<GroupProgress> = emptyList(),
        val limitGroups: List<LimitGroup> = emptyList(),
        val pots: PotsSummary? = null,
        val receipts: IncomeReceipts,
    ) : MonthlyPlanUiState

    data class Error(
        val message: UiText,
    ) : MonthlyPlanUiState
}

/**
 * Payday and the receipts of [Content.period]'s month: [receivedOn] holds the day each fully received income row
 * (keyed by [key]) was received; [canReceive] is false for future months; [today] caps the date of a receipt.
 */
data class IncomeReceipts(
    val payday: LocalDate,
    val today: LocalDate,
    val canReceive: Boolean,
    val receivedOn: Map<String, LocalDate> = emptyMap(),
)

/** The row's identity within a month's list: the id of its first line. */
val IncomeRow.key: String get() = lines.first().id

/**
 * The working days of [month]: [days] is what plans use (the override when [overridden], else the calendar
 * count minus [daysOff]); [calendarDays] is the plain calendar count (holidays out, days off not) and
 * [holidays] how many weekdays of the month are holidays.
 */
data class WorkingDaysState(
    val month: YearMonth,
    val days: Int,
    val daysOff: Int = 0,
    val calendarDays: Int,
    val holidays: Int,
    val overridden: Boolean,
)

/** When and how an income line pays; the part of the line the editor sheet adds beyond label and amount. */
data class IncomeSchedule(
    val basis: IncomeBasis = IncomeBasis.FIXED,
    val frequency: IncomeFrequency = IncomeFrequency.MONTHLY,
    val monthOfYear: Int? = null,
    val yearMonth: YearMonth? = null,
    val skipMonths: Set<Int> = emptySet(),
    val earmark: Earmark? = null,
) {
    /**
     * Keeps only the fields that belong to [frequency] (only monthly lines can be earmarked); a missing month
     * falls back to [fallback]'s.
     */
    fun normalized(fallback: YearMonth): IncomeSchedule =
        when (frequency) {
            IncomeFrequency.MONTHLY -> copy(monthOfYear = null, yearMonth = null)
            IncomeFrequency.YEARLY ->
                copy(
                    monthOfYear = monthOfYear ?: fallback.monthValue,
                    yearMonth = null,
                    skipMonths = emptySet(),
                    earmark = null,
                )
            IncomeFrequency.ONCE ->
                copy(monthOfYear = null, yearMonth = yearMonth ?: fallback, skipMonths = emptySet(), earmark = null)
        }
}

/**
 * The income-line sheet: [editingId] is `null` for a new line. [labelError] is set when the last
 * save attempt was rejected, and the dialog stays open.
 */
data class IncomeLineEditorState(
    val editingId: String?,
    val initialLabel: String,
    val initialAmountCents: Long?,
    val initialSchedule: IncomeSchedule = IncomeSchedule(),
    val labelError: UiText? = null,
    val earmarkOptions: EarmarkOptions = EarmarkOptions(),
    /** A line generated from the salary profile: only its label and earmark can be changed here. */
    val managed: Boolean = false,
    /** The salary component of a managed line, whose name replaces the label in the sheet. */
    val component: SalaryComponent? = null,
)
