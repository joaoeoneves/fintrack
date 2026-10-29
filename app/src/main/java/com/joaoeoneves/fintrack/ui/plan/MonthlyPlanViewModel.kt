package com.joaoeoneves.fintrack.ui.plan

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.IncomeMonth
import com.joaoeoneves.fintrack.domain.model.IncomeRow
import com.joaoeoneves.fintrack.domain.model.MAX_PLANNED_INCOME_LINES
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.model.annualizedCents
import com.joaoeoneves.fintrack.domain.model.asOf
import com.joaoeoneves.fintrack.domain.model.billsOf
import com.joaoeoneves.fintrack.domain.model.canGoToNextMonth
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.effectiveWorkingDays
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.holidayWeekdaysIn
import com.joaoeoneves.fintrack.domain.model.incomeRowsIn
import com.joaoeoneves.fintrack.domain.model.isEnded
import com.joaoeoneves.fintrack.domain.model.isExtra
import com.joaoeoneves.fintrack.domain.model.isManaged
import com.joaoeoneves.fintrack.domain.model.looksLikeSalary
import com.joaoeoneves.fintrack.domain.model.monthlyEquivalentCents
import com.joaoeoneves.fintrack.domain.model.payDateIn
import com.joaoeoneves.fintrack.domain.model.payingLinesByPayMonth
import com.joaoeoneves.fintrack.domain.model.receivedOn
import com.joaoeoneves.fintrack.domain.model.salaryComponent
import com.joaoeoneves.fintrack.domain.model.workingDaysIn
import com.joaoeoneves.fintrack.domain.usecase.ObservePeriodOverview
import com.joaoeoneves.fintrack.domain.usecase.ReceiveRequest
import com.joaoeoneves.fintrack.domain.usecase.RecordPayday
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

private const val TAG = "MonthlyPlanViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
private const val KEY_SELECTED_MONTH = "selectedMonth"
private const val ROUTE_MONTH_ARG = "month"

/** The monthly plan: expected-income lines and the month's figures, envelope cards and set-up status. */
@Suppress("TooManyFunctions") // One handler per user action on the screen.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MonthlyPlanViewModel
    @Inject
    constructor(
        private val repositories: PlanRepositories,
        private val observePeriodOverview: ObservePeriodOverview,
        private val recordPayday: RecordPayday,
        savedStateHandle: SavedStateHandle,
        private val clock: Clock,
    ) : ViewModel() {
        // The selected month, month periods only; the plan itself is global, the month only decides
        // which actuals the "planned vs actual" progress and the fixed-spending line are measured on.
        private val selection =
            PeriodSelection(
                savedStateHandle,
                KEY_SELECTED_MONTH,
                // The income screen is opened for a month (route argument); the Plan tab has none.
                savedStateHandle.get<String>(ROUTE_MONTH_ARG) ?: Period.CurrentMonth.encode(),
                clock,
                PLAN_MONTHS_AHEAD,
            )
        private val editor = MutableStateFlow<IncomeLineEditorState?>(null)
        private val errors = Channel<UiText>(Channel.BUFFERED)
        private val deletedLines = Channel<PlannedIncome>(Channel.BUFFERED)

        /** Each manual income line just deleted, for an undo snackbar. */
        val deletedLineEvent: Flow<PlannedIncome> = deletedLines.receiveAsFlow()

        /** One-shot user-facing messages for plan writes that failed, shown as a snackbar. */
        val errorEvent: Flow<UiText> = errors.receiveAsFlow()

        private val retryTrigger = MutableStateFlow(0)

        val uiState: StateFlow<MonthlyPlanUiState> =
            combine(selection.token, retryTrigger) { token, _ -> token }
                // "Now" is read on every (re)subscription so a retry after the calendar rolls over
                // re-resolves CurrentMonth and canGoToNextMonth.
                .flatMapLatest { token ->
                    val now = clock.instant()
                    val period = decodePeriod(token, now, clock.zone, PLAN_MONTHS_AHEAD)
                    val overview = observePeriodOverview(period, now, clock.zone, recentLimit = 0)
                    planFlow(
                        repositories = repositories,
                        selected =
                            SelectedMonth(
                                period = period,
                                canGoToNextMonth = period.canGoToNextMonth(now, clock.zone, PLAN_MONTHS_AHEAD),
                                overview = overview,
                                today = LocalDate.now(clock),
                                zone = clock.zone,
                            ),
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
                            history = amountHistoryNote(line.versions),
                            initialSchedule =
                                IncomeSchedule(
                                    basis = line.basis,
                                    frequency = line.frequency,
                                    monthOfYear = line.monthOfYear,
                                    yearMonth = line.yearMonth,
                                    skipMonths = line.skipMonths,
                                    earmark = line.earmark,
                                    autoRecord = line.autoRecord,
                                    payDay = line.payDay,
                                ),
                        )
                }
            }
        }

        /**
         * Records [row] of the selected month as received on [date]; [amountCents] replaces the amount of a
         * single-line row. A no-op until the plan has loaded.
         */
        fun onReceive(
            row: IncomeRow,
            date: LocalDate,
            amountCents: Long?,
        ) {
            val content = uiState.value as? MonthlyPlanUiState.Content ?: return
            viewModelScope.launch {
                val byPayMonth = row.payingLinesByPayMonth(content.workingDays.month, content.incomeMonth)
                for ((payMonth, lines) in byPayMonth) {
                    val request =
                        ReceiveRequest(
                            lines = lines,
                            month = payMonth,
                            workingDays = content.incomeMonth.workingDays(payMonth),
                            date = date,
                            today = content.receipts.today,
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
            val content = uiState.value as? MonthlyPlanUiState.Content ?: return
            viewModelScope.launch {
                val byPayMonth = row.payingLinesByPayMonth(content.workingDays.month, content.incomeMonth)
                for ((payMonth, lines) in byPayMonth) {
                    val result = recordPayday.unreceive(lines, payMonth, clock.zone)
                    if (result.isFailure) {
                        result.onFailure { reportFailure(it) }
                        break
                    }
                }
            }
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
            Log.w(TAG, "Failed to save the monthly plan", error)
            errors.send(UiText.StringResource(R.string.error_plan_save_failed))
        }
    }

private suspend fun PlanRepositories.currentLines(): List<PlannedIncome> = plannedIncome.observePlannedIncome().first()

/** The month the plan is currently evaluated against, with its [overview] flow. */
private class SelectedMonth(
    val period: Period,
    val canGoToNextMonth: Boolean,
    val overview: Flow<PeriodOverview>,
    val today: LocalDate,
    val zone: ZoneId,
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
        combine(repositories.monthSettings.observeMonthSettings(), editor, repositories.goals.observeGoals(), ::Triple),
    ) { lines, _, categories, overview, (settings, editorState, goals) ->
        val workingDays = workingDaysOf(overview.budgetMonth, settings)
        val incomeMonth = IncomeMonth.of(settings)
        val rows = incomeRowsIn(lines, overview.budgetMonth, incomeMonth)
        val content: MonthlyPlanUiState =
            MonthlyPlanUiState.Content(
                incomeLines = lines,
                incomeRows = rows,
                // The overview computes the plan once for Home, Movimentos and this screen alike.
                summary = overview.plan,
                period = selected.period,
                canGoToNextMonth = selected.canGoToNextMonth,
                canAddIncomeLine = lines.count { !it.isManaged } < MAX_PLANNED_INCOME_LINES,
                workingDays = workingDays,
                editor = editorState,
                envelopes = overview.envelopes,
                extraIncomeCents = overview.plan.extrasExpectedCents,
                envelopeCards = envelopeCardsOf(overview.envelopes),
                configure = configureOf(lines, categories, overview, workingDays.days, goals),
                receipts = receiptsOf(selected, overview, rows, incomeMonth),
                duplicateKeys = duplicateKeysOf(lines, rows),
                incomeMonth = incomeMonth,
            )
        content
    }.catch { e ->
        Log.w(TAG, "Failed to observe the monthly plan", e)
        emit(MonthlyPlanUiState.Error(UiText.StringResource(R.string.error_generic_fallback)))
    }

private fun configureOf(
    lines: List<PlannedIncome>,
    categories: List<Category>,
    overview: PeriodOverview,
    workingDays: Int,
    goals: List<SavingsGoal>,
): ConfigureStatus {
    // A savings plan's monthly item is the plan's, not a bill.
    val today = overview.budgetMonth.atDay(1)
    val bills = billsOf(overview.recurring, goals).filter { it.active && !it.isEnded(today) }
    val incomeMonth = IncomeMonth.forMonth(overview.budgetMonth, workingDays)
    val regular = lines.asOf(overview.budgetMonth).filterNot { it.isExtra }
    return ConfigureStatus(
        incomeMonthlyCents = regular.sumOf { incomeMonth.amountIn(it, overview.budgetMonth) },
        incomeLineCount = regular.size,
        envelopeCount = overview.envelopes.envelopes.size,
        billCount = bills.size,
        billsMonthlyCents = monthlyEquivalentCents(annualizedCents(bills, RecurringType.EXPENSE, today)),
        categoryCount = categories.count { !it.archived },
    )
}

private fun receiptsOf(
    selected: SelectedMonth,
    overview: PeriodOverview,
    rows: List<IncomeRow>,
    incomeMonth: IncomeMonth,
): IncomeReceipts {
    val month = overview.budgetMonth
    val received =
        rows.mapNotNull { row ->
            row.receivedOn(month, incomeMonth, overview.monthIncome, selected.zone)?.let { row.key to it }
        }
    return IncomeReceipts(
        // The latest pay date of the rows that pay this month none without rows.
        payday = rows.filter { it.cents > 0L }.maxOfOrNull { it.payDateIn(month, incomeMonth) },
        today = selected.today,
        canReceive = month <= YearMonth.from(selected.today),
        receivedOn = received.toMap(),
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

/** The plan views can open this many months after the current one: a salary paid now funds next month. */
internal const val PLAN_MONTHS_AHEAD = 1
