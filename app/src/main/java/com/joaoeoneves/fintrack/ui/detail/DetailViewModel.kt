package com.joaoeoneves.fintrack.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.IncomeSourceKey
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.SalarySpendComparison
import com.joaoeoneves.fintrack.domain.model.anchorMonth
import com.joaoeoneves.fintrack.domain.model.canGoToNextMonth
import com.joaoeoneves.fintrack.domain.model.canGoToNextYear
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.incomeSourceOf
import com.joaoeoneves.fintrack.domain.model.percentChange
import com.joaoeoneves.fintrack.domain.model.resolve
import com.joaoeoneves.fintrack.domain.model.salarySpendComparisonOf
import com.joaoeoneves.fintrack.domain.model.seriesComparisonOf
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.usecase.MonthlyHistory
import com.joaoeoneves.fintrack.domain.usecase.ObserveMonthlyHistory
import com.joaoeoneves.fintrack.domain.usecase.ObservePeriodOverview
import com.joaoeoneves.fintrack.ui.common.period.PeriodActions
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelection
import com.joaoeoneves.fintrack.ui.common.period.asMonthOrYearPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import javax.inject.Inject

// The route arguments (CategoryDetail / IncomeSourceDetail), read from the SavedStateHandle.
private const val ARG_CATEGORY_ID = "categoryId"
private const val ARG_SOURCE_KEY = "sourceKey"
private const val ARG_PERIOD = "period"

// The selected period (a month or a year), kept across process death.
private const val KEY_SELECTED_PERIOD = "selectedPeriod"

/** Months of the chart on the month scale. */
const val DETAIL_MONTHS = 12

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** What one emission of the page is built from. */
@Suppress("LongParameterList") // one input per combined flow
private class DetailSources(
    val period: Period,
    val months: List<YearMonth>,
    val history: MonthlyHistory,
    val overview: PeriodOverview,
    val categories: List<Category>,
    val canGoNext: Boolean,
    /** The SPENDING total of the month after the selected one, when a salary source needs it. */
    val nextMonthSpendingCents: Long = 0L,
)

/**
 * The detail page of one category or one income source (decided by the route arguments), on the "Mês | Ano" switch.
 * Month: 12 months of bars (tap or the arrows pick the month), that month's figures and movements and how it compares
 * with the earlier ones. Year: the months of the calendar year as bars, the year's total and movements. The selection
 * starts at the route's period.
 */
@Suppress("TooManyFunctions") // one small builder per figure of the page
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val observeMonthlyHistory: ObserveMonthlyHistory,
        private val observePeriodOverview: ObservePeriodOverview,
        private val categoryRepository: CategoryRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val categoryId: String? = savedStateHandle[ARG_CATEGORY_ID]
        private val sourceToken: String? = savedStateHandle[ARG_SOURCE_KEY]
        private val sourceKey: IncomeSourceKey? = sourceToken?.let(::decodeIncomeSourceKey)
        private val routePeriod: Period =
            decodePeriod(savedStateHandle[ARG_PERIOD] ?: Period.CurrentMonth.encode(), clock.instant(), clock.zone)
                .asMonthOrYearPeriod()
        private val routeEnd: YearMonth = routeEndOf(routePeriod, clock.instant(), clock.zone)
        private val isSalarySource: Boolean = (sourceKey as? IncomeSourceKey.Row)?.component != null
        private val selection =
            PeriodSelection(savedStateHandle, KEY_SELECTED_PERIOD, routePeriod.encode(), clock)

        /** The period selector's callbacks (month and year scales). */
        val periodActions: PeriodActions =
            PeriodActions(
                onPrevious = selection::previous,
                onNext = selection::next,
                onSelect = { selection.select(it.asMonthOrYearPeriod()) },
                onSelectScale = selection::selectScale,
            )

        val uiState: StateFlow<DetailUiState> =
            if (categoryId == null && sourceKey == null) {
                flowOf<DetailUiState>(DetailUiState.NotFound)
            } else {
                selection.token
                    .flatMapLatest { token -> sourcesOf(token).map(::contentOf) }
                    .catch { emit(DetailUiState.NotFound) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), DetailUiState.Loading)

        /** Selects [month] on the month scale (one of the chart's months; anything else is ignored). */
        fun onSelectMonth(month: YearMonth) {
            val now = clock.instant()
            val period = decodePeriod(selection.token.value, now, clock.zone)
            val charted = chartMonthsOf(period, routeEnd, DETAIL_MONTHS, now, clock.zone)
            if (period is Period.Year || month !in charted) return
            selection.select(Period.ofMonth(month, now, clock.zone))
        }

        private fun sourcesOf(token: String): Flow<DetailSources> {
            val now = clock.instant()
            val period = decodePeriod(token, now, clock.zone)
            val months = chartMonthsOf(period, routeEnd, DETAIL_MONTHS, now, clock.zone)
            val canGoNext =
                if (period is Period.Year) {
                    period.canGoToNextYear(now, clock.zone)
                } else {
                    period.canGoToNextMonth(now, clock.zone)
                }
            return combine(
                observeMonthlyHistory(months.last(), months.size, clock.zone),
                observePeriodOverview(period, now, clock.zone, recentLimit = 0),
                categoryRepository.observeCategories(),
                nextMonthSpendingOf(period, now),
            ) { history, overview, categories, nextSpending ->
                DetailSources(period, months, history, overview, categories, canGoNext, nextSpending)
            }
        }

        /**
         * The SPENDING total (the Spending tab's figure) of the month after the selected one, for a salary source
         * on the month scale once that month has started; 0 (unused) otherwise.
         */
        private fun nextMonthSpendingOf(
            period: Period,
            now: Instant,
        ): Flow<Long> {
            val month = period.anchorMonth(now, clock.zone)
            val next = month?.plusMonths(1)
            return if (!isSalarySource || next == null || next > currentYearMonth(now, clock.zone)) {
                flowOf(0L)
            } else {
                observePeriodOverview(Period.ofMonth(next, now, clock.zone), now, clock.zone, recentLimit = 0)
                    .map { it.summary.spendingCents }
            }
        }

        private fun contentOf(sources: DetailSources): DetailUiState =
            if (categoryId != null) categoryContent(sources) else sourceContent(sources, requireNotNull(sourceKey))

        private fun selectedMonthOf(sources: DetailSources): YearMonth? {
            val period = sources.period
            return period.anchorMonth(clock.instant(), clock.zone)
        }

        private fun selectedIndexOf(sources: DetailSources): Int =
            selectedMonthOf(sources)?.let { sources.months.indexOf(it) } ?: NO_SELECTION

        /** The figure above the chart: the selected month's value, or the year's total. */
        private fun amountOf(
            sources: DetailSources,
            values: List<Long>,
        ): Long = selectedIndexOf(sources).takeIf { it >= 0 }?.let { values[it] } ?: values.sum()

        /** The change against the earlier months, on the month scale only. */
        private fun deltaOf(
            sources: DetailSources,
            values: List<Long>,
        ): DetailDeltaFigure? {
            val index = selectedIndexOf(sources)
            if (index < 0) return null
            val comparison = seriesComparisonOf(values, index)
            return percentChange(comparison.currentCents, comparison.averageCents)?.let(::DetailDeltaFigure)
        }

        private fun categoryContent(sources: DetailSources): DetailUiState {
            val overview = sources.overview
            val id = CategoryId(requireNotNull(categoryId))
            val category = sources.categories.resolve(id)
            val values = sources.history.categoryMonthly[id] ?: List(sources.months.size) { 0L }
            val movements = overview.transactions.expenses.filter { it.category.id == id }
            return if (category.isUnknown && values.all { it == 0L } && movements.isEmpty()) {
                DetailUiState.NotFound
            } else {
                val rows = overview.envelopes.envelopes.flatMap { it.rows } + overview.envelopes.unenveloped
                val envelope = overview.envelopes.envelopes.firstOrNull { it.envelope.id == category.groupId }
                val isMonth = sources.period !is Period.Year
                DetailUiState.Content(
                    subject =
                        DetailSubject.OfCategory(
                            category = category,
                            envelopeName = envelope?.envelope?.name,
                            // The limit is a monthly figure: not shown on the year scale.
                            limitCents = rows.firstOrNull { it.category.id == id }?.limitOf()?.takeIf { isMonth },
                        ),
                    period = sources.period,
                    canGoToNextMonth = sources.canGoNext,
                    months = sources.months.zip(values) { month, value -> DetailMonth(month, value) },
                    selectedIndex = selectedIndexOf(sources),
                    amountCents = amountOf(sources, values),
                    delta = deltaOf(sources, values),
                    movements = movements.sortedByDescending { it.date },
                    editTarget =
                        DetailEditTarget
                            .CategoryEditor(id.value)
                            .takeUnless { category.archived || category.isUnknown },
                )
            }
        }

        private fun CategoryBudget.limitOf(): Long? = limitCents?.takeIf { it > 0L }

        private fun sourceContent(
            sources: DetailSources,
            key: IncomeSourceKey,
        ): DetailUiState {
            val source = sources.history.incomeSources.firstOrNull { it.key == key }
            val values =
                sources.months.map { month -> source?.months?.firstOrNull { it.month == month }?.receivedCents ?: 0L }
            val movements: List<Income> =
                sources.overview.transactions.income
                    .filter { incomeSourceOf(it, sources.overview.plannedIncome) == key }
                    .sortedByDescending { it.date }
            val component = (key as? IncomeSourceKey.Row)?.component
            val selected = selectedMonthOf(sources)
            return DetailUiState.Content(
                subject =
                    DetailSubject.OfSource(
                        component = component,
                        label = source?.label ?: (key as? IncomeSourceKey.Named)?.normalised,
                        // The expected amount is a monthly figure: not shown on the year scale.
                        expectedCents =
                            source
                                ?.months
                                ?.firstOrNull { it.month == selected }
                                ?.expectedCents
                                ?.takeIf { it > 0L },
                    ),
                period = sources.period,
                canGoToNextMonth = sources.canGoNext,
                months = sources.months.zip(values) { month, value -> DetailMonth(month, value) },
                selectedIndex = selectedIndexOf(sources),
                amountCents = amountOf(sources, values),
                delta = deltaOf(sources, values),
                movements = movements,
                editTarget = editTargetOf(key, sources.period),
                salaryComparison = salaryComparisonOf(sources, values),
            )
        }

        private fun salaryComparisonOf(
            sources: DetailSources,
            values: List<Long>,
        ): SalarySpendComparison? {
            val index = selectedIndexOf(sources)
            return if (!isSalarySource || index < 0) {
                null
            } else {
                salarySpendComparisonOf(
                    month = sources.months[index],
                    receivedCents = values[index],
                    nextMonthSpendingCents = sources.nextMonthSpendingCents,
                    today = currentYearMonth(clock.instant(), clock.zone),
                )
            }
        }

        private fun editTargetOf(
            key: IncomeSourceKey,
            period: Period,
        ): DetailEditTarget? =
            when (key) {
                is IncomeSourceKey.Named -> null
                is IncomeSourceKey.Row ->
                    if (key.component != null) {
                        DetailEditTarget.SalaryProfile
                    } else {
                        DetailEditTarget.IncomeLines(period.encode())
                    }
            }
    }
