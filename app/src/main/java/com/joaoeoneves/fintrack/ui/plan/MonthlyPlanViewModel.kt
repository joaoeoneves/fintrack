package com.joaoeoneves.fintrack.ui.plan

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.Earmark
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.MAX_PLANNED_INCOME_LINES
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.budgetProgressOf
import com.joaoeoneves.fintrack.domain.model.canGoToNextMonth
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.effectiveWorkingDays
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.holidayWeekdaysIn
import com.joaoeoneves.fintrack.domain.model.incomeRowsIn
import com.joaoeoneves.fintrack.domain.model.isManaged
import com.joaoeoneves.fintrack.domain.model.limitGroupsOf
import com.joaoeoneves.fintrack.domain.model.monthlyPlanOf
import com.joaoeoneves.fintrack.domain.model.salaryComponent
import com.joaoeoneves.fintrack.domain.model.workingDaysIn
import com.joaoeoneves.fintrack.domain.usecase.ObservePeriodOverview
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.common.period.PeriodActions
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelection
import com.joaoeoneves.fintrack.ui.common.period.asMonthPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

private const val TAG = "MonthlyPlanViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
private const val KEY_SELECTED_MONTH = "selectedMonth"

/** Edits the global monthly plan: expected-income lines and the per-category planned allocations. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MonthlyPlanViewModel
    @Inject
    constructor(
        private val repositories: PlanRepositories,
        private val observePeriodOverview: ObservePeriodOverview,
        savedStateHandle: SavedStateHandle,
        private val clock: Clock,
    ) : ViewModel() {
        // The selected month, month periods only; the plan itself is global, the month only decides
        // which actuals the "planned vs actual" progress and the fixed-spending line are measured on.
        private val selection =
            PeriodSelection(savedStateHandle, KEY_SELECTED_MONTH, Period.CurrentMonth.encode(), clock)
        private val editor = MutableStateFlow<IncomeLineEditorState?>(null)
        private val errors = Channel<UiText>(Channel.BUFFERED)

        /** One-shot user-facing messages for plan writes that failed, shown as a snackbar. */
        val errorEvent: Flow<UiText> = errors.receiveAsFlow()

        private val retryTrigger = MutableStateFlow(0)

        val uiState: StateFlow<MonthlyPlanUiState> =
            combine(selection.token, retryTrigger) { token, _ -> token }
                // "Now" is read on every (re)subscription so a retry after the calendar rolls over
                // re-resolves CurrentMonth and canGoToNextMonth.
                .flatMapLatest { token ->
                    val now = clock.instant()
                    val period = decodePeriod(token, now, clock.zone)
                    val overview = observePeriodOverview(period, now, clock.zone, recentLimit = 0)
                    planFlow(
                        repositories = repositories,
                        selected = SelectedMonth(period, period.canGoToNextMonth(now, clock.zone), overview),
                        editor = editor,
                    )
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    initialValue = MonthlyPlanUiState.Loading,
                )

        fun onRetry() {
            retryTrigger.value++
        }

        /** The month picker's callbacks; month periods only, anything else falls back to the current month. */
        val periodActions: PeriodActions =
            PeriodActions(
                onPrevious = selection::previous,
                onNext = selection::next,
                onSelect = { period -> selection.select(period.asMonthPeriod()) },
            )

        fun onAddIncomeLine() {
            viewModelScope.launch {
                if (repositories.currentLines().count { !it.isManaged } < MAX_PLANNED_INCOME_LINES) {
                    editor.value = IncomeLineEditorState(editingId = null, initialLabel = "", initialAmountCents = null)
                }
            }
        }

        fun onEditIncomeLine(id: String) {
            viewModelScope.launch {
                repositories.currentLines().firstOrNull { it.id == id }?.let { line ->
                    editor.value =
                        IncomeLineEditorState(
                            editingId = line.id,
                            initialLabel = line.label,
                            initialAmountCents = line.amountCents,
                            managed = line.isManaged,
                            component = line.salaryComponent,
                            initialSchedule =
                                IncomeSchedule(
                                    basis = line.basis,
                                    frequency = line.frequency,
                                    monthOfYear = line.monthOfYear,
                                    yearMonth = line.yearMonth,
                                    skipMonths = line.skipMonths,
                                    earmark = line.earmark,
                                ),
                        )
                }
            }
        }

        fun onDismissEditor() {
            editor.value = null
        }

        /**
         * Saves the open editor's line with the trimmed [label]; a rejected label keeps the editor open.
         * [schedule] is cleaned to the fields its frequency uses; [month] fills a missing month.
         */
        fun onSaveIncomeLine(
            label: String,
            amountCents: Long,
            schedule: IncomeSchedule,
            month: YearMonth,
        ) {
            val current = editor.value ?: return
            val trimmed = label.trim()
            val error = validateIncomeLineLabel(trimmed)
            if (error != null) {
                editor.value = current.copy(labelError = error)
                return
            }
            viewModelScope.launch {
                val lines = repositories.currentLines()
                val existing = lines.firstOrNull { it.id == current.editingId }
                val line = incomeLineOf(existing, lines, trimmed, amountCents, schedule.normalized(month))
                repositories.plannedIncome
                    .savePlannedIncome(line)
                    .onSuccess { editor.value = null }
                    .onFailure { reportFailure(it) }
            }
        }

        fun onDeleteIncomeLine(id: String) {
            viewModelScope.launch {
                // A managed line goes away with its salary profile, not by hand.
                if (repositories.currentLines().firstOrNull { it.id == id }?.isManaged == true) return@launch
                repositories.plannedIncome
                    .deletePlannedIncome(id)
                    .onSuccess { editor.value = null }
                    .onFailure { reportFailure(it) }
            }
        }

        /**
         * Overrides [month]'s working days with [days], or restores the calendar count when `null`; [daysOff],
         * when given, also sets the days off taken that month.
         */
        fun onSetWorkingDays(
            month: YearMonth,
            days: Int?,
            daysOff: Int? = null,
        ) {
            viewModelScope.launch {
                repositories.monthSettings.setWorkingDaysOverride(month, days).onFailure { reportFailure(it) }
                daysOff?.let { repositories.monthSettings.setDaysOff(month, it).onFailure { e -> reportFailure(e) } }
            }
        }

        fun onSetAllocation(
            category: Category,
            limitCents: Long,
            skipMonths: Set<Int> = emptySet(),
        ) {
            viewModelScope.launch {
                repositories.budgets.setBudget(category.id, limitCents, skipMonths).onFailure { reportFailure(it) }
            }
        }

        fun onClearAllocation(category: Category) {
            viewModelScope.launch {
                repositories.budgets.clearBudget(category.id).onFailure { reportFailure(it) }
            }
        }

        private suspend fun reportFailure(error: Throwable) {
            Log.w(TAG, "Failed to save the monthly plan", error)
            errors.send(UiText.StringResource(R.string.error_plan_save_failed))
        }
    }

/** The line to save: a managed one keeps the profile's amount and schedule, any other takes the editor's. */
private fun incomeLineOf(
    existing: PlannedIncome?,
    lines: List<PlannedIncome>,
    label: String,
    amountCents: Long,
    schedule: IncomeSchedule,
): PlannedIncome =
    if (existing != null && existing.isManaged) {
        existing.withUserEdits(label, schedule.earmark)
    } else {
        PlannedIncome(
            id = existing?.id.orEmpty(),
            label = label,
            amountCents = amountCents,
            order = existing?.order ?: ((lines.maxOfOrNull { it.order } ?: -1) + 1),
            basis = schedule.basis,
            frequency = schedule.frequency,
            monthOfYear = schedule.monthOfYear,
            yearMonth = schedule.yearMonth,
            skipMonths = schedule.skipMonths,
            earmark = schedule.earmark,
        )
    }

/** A managed line keeps the profile's amount and schedule; only the label and (monthly lines') earmark change. */
private fun PlannedIncome.withUserEdits(
    label: String,
    earmark: Earmark?,
): PlannedIncome = copy(label = label, earmark = if (frequency == IncomeFrequency.MONTHLY) earmark else this.earmark)

private suspend fun PlanRepositories.currentLines(): List<PlannedIncome> = plannedIncome.observePlannedIncome().first()

/** The month the plan is currently evaluated against, with its [overview] flow. */
private class SelectedMonth(
    val period: Period,
    val canGoToNextMonth: Boolean,
    val overview: Flow<PeriodOverview>,
)

private fun planFlow(
    repositories: PlanRepositories,
    selected: SelectedMonth,
    editor: Flow<IncomeLineEditorState?>,
): Flow<MonthlyPlanUiState> =
    combine(
        repositories.plannedIncome.observePlannedIncome(),
        repositories.budgets.observeBudgets(),
        repositories.categories.observeCategories(),
        selected.overview,
        combine(repositories.monthSettings.observeMonthSettings(), editor) { settings, editorState ->
            settings to editorState
        },
    ) { lines, budgets, categories, overview, (settings, editorState) ->
        val allocations = budgetProgressOf(emptyList(), budgets, categories, overview.recurring, overview.budgetMonth)
        val workingDays = workingDaysOf(overview.budgetMonth, settings)
        val content: MonthlyPlanUiState =
            MonthlyPlanUiState.Content(
                incomeLines = lines,
                incomeRows = incomeRowsIn(lines, overview.budgetMonth, workingDays.days),
                allocations = allocations,
                summary = monthlyPlanOf(allocations, lines, emptyList(), overview.budgetMonth, workingDays.days),
                period = selected.period,
                canGoToNextMonth = selected.canGoToNextMonth,
                progress = overview.budgets,
                fixedThisMonthCents = overview.summary.fixedSpendingCents,
                canAddIncomeLine = lines.count { !it.isManaged } < MAX_PLANNED_INCOME_LINES,
                workingDays = workingDays,
                editor = editorState?.copy(earmarkOptions = earmarkOptionsOf(overview, allocations)),
                groups = overview.groups,
                limitGroups = limitGroupsOf(allocations, overview.budgets, overview.groups),
                pots = overview.pots,
            )
        content
    }.catch { e ->
        Log.w(TAG, "Failed to observe the monthly plan", e)
        emit(MonthlyPlanUiState.Error(UiText.StringResource(R.string.error_generic_fallback)))
    }

private fun workingDaysOf(
    month: YearMonth,
    settings: List<MonthSettings>,
): WorkingDaysState {
    val monthSettings = settings.firstOrNull { it.month == month }
    val override = monthSettings?.workingDaysOverride
    val daysOff = monthSettings?.daysOff ?: 0
    val calendar = workingDaysIn(month)
    return WorkingDaysState(
        month = month,
        days = effectiveWorkingDays(month, override, daysOff),
        daysOff = daysOff,
        calendarDays = calendar,
        holidays = holidayWeekdaysIn(month),
        overridden = override != null,
    )
}

/** The defined groups and the active, named categories (those of the plan rows) an income line can be earmarked for. */
private fun earmarkOptionsOf(
    overview: PeriodOverview,
    rows: List<CategoryBudget>,
): EarmarkOptions =
    EarmarkOptions(
        groups = overview.groups.mapNotNull { it.group },
        categories = rows.map { it.category }.filterNot { it.archived || it.isUnknown },
    )
