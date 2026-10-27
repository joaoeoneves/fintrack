package com.joaoeoneves.fintrack.ui.insights

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.canGoToNextMonth
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.plannedSlices
import com.joaoeoneves.fintrack.domain.model.spendingSplitOf
import com.joaoeoneves.fintrack.domain.usecase.ObservePeriodOverview
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelection
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
import javax.inject.Inject

private const val TAG = "InsightsViewModel"

// Keeps the Firestore listeners alive briefly across configuration changes.
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

// The route argument is only the initial value; later choices live under a separate key so the
// argument itself is never overwritten.
private const val ARG_PERIOD = "period"
private const val KEY_SELECTED_PERIOD = "selectedPeriod"

/** Observes the category breakdown (actual vs planned) of a selectable month, rolling or custom period. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel
    @Inject
    constructor(
        private val observePeriodOverview: ObservePeriodOverview,
        savedStateHandle: SavedStateHandle,
        private val clock: Clock,
    ) : ViewModel() {
        // The route's "period" argument is only the initial value (null: the current month).
        private val selection =
            PeriodSelection(
                savedStateHandle,
                KEY_SELECTED_PERIOD,
                savedStateHandle.get<String>(ARG_PERIOD) ?: Period.CurrentMonth.encode(),
                clock,
            )
        private val retryTrigger = MutableStateFlow(0)

        val uiState: StateFlow<InsightsUiState> =
            combine(selection.token, retryTrigger) { token, _ -> token }
                // "Now" is read on every (re)subscription so a retry after the calendar rolls over
                // re-resolves CurrentMonth and canGoToNextMonth.
                .flatMapLatest { token ->
                    val now = clock.instant()
                    contentFlow(decodePeriod(token, now, clock.zone), now)
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    initialValue = InsightsUiState.Loading,
                )

        private fun contentFlow(
            period: Period,
            now: Instant,
        ): Flow<InsightsUiState> =
            observePeriodOverview(period, now, clock.zone, recentLimit = 0)
                .map<PeriodOverview, InsightsUiState> { overview ->
                    InsightsUiState.Content(
                        period = period,
                        canGoToNextMonth = period.canGoToNextMonth(now, clock.zone),
                        breakdown =
                            InsightsBreakdown(
                                categoryTotals = overview.summary.categoryTotals,
                                plannedSlices = overview.budgets.plannedSlices(),
                                hasExpenses = overview.transactions.expenses.isNotEmpty(),
                                groups = overview.periodGroups,
                            ),
                        split = spendingSplitOf(overview.transactions.expenses),
                    )
                }.catch { e ->
                    Log.w(TAG, "Insights flow failed", e)
                    emit(InsightsUiState.Error(UiText.StringResource(R.string.error_generic_fallback)))
                }

        fun onPeriodSelected(period: Period) = selection.select(period)

        fun onPreviousMonth() = selection.previous()

        fun onNextMonth() = selection.next()

        fun onRetry() {
            retryTrigger.value++
        }
    }
