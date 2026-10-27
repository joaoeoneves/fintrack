package com.joaoeoneves.fintrack.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.canGoToNextMonth
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.domain.model.dailyAllowanceOf
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.plannedSlices
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.usecase.ObservePeriodOverview
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.common.messageOr
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

// How long (in ms) the underlying Firestore listeners are kept alive after the last collector
// disappears, so a quick configuration change doesn't tear down and immediately re-establish them.
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

// How many of the most recent expenses/income entries are surfaced in the dashboard's "recent
// activity" preview.
private const val RECENT_ITEMS_LIMIT = 5

// SavedStateHandle key of the selected period token.
private const val KEY_PERIOD = "period"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        private val observePeriodOverview: ObservePeriodOverview,
        private val budgetRepository: BudgetRepository,
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
                .catch { e -> emit(DashboardUiState.Error(e.messageOr(R.string.error_generic_fallback))) }
        }

        fun onPeriodSelected(period: Period) = selection.select(period)

        fun onPreviousMonth() = selection.previous()

        fun onNextMonth() = selection.next()

        fun onRetry() {
            retryTrigger.value++
        }

        private val budgetErrors = Channel<UiText>(Channel.BUFFERED)

        /** One-shot user-facing messages for budget writes that failed, shown as a snackbar. */
        val budgetErrorEvent: Flow<UiText> = budgetErrors.receiveAsFlow()

        fun onSetBudget(
            category: Category,
            limitCents: Long,
        ) {
            viewModelScope.launch {
                budgetRepository.setBudget(category.id, limitCents).onFailure {
                    budgetErrors.send(UiText.StringResource(R.string.error_budget_save_failed))
                }
            }
        }

        fun onClearBudget(category: Category) {
            viewModelScope.launch {
                budgetRepository.clearBudget(category.id).onFailure {
                    budgetErrors.send(UiText.StringResource(R.string.error_budget_remove_failed))
                }
            }
        }

        private fun PeriodOverview.toContent(
            period: Period,
            now: Instant,
            zone: ZoneId,
        ): DashboardUiState.Content {
            val isCurrentMonth = budgetMonth == currentYearMonth(now, zone)
            return DashboardUiState.Content(
                period = period,
                canGoToNextMonth = period.canGoToNextMonth(now, zone),
                budgetMonth = budgetMonth,
                budgetMonthIsCurrent = isCurrentMonth,
                expenses = transactions.expenses,
                categoryTotals = summary.categoryTotals,
                outflowCents = summary.outflowCents,
                spendingCents = summary.spendingCents,
                savedCents = summary.savedCents,
                savingsRate = summary.savingsRate,
                recentExpenses = recent.expenses,
                budgets = budgets,
                incomeCents = summary.incomeCents,
                netCents = summary.netCents,
                recentIncome = recent.income,
                plan = plan,
                plannedSlices = budgets.plannedSlices(),
                dailyAllowance =
                    if (isCurrentMonth) {
                        dailyAllowanceOf(plan.safeToSpendCents, budgetMonth, now.atZone(zone).toLocalDate())
                    } else {
                        null
                    },
            )
        }
    }
