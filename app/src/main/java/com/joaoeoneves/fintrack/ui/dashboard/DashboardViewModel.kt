package com.joaoeoneves.fintrack.ui.dashboard

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.anchorMonth
import com.joaoeoneves.fintrack.domain.model.canGoToNextMonth
import com.joaoeoneves.fintrack.domain.model.canGoToNextYear
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.nextYear
import com.joaoeoneves.fintrack.domain.model.percentChange
import com.joaoeoneves.fintrack.domain.model.previousYear
import com.joaoeoneves.fintrack.domain.model.spendingComparisonOf
import com.joaoeoneves.fintrack.domain.usecase.MonthlyHistory
import com.joaoeoneves.fintrack.domain.usecase.ObserveMonthlyHistory
import com.joaoeoneves.fintrack.domain.usecase.ObservePeriodOverview
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.common.period.PeriodScale
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelection
import com.joaoeoneves.fintrack.ui.common.period.asMonthPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

private const val TAG = "DashboardViewModel"

// How long (in ms) the underlying Firestore listeners are kept alive after the last collector
// disappears, so a quick configuration change doesn't tear down and immediately re-establish them.
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

// SavedStateHandle key of the selected period token.
private const val KEY_PERIOD = "period"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        private val observePeriodOverview: ObservePeriodOverview,
        private val observeMonthlyHistory: ObserveMonthlyHistory,
        savedStateHandle: SavedStateHandle,
        private val clock: Clock,
    ) : ViewModel() {
        private val selection =
            PeriodSelection(savedStateHandle, KEY_PERIOD, Period.CurrentMonth.encode(), clock)
        private val retryTrigger = MutableStateFlow(0)

        val uiState: StateFlow<DashboardUiState> =
            combine(selection.token, retryTrigger) { token, _ -> token }
                // "Now" is read here, on every (re)subscription, so an ON_RESUME retry after the
                // calendar rolls over re-resolves CurrentMonth and canGoToNextMonth.
                .flatMapLatest { token ->
                    val now = clock.instant()
                    contentFlow(decodePeriod(token, now, clock.zone), now)
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    initialValue = DashboardUiState.Loading,
                )

        private fun contentFlow(
            period: Period,
            now: Instant,
        ): Flow<DashboardUiState> {
            val zone = clock.zone
            val today = currentYearMonth(now, zone)
            val endMonth = endMonthOf(period, today, zone, now)
            return combine(
                observePeriodOverview(period, now, zone, RECENT_ITEMS_LIMIT),
                observeMonthlyHistory(endMonth, HISTORY_MONTHS, zone, today),
            ) { overview, history -> contentOf(overview, history, ViewedPeriod(period, endMonth, today), now) }
                .catch { e ->
                    Log.w(TAG, "Dashboard flow failed", e)
                    emit(DashboardUiState.Error(UiText.StringResource(R.string.error_generic_fallback)))
                }
        }

        /** Home knows months and years: rolling and custom periods (now on Activity) fall back to the current month. */
        fun onPeriodSelected(period: Period) {
            selection.select(if (period is Period.Year) period else period.asMonthPeriod())
        }

        fun onSelectScale(scale: PeriodScale) = selection.selectScale(scale)

        fun onPreviousMonth() {
            val current = currentPeriod()
            if (current is Period.Year) selection.select(current.previousYear()) else selection.previous()
        }

        fun onNextMonth() {
            val current = currentPeriod()
            if (current is Period.Year) {
                selection.select(current.nextYear(clock.instant(), clock.zone))
            } else {
                selection.next()
            }
        }

        fun onRetry() {
            retryTrigger.value++
        }

        private fun currentPeriod(): Period = decodePeriod(selection.token.value, clock.instant(), clock.zone)

        private fun endMonthOf(
            period: Period,
            today: YearMonth,
            zone: ZoneId,
            now: Instant,
        ): YearMonth =
            when (period) {
                is Period.Year -> if (period.year >= today.year) today else YearMonth.of(period.year, DECEMBER)
                else -> period.anchorMonth(now, zone) ?: today
            }

        private fun contentOf(
            overview: PeriodOverview,
            history: MonthlyHistory,
            viewed: ViewedPeriod,
            now: Instant,
        ): DashboardUiState {
            val (period, endMonth, today) = viewed
            val isYear = period is Period.Year
            val months =
                if (isYear) (1..endMonth.monthValue).map { YearMonth.of(endMonth.year, it) } else listOf(endMonth)
            val expected =
                history.incomeSources
                    .sumOf { source -> source.months.filter { it.month in months }.sumOf { it.expectedCents ?: 0L } }
                    .takeIf { it > 0L }
            val monthSaved = history.months.lastOrNull()?.savedCents ?: 0L
            val yearSaved = history.yearToDate.savedCents
            val comparison = spendingComparisonOf(history.months, endMonth)
            return DashboardUiState.Content(
                period = period,
                canGoToNextMonth =
                    if (isYear) period.canGoToNextYear(now, clock.zone) else period.canGoToNextMonth(now, clock.zone),
                budgetMonth = endMonth,
                budgetMonthIsCurrent = endMonth == today,
                incomeCents = overview.summary.incomeCents,
                expectedIncomeCents = expected,
                spendingCents = overview.summary.spendingCents,
                spendingVsAveragePercent =
                    if (isYear) null else percentChange(overview.summary.spendingCents, comparison.averageCents),
                invested =
                    InvestedFigure(if (isYear) null else monthSaved, yearSaved, endMonth.year)
                        .takeIf { (it.monthCents ?: 0L) != 0L || yearSaved != 0L },
                leftover =
                    history.leftover.averageCents?.let { LeftoverFigure(it, minOf(endMonth, today.minusMonths(1))) },
                recent = mergeRecent(overview.recent.expenses, overview.recent.income),
                spendingAverageMonths = comparison.averageMonths,
            )
        }
    }

private const val HISTORY_MONTHS = 12
private const val DECEMBER = 12

/** The viewed [period], the last month it covers and the current month. */
private data class ViewedPeriod(
    val period: Period,
    val endMonth: YearMonth,
    val today: YearMonth,
)
