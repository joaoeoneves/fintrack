package com.joaoeoneves.fintrack.ui.spending

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.canGoToNextMonth
import com.joaoeoneves.fintrack.domain.model.canGoToNextYear
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.usecase.MonthlyHistory
import com.joaoeoneves.fintrack.domain.usecase.ObserveMonthlyHistory
import com.joaoeoneves.fintrack.domain.usecase.ObservePeriodOverview
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityAnalytics
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityHistory
import com.joaoeoneves.fintrack.ui.activity.analytics.analyticsOf
import com.joaoeoneves.fintrack.ui.activity.analytics.periodMonthsOf
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.common.period.PeriodActions
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelection
import com.joaoeoneves.fintrack.ui.common.period.asMonthOrYearPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

private const val ARG_PERIOD = "period"
private const val KEY_SELECTED_PERIOD = "selectedPeriod"
private const val TAG = "SpendingTabViewModel"
private const val HISTORY_MONTHS = 12

// How long (in ms) the listeners outlive the last collector, so a configuration change doesn't re-subscribe.
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

private sealed interface PeriodData {
    data class Loaded(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val overview: PeriodOverview,
    ) : PeriodData

    data class Failed(
        val message: UiText,
    ) : PeriodData
}

/**
 * The Spending tab: the figures, bars and breakdowns of one month or year, SAVING-kind categories left out. It owns
 * its own period (seeded from the route) and the history listener, which is only active while the tab is open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SpendingTabViewModel
    @Inject
    constructor(
        private val observePeriodOverview: ObservePeriodOverview,
        private val observeMonthlyHistory: ObserveMonthlyHistory,
        categoryRepository: CategoryRepository,
        savedStateHandle: SavedStateHandle,
        private val clock: Clock,
    ) : ViewModel() {
        private val selection =
            PeriodSelection(
                savedStateHandle = savedStateHandle,
                key = KEY_SELECTED_PERIOD,
                initialToken = savedStateHandle.get<String>(ARG_PERIOD) ?: Period.CurrentMonth.encode(),
                clock = clock,
            )
        private val retryTrigger = MutableStateFlow(0)
        private val categories: Flow<List<Category>> =
            categoryRepository.observeCategories().catch { emit(BuiltInCategories.all) }

        private val periodData: Flow<PeriodData> =
            combine(selection.token, retryTrigger) { token, _ -> token }
                .flatMapLatest { token ->
                    val now = clock.instant()
                    val period = decodePeriod(token, now, clock.zone)
                    val canGoToNextMonth =
                        if (period is Period.Year) {
                            period.canGoToNextYear(now, clock.zone)
                        } else {
                            period.canGoToNextMonth(now, clock.zone)
                        }
                    observePeriodOverview(period, now, clock.zone, recentLimit = 0)
                        .map<PeriodOverview, PeriodData> { PeriodData.Loaded(period, canGoToNextMonth, it) }
                        .catch { e ->
                            Log.w(TAG, "Spending flow failed", e)
                            emit(PeriodData.Failed(UiText.StringResource(R.string.error_generic_fallback)))
                        }
                }

        // The window ends at the period's last month and is at least a year long; it only re-subscribes when one of
        // those two changes.
        private val historyWindow: Flow<Pair<YearMonth, Int>> =
            combine(selection.token, retryTrigger) { token, _ -> token }
                .map { token ->
                    val now = clock.instant()
                    val months = periodMonthsOf(decodePeriod(token, now, clock.zone), now, clock.zone)
                    months.last() to maxOf(HISTORY_MONTHS, months.size)
                }.distinctUntilChanged()

        private val history: Flow<MonthlyHistory?> =
            historyWindow.flatMapLatest { (end, months) ->
                observeMonthlyHistory(end, months, clock.zone)
                    .map<MonthlyHistory, MonthlyHistory?> { it }
                    .catch { e ->
                        Log.w(TAG, "History flow failed", e)
                        emit(null)
                    }
            }

        val periodActions =
            PeriodActions(
                onPrevious = selection::previous,
                onNext = selection::next,
                onSelect = { selection.select(it.asMonthOrYearPeriod()) },
                onSelectScale = selection::selectScale,
            )

        val uiState: StateFlow<SpendingTabUiState> =
            combine(periodData, history, categories) { data, monthly, catalog ->
                when (data) {
                    is PeriodData.Loaded -> data.toContent(monthly, catalog)
                    is PeriodData.Failed -> SpendingTabUiState.Error(data.message)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = SpendingTabUiState.Loading,
            )

        fun onRetry() {
            retryTrigger.value++
        }

        private fun PeriodData.Loaded.toContent(
            monthly: MonthlyHistory?,
            catalog: List<Category>,
        ): SpendingTabUiState.Content {
            val months = periodMonthsOf(period, clock.instant(), clock.zone)
            return SpendingTabUiState.Content(
                period = period,
                canGoToNextMonth = canGoToNextMonth,
                analytics = overview.analyticsOf(selectedEnvelope = null, catalog = catalog).withoutSaving(),
                groups = overview.periodEnvelopes.envelopes.map { SpendingGroup(it.envelope.id, it.envelope.name) },
                history =
                    monthly
                        ?.takeIf { it.months.lastOrNull()?.month == months.last() }
                        ?.let { ActivityHistory(months, period is Period.CurrentMonth || period is Period.Month, it) },
            )
        }
    }

/** The analytics of spending only: SAVING-kind categories leave the category list. */
private fun ActivityAnalytics.withoutSaving(): ActivityAnalytics =
    copy(
        split = split.copy(savingCents = 0L),
        breakdown =
            breakdown.copy(
                categoryTotals = breakdown.categoryTotals.filter { it.category.kind != CategoryKind.SAVING },
                hasExpenses = breakdown.categoryTotals.any { it.category.kind != CategoryKind.SAVING },
            ),
    )
