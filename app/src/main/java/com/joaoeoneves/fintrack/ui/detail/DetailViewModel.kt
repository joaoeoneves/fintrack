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
import com.joaoeoneves.fintrack.domain.model.anchorMonth
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.incomeSourceOf
import com.joaoeoneves.fintrack.domain.model.percentChange
import com.joaoeoneves.fintrack.domain.model.resolve
import com.joaoeoneves.fintrack.domain.model.seriesComparisonOf
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.usecase.MonthlyHistory
import com.joaoeoneves.fintrack.domain.usecase.ObserveMonthlyHistory
import com.joaoeoneves.fintrack.domain.usecase.ObservePeriodOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

// The route arguments (CategoryDetail / IncomeSourceDetail), read from the SavedStateHandle.
private const val ARG_CATEGORY_ID = "categoryId"
private const val ARG_SOURCE_KEY = "sourceKey"
private const val ARG_PERIOD = "period"

// The selected month ("yyyy-MM"), kept across process death.
private const val KEY_MONTH = "month"

/** Months of the chart. */
const val DETAIL_MONTHS = 12

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * The detail page of one category or one income source (decided by the route arguments): 12 months of bars ending at
 * the route's month, the movements of the selected month and how that month compares with the earlier ones.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        observeMonthlyHistory: ObserveMonthlyHistory,
        private val observePeriodOverview: ObservePeriodOverview,
        categoryRepository: CategoryRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val categoryId: String? = savedStateHandle[ARG_CATEGORY_ID]
        private val sourceToken: String? = savedStateHandle[ARG_SOURCE_KEY]
        private val sourceKey: IncomeSourceKey? = sourceToken?.let(::decodeIncomeSourceKey)
        private val periodToken: String = savedStateHandle[ARG_PERIOD] ?: Period.CurrentMonth.encode()
        private val endMonth: YearMonth = monthOf(periodToken)
        private val window: List<YearMonth> = (DETAIL_MONTHS - 1 downTo 0).map { endMonth.minusMonths(it.toLong()) }

        private val selectedMonth =
            savedStateHandle.getStateFlow(KEY_MONTH, endMonth.toString())

        val uiState: StateFlow<DetailUiState> =
            if (categoryId == null && sourceKey == null) {
                flowOf<DetailUiState>(DetailUiState.NotFound)
            } else {
                combine(
                    observeMonthlyHistory(endMonth, DETAIL_MONTHS, clock.zone),
                    selectedMonth.flatMapLatest { overviewOf(selectedOf(it)) },
                    categoryRepository.observeCategories(),
                    selectedMonth,
                ) { history, overview, categories, selected ->
                    contentOf(history, overview, categories, selectedOf(selected))
                }.catch { emit(DetailUiState.NotFound) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), DetailUiState.Loading)

        /** Selects [month] (one of the chart's months; anything else is ignored). */
        fun onSelectMonth(month: YearMonth) {
            if (month in window) savedStateHandle[KEY_MONTH] = month.toString()
        }

        private fun monthOf(token: String): YearMonth {
            val now = clock.instant()
            val period = decodePeriod(token, now, clock.zone)
            return period.anchorMonth(now, clock.zone) ?: currentYearMonth(now, clock.zone)
        }

        private fun selectedOf(token: String): YearMonth =
            runCatching { YearMonth.parse(token) }.getOrNull()?.takeIf { it in window } ?: endMonth

        private fun overviewOf(month: YearMonth) =
            clock.instant().let { now ->
                observePeriodOverview(Period.ofMonth(month, now, clock.zone), now, clock.zone, recentLimit = 0)
            }

        private fun contentOf(
            history: MonthlyHistory,
            overview: PeriodOverview,
            categories: List<Category>,
            selected: YearMonth,
        ): DetailUiState {
            val index = window.indexOf(selected).coerceAtLeast(0)
            return if (categoryId != null) {
                categoryContent(history, overview, categories, index)
            } else {
                sourceContent(history, overview, requireNotNull(sourceKey), index)
            }
        }

        private fun categoryContent(
            history: MonthlyHistory,
            overview: PeriodOverview,
            categories: List<Category>,
            index: Int,
        ): DetailUiState {
            val id = CategoryId(requireNotNull(categoryId))
            val category = categories.resolve(id)
            val values = history.categoryMonthly[id] ?: List(DETAIL_MONTHS) { 0L }
            val movements = overview.transactions.expenses.filter { it.category.id == id }
            return if (category.isUnknown && values.all { it == 0L } && movements.isEmpty()) {
                DetailUiState.NotFound
            } else {
                val rows = overview.envelopes.envelopes.flatMap { it.rows } + overview.envelopes.unenveloped
                val envelope = overview.envelopes.envelopes.firstOrNull { it.envelope.id == category.groupId }
                DetailUiState.Content(
                    subject =
                        DetailSubject.OfCategory(
                            category = category,
                            envelopeName = envelope?.envelope?.name,
                            limitCents = rows.firstOrNull { it.category.id == id }?.limitOf(),
                        ),
                    months = window.zip(values) { month, value -> DetailMonth(month, value) },
                    selectedIndex = index,
                    delta = deltaOf(values, index),
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
            history: MonthlyHistory,
            overview: PeriodOverview,
            key: IncomeSourceKey,
            index: Int,
        ): DetailUiState {
            val source = history.incomeSources.firstOrNull { it.key == key }
            val values = window.map { month -> source?.months?.firstOrNull { it.month == month }?.receivedCents ?: 0L }
            val movements: List<Income> =
                overview.monthIncome
                    .filter { incomeSourceOf(it, overview.plannedIncome) == key }
                    .sortedByDescending { it.date }
            val component = (key as? IncomeSourceKey.Row)?.component
            return DetailUiState.Content(
                subject =
                    DetailSubject.OfSource(
                        component = component,
                        label = source?.label ?: (key as? IncomeSourceKey.Named)?.normalised,
                        expectedCents =
                            source
                                ?.months
                                ?.firstOrNull { it.month == window[index] }
                                ?.expectedCents
                                ?.takeIf { it > 0L },
                    ),
                months = window.zip(values) { month, value -> DetailMonth(month, value) },
                selectedIndex = index,
                delta = deltaOf(values, index),
                movements = movements,
                editTarget = editTargetOf(key),
            )
        }

        private fun editTargetOf(key: IncomeSourceKey): DetailEditTarget? =
            when (key) {
                is IncomeSourceKey.Named -> null
                is IncomeSourceKey.Row ->
                    if (key.component != null) {
                        DetailEditTarget.SalaryProfile
                    } else {
                        DetailEditTarget.IncomeLines(periodToken)
                    }
            }

        private fun deltaOf(
            values: List<Long>,
            index: Int,
        ): DetailDeltaFigure? {
            val comparison = seriesComparisonOf(values, index)
            return percentChange(comparison.currentCents, comparison.averageCents)?.let(::DetailDeltaFigure)
        }
    }
