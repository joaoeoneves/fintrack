package com.joaoeoneves.fintrack.ui.dashboard

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.canGoToNextMonth
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.usecase.ObservePeriodOverview
import com.joaoeoneves.fintrack.ui.common.UiText
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
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
            return observePeriodOverview(period, now, zone, RECENT_ITEMS_LIMIT)
                .map<PeriodOverview, DashboardUiState> { overview -> overview.toContent(period, now, zone) }
                .catch { e ->
                    Log.w(TAG, "Dashboard flow failed", e)
                    emit(DashboardUiState.Error(UiText.StringResource(R.string.error_generic_fallback)))
                }
        }

        /** Home is month-only: rolling and custom periods (now on Activity) fall back to the current month. */
        fun onPeriodSelected(period: Period) = selection.select(period.asMonthPeriod())

        fun onPreviousMonth() = selection.previous()

        fun onNextMonth() = selection.next()

        fun onRetry() {
            retryTrigger.value++
        }

        private fun PeriodOverview.toContent(
            period: Period,
            now: Instant,
            zone: ZoneId,
        ): DashboardUiState.Content {
            val isCurrentMonth = budgetMonth == currentYearMonth(now, zone)
            val savingIds =
                summary.categoryTotals
                    .filter { it.category.kind == CategoryKind.SAVING }
                    .map { it.category.id }
                    .toSet()
            return DashboardUiState.Content(
                period = period,
                canGoToNextMonth = period.canGoToNextMonth(now, zone),
                budgetMonth = budgetMonth,
                budgetMonthIsCurrent = isCurrentMonth,
                expenses = transactions.expenses,
                chartExpenses = transactions.expenses.filter { it.category.id !in savingIds },
                income = transactions.income,
                range = transactions.range,
                outflowCents = summary.outflowCents,
                spendingCents = summary.spendingCents,
                fixedSpendingCents = summary.fixedSpendingCents,
                savedCents = summary.savedCents,
                savingsRate = summary.savingsRate,
                recent = mergeRecent(recent.expenses, recent.income),
                incomeCents = summary.incomeCents,
                netCents = summary.netCents,
                plan = plan,
                envelopes = envelopes,
            )
        }
    }
