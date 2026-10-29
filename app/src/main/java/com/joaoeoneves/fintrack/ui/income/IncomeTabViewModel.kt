package com.joaoeoneves.fintrack.ui.income

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.IncomeMonth
import com.joaoeoneves.fintrack.domain.model.IncomeRow
import com.joaoeoneves.fintrack.domain.model.MAX_PLANNED_INCOME_LINES
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.canGoToNextMonth
import com.joaoeoneves.fintrack.domain.model.canGoToNextYear
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.effectiveWorkingDays
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.holidayWeekdaysIn
import com.joaoeoneves.fintrack.domain.model.incomeRowsIn
import com.joaoeoneves.fintrack.domain.model.isManaged
import com.joaoeoneves.fintrack.domain.model.looksLikeSalary
import com.joaoeoneves.fintrack.domain.model.payDateIn
import com.joaoeoneves.fintrack.domain.model.payingLinesByPayMonth
import com.joaoeoneves.fintrack.domain.model.receivedOn
import com.joaoeoneves.fintrack.domain.model.salaryComponent
import com.joaoeoneves.fintrack.domain.model.workingDaysIn
import com.joaoeoneves.fintrack.domain.usecase.MonthlyHistory
import com.joaoeoneves.fintrack.domain.usecase.ObserveMonthlyHistory
import com.joaoeoneves.fintrack.domain.usecase.ObservePeriodOverview
import com.joaoeoneves.fintrack.domain.usecase.ReceiveRequest
import com.joaoeoneves.fintrack.domain.usecase.RecordPayday
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityHistory
import com.joaoeoneves.fintrack.ui.activity.analytics.periodMonthsOf
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.common.period.PeriodActions
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelection
import com.joaoeoneves.fintrack.ui.common.period.asMonthOrYearPeriod
import com.joaoeoneves.fintrack.ui.plan.IncomeLineEditorState
import com.joaoeoneves.fintrack.ui.plan.IncomeReceipts
import com.joaoeoneves.fintrack.ui.plan.IncomeSchedule
import com.joaoeoneves.fintrack.ui.plan.PlanRepositories
import com.joaoeoneves.fintrack.ui.plan.WorkingDaysState
import com.joaoeoneves.fintrack.ui.plan.amountHistoryNote
import com.joaoeoneves.fintrack.ui.plan.incomeLineOf
import com.joaoeoneves.fintrack.ui.plan.key
import com.joaoeoneves.fintrack.ui.plan.paysDifferently
import com.joaoeoneves.fintrack.ui.plan.receivedPaydayOf
import com.joaoeoneves.fintrack.ui.plan.validateIncomeLineLabel
import com.joaoeoneves.fintrack.ui.plan.versionedFrom
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

private const val TAG = "IncomeTabViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
private const val KEY_SELECTED_PERIOD = "selectedPeriod"
private const val ARG_PERIOD = "period"
private const val HISTORY_MONTHS = 12

/** The month's overview and month settings, the inputs of the Month scale (not loaded on the Year scale). */
private class MonthInputs(
    val overview: PeriodOverview,
    val settings: List<MonthSettings>,
)

/** Everything the tab's state is assembled from. */
private class TabSources(
    val lines: List<PlannedIncome>,
    val inputs: MonthInputs?,
    val history: ActivityHistory?,
    val editor: IncomeLineEditorState?,
)

/**
 * The Income tab: the selected month's expected vs received income (with receive/undo and the manual-line editor)
 * or the selected year's income by source, plus the bars and "By source" breakdown of either scale.
 */
@Suppress("TooManyFunctions") // One handler per user action on the screen.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IncomeTabViewModel
    @Inject
    constructor(
        private val repositories: PlanRepositories,
        private val observePeriodOverview: ObservePeriodOverview,
        private val observeMonthlyHistory: ObserveMonthlyHistory,
        private val recordPayday: RecordPayday,
        savedStateHandle: SavedStateHandle,
        private val clock: Clock,
    ) : ViewModel() {
        private val selection =
            PeriodSelection(
                savedStateHandle,
                KEY_SELECTED_PERIOD,
                savedStateHandle.get<String>(ARG_PERIOD) ?: Period.CurrentMonth.encode(),
                clock,
            )
        private val editor = MutableStateFlow<IncomeLineEditorState?>(null)
        private val errors = Channel<UiText>(Channel.BUFFERED)
        private val deletedLines = Channel<PlannedIncome>(Channel.BUFFERED)

        /** Each manual income line just deleted, for an undo snackbar. */
        val deletedLineEvent: Flow<PlannedIncome> = deletedLines.receiveAsFlow()

        /** One-shot user-facing messages for writes that failed, shown as a snackbar. */
        val errorEvent: Flow<UiText> = errors.receiveAsFlow()

        private val retryTrigger = MutableStateFlow(0)

        val uiState: StateFlow<IncomeTabUiState> =
            combine(selection.token, retryTrigger) { token, _ -> token }
                // "Now" is read on every (re)subscription so a retry after the calendar rolls over re-resolves it.
                .flatMapLatest { token ->
                    val now = clock.instant()
                    val period = decodePeriod(token, now, clock.zone)
                    val canGoNext =
                        if (period is Period.Year) {
                            period.canGoToNextYear(now, clock.zone)
                        } else {
                            period.canGoToNextMonth(now, clock.zone)
                        }
                    combine(
                        repositories.plannedIncome.observePlannedIncome(),
                        monthInputs(period, now),
                        historyOf(period, now),
                        editor,
                    ) { lines, inputs, history, editorState ->
                        contentOf(period, canGoNext, TabSources(lines, inputs, history, editorState))
                    }
                }.catch { e ->
                    Log.w(TAG, "Failed to observe the income tab", e)
                    emit(IncomeTabUiState.Error(UiText.StringResource(R.string.error_generic_fallback)))
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    initialValue = IncomeTabUiState.Loading,
                )

        fun onRetry() {
            retryTrigger.value++
        }

        /** The period selector's callbacks; month and year periods only, anything else falls back to this month. */
        val periodActions: PeriodActions =
            PeriodActions(
                onPrevious = selection::previous,
                onNext = selection::next,
                onSelect = { period -> selection.select(period.asMonthOrYearPeriod()) },
                onSelectScale = selection::selectScale,
            )

        private fun monthInputs(
            period: Period,
            now: Instant,
        ): Flow<MonthInputs?> =
            if (period is Period.Year) {
                flowOf(null)
            } else {
                combine(
                    observePeriodOverview(period, now, clock.zone, recentLimit = 0),
                    repositories.monthSettings.observeMonthSettings(),
                    ::MonthInputs,
                )
            }

        // The window ends at the period's last month and is at least a year long.
        private fun historyOf(
            period: Period,
            now: Instant,
        ): Flow<ActivityHistory?> {
            val months = periodMonthsOf(period, now, clock.zone)
            val calendarMonth = period is Period.CurrentMonth || period is Period.Month
            return observeMonthlyHistory(months.last(), maxOf(HISTORY_MONTHS, months.size), clock.zone)
                .map<MonthlyHistory, ActivityHistory?> { ActivityHistory(months, calendarMonth, it) }
                .catch { e ->
                    Log.w(TAG, "History flow failed", e)
                    emit(null)
                }
        }

        private fun contentOf(
            period: Period,
            canGoToNext: Boolean,
            sources: TabSources,
        ): IncomeTabUiState =
            IncomeTabUiState.Content(
                period = period,
                canGoToNextMonth = canGoToNext,
                month = sources.inputs?.let { monthViewOf(sources.lines, it, LocalDate.now(clock), clock.zone) },
                history = sources.history,
                editor = sources.editor,
                hasProfile = sources.lines.any { it.isManaged },
                canAddIncomeLine = sources.lines.count { !it.isManaged } < MAX_PLANNED_INCOME_LINES,
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
                            history = amountHistoryNote(line.versions),
                            initialSchedule =
                                IncomeSchedule(
                                    basis = line.basis,
                                    frequency = line.frequency,
                                    monthOfYear = line.monthOfYear,
                                    yearMonth = line.yearMonth,
                                    skipMonths = line.skipMonths,
                                    autoRecord = line.autoRecord,
                                    payDay = line.payDay,
                                    payDayEarlierIfClosed = line.payDayEarlierIfClosed,
                                ),
                        )
                }
            }
        }

        /**
         * Records [row] of the selected month as received on [date]; [amountCents] replaces the amount of a
         * single-line row. A no-op until the income has loaded (or on the Year scale).
         */
        fun onReceive(
            row: IncomeRow,
            date: LocalDate,
            amountCents: Long?,
        ) {
            val month = (uiState.value as? IncomeTabUiState.Content)?.month ?: return
            viewModelScope.launch {
                val byPayMonth = row.payingLinesByPayMonth(month.workingDays.month, month.incomeMonth)
                for ((payMonth, lines) in byPayMonth) {
                    val request =
                        ReceiveRequest(
                            lines = lines,
                            month = payMonth,
                            workingDays = month.incomeMonth.workingDays(payMonth),
                            date = date,
                            today = month.receipts.today,
                            zone = clock.zone,
                            amountOverrideCents = amountCents,
                        )
                    val result = recordPayday.receive(request)
                    if (result.isFailure) {
                        result.onFailure { reportFailure(it) }
                        break
                    }
                }
            }
        }

        /** Takes [row] of the selected month back to not received by deleting its payday incomes. */
        fun onUndoReceive(row: IncomeRow) {
            val month = (uiState.value as? IncomeTabUiState.Content)?.month ?: return
            viewModelScope.launch {
                val byPayMonth = row.payingLinesByPayMonth(month.workingDays.month, month.incomeMonth)
                for ((payMonth, lines) in byPayMonth) {
                    val result = recordPayday.unreceive(lines, payMonth)
                    if (result.isFailure) {
                        result.onFailure { reportFailure(it) }
                        break
                    }
                }
            }
        }

        /** The calendar working days of the current month, for the line editor when the Year scale has no month. */
        fun fallbackWorkingDays(): WorkingDaysState {
            val month = currentYearMonth(clock.instant(), clock.zone)
            return workingDaysOf(month, emptyList())
        }

        fun onDismissEditor() {
            editor.value = null
        }

        /**
         * Saves the open editor's line with the trimmed [label]; a rejected label keeps the editor open.
         * [schedule] is cleaned to the fields its frequency uses; [month] fills a missing month; [effectiveFrom]
         * is the plan month a new line starts in or a changed amount applies from.
         */
        fun onSaveIncomeLine(
            label: String,
            amountCents: Long,
            schedule: IncomeSchedule,
            month: YearMonth,
            effectiveFrom: YearMonth = currentYearMonth(clock.instant(), clock.zone),
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
                val line =
                    incomeLineOf(existing, lines, trimmed, amountCents, schedule.normalized(month))
                        .versionedFrom(existing, effectiveFrom)
                if (!current.looksLikeSalary && line.looksLikeSalary(lines)) {
                    // A warning, not an error: the next tap on the sheet's button saves anyway.
                    editor.value = current.copy(looksLikeSalary = true)
                    return@launch
                }
                repositories.plannedIncome
                    .savePlannedIncome(line)
                    .onSuccess {
                        if (existing != null && existing.paysDifferently(line)) dropCursor(existing.id)
                        editor.value = null
                    }.onFailure { reportFailure(it) }
            }
        }

        /** Deletes a manual income line at once; [deletedLineEvent] lets the screen offer to undo. */
        fun onDeleteIncomeLine(id: String) {
            viewModelScope.launch {
                val line = repositories.currentLines().firstOrNull { it.id == id }
                // A managed line goes away with its salary profile, not by hand.
                if (line == null || line.isManaged) return@launch
                repositories.plannedIncome
                    .deletePlannedIncome(id)
                    .onSuccess {
                        dropCursor(id)
                        editor.value = null
                        deletedLines.send(line)
                    }.onFailure { reportFailure(it) }
            }
        }

        /** Puts back a line deleted a moment ago. */
        fun onUndoDeleteIncomeLine(line: PlannedIncome) {
            viewModelScope.launch { repositories.plannedIncome.savePlannedIncome(line).onFailure { reportFailure(it) } }
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

        // A line's automation restarts (no backfill) when its pay day or switch changes, or it goes away.
        private suspend fun dropCursor(id: String) {
            repositories.automation.updateLineCursors(remove = setOf(id)).onFailure { reportFailure(it) }
        }

        private suspend fun reportFailure(error: Throwable) {
            Log.w(TAG, "Failed to save the income", error)
            errors.send(UiText.StringResource(R.string.error_plan_save_failed))
        }
    }

private suspend fun PlanRepositories.currentLines(): List<PlannedIncome> = plannedIncome.observePlannedIncome().first()

private fun monthViewOf(
    lines: List<PlannedIncome>,
    inputs: MonthInputs,
    today: LocalDate,
    zone: ZoneId,
): IncomeMonthView {
    val overview = inputs.overview
    val workingDays = workingDaysOf(overview.budgetMonth, inputs.settings)
    val incomeMonth = IncomeMonth.of(inputs.settings)
    val rows = incomeRowsIn(lines, overview.budgetMonth, incomeMonth)
    return IncomeMonthView(
        incomeRows = rows,
        workingDays = workingDays,
        receipts = receiptsOf(overview, rows, incomeMonth, today, zone),
        duplicateKeys = duplicateKeysOf(lines, rows),
        incomeMonth = incomeMonth,
    )
}

private fun receiptsOf(
    overview: PeriodOverview,
    rows: List<IncomeRow>,
    incomeMonth: IncomeMonth,
    today: LocalDate,
    zone: ZoneId,
): IncomeReceipts {
    val month = overview.budgetMonth
    val received =
        rows.mapNotNull { row ->
            row.receivedOn(month, incomeMonth, overview.monthIncome, zone)?.let { row.key to it }
        }
    return IncomeReceipts(
        // The latest pay date of the rows that pay this month none without rows.
        payday = rows.filter { it.cents > 0L }.maxOfOrNull { it.payDateIn(month, incomeMonth) },
        today = today,
        canReceive = month <= YearMonth.from(today),
        receivedOn = received.toMap(),
        receivedPayday = receivedPaydayOf(rows, received.toMap()),
    )
}

private fun duplicateKeysOf(
    lines: List<PlannedIncome>,
    rows: List<IncomeRow>,
): Set<String> =
    rows
        .filter { row -> row.component == null && row.lines.any { it.looksLikeSalary(lines) } }
        .mapTo(mutableSetOf()) { it.key }

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
