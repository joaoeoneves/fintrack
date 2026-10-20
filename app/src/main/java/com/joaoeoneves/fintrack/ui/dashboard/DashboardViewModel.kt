package com.joaoeoneves.fintrack.ui.dashboard

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.anchorMonth
import com.joaoeoneves.fintrack.domain.model.canGoToNextMonth
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.netCents
import com.joaoeoneves.fintrack.domain.model.savingsRate
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.time.YearMonth
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

/** The resolved, period-derived header fields of [DashboardUiState.Content]. */
private data class PeriodHeader(
    val period: Period,
    val canGoToNextMonth: Boolean,
    val budgetMonth: YearMonth,
    val budgetMonthIsCurrent: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val budgetRepository: BudgetRepository,
        private val incomeRepository: IncomeRepository,
        savedStateHandle: SavedStateHandle,
        private val clock: Clock,
        // Nullable with a default so existing unit tests that construct this ViewModel directly
        // (bypassing Hilt) keep compiling; Hilt itself always supplies a real ApplicationContext in
        // production. When null (test-only), the fallback below matches the exact literal those
        // tests assert on.
        @param:ApplicationContext private val context: Context? = null,
    ) : ViewModel() {
        private val selection =
            PeriodSelection(savedStateHandle, KEY_PERIOD, Period.CurrentMonth.encode(), clock)
        private val retryTrigger = MutableStateFlow(0)

        private fun budgetProgress(
            month: YearMonth,
            zone: ZoneId,
        ): Flow<List<CategoryBudget>> {
            val monthRange = month.toInstantRange(zone)
            return combine(
                expenseRepository.observeExpenses(monthRange.startInclusive, monthRange.endExclusive),
                budgetRepository.observeBudgets(),
            ) { monthExpenses, budgets ->
                ExpenseCategory.entries.map { category ->
                    CategoryBudget(
                        category = category,
                        limitCents = budgets.find { it.category == category }?.limitCents,
                        spentCents =
                            monthExpenses
                                .filter { it.category == category }
                                .netCents()
                                .coerceAtLeast(0),
                    )
                }
            }
        }

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
            val currentMonth = currentYearMonth(now, zone)
            val budgetMonth = period.anchorMonth(now, zone) ?: currentMonth
            val header =
                PeriodHeader(
                    period = period,
                    canGoToNextMonth = period.canGoToNextMonth(now, zone),
                    budgetMonth = budgetMonth,
                    budgetMonthIsCurrent = budgetMonth == currentMonth,
                )
            val range = period.toInstantRange(now, zone)
            return combine(
                expenseRepository.observeExpenses(range.startInclusive, range.endExclusive),
                budgetProgress(budgetMonth, zone),
                incomeRepository.observeIncome(range.startInclusive, range.endExclusive),
            ) { expenses, budgets, income -> expenses.toContent(header, budgets, income) }
                .map<DashboardUiState.Content, DashboardUiState> { content -> content }
                .catch { e ->
                    val fallback =
                        context?.getString(R.string.error_generic_fallback) ?: "Something went wrong"
                    emit(DashboardUiState.Error(e.message ?: fallback))
                }
        }

        fun onPeriodSelected(period: Period) = selection.select(period)

        fun onPreviousMonth() = selection.previous()

        fun onNextMonth() = selection.next()

        fun onRetry() {
            retryTrigger.value++
        }

        private val budgetErrors = Channel<String>(Channel.BUFFERED)

        /** One-shot user-facing messages for budget writes that failed, shown as a snackbar. */
        val budgetErrorEvent: Flow<String> = budgetErrors.receiveAsFlow()

        fun onSetBudget(
            category: ExpenseCategory,
            limitCents: Long,
        ) {
            viewModelScope.launch {
                budgetRepository.setBudget(category, limitCents).onFailure {
                    budgetErrors.send(context?.getString(R.string.error_budget_save_failed) ?: "Failed to save budget")
                }
            }
        }

        fun onClearBudget(category: ExpenseCategory) {
            viewModelScope.launch {
                budgetRepository.clearBudget(category).onFailure {
                    budgetErrors.send(
                        context?.getString(R.string.error_budget_remove_failed) ?: "Failed to remove budget",
                    )
                }
            }
        }

        private fun List<Expense>.toContent(
            header: PeriodHeader,
            budgets: List<CategoryBudget>,
            income: List<Income>,
        ): DashboardUiState.Content {
            val categoryTotals =
                ExpenseCategory.entries.map { category ->
                    CategoryTotal(
                        category = category,
                        totalCents = filter { it.category == category }.netCents(),
                    )
                }
            val spendingCents = filter { it.category.kind == CategoryKind.SPENDING }.netCents()
            val savedCents = filter { it.category.kind == CategoryKind.SAVING }.netCents()
            val incomeCents = income.sumOf { it.amountCents }
            return DashboardUiState.Content(
                period = header.period,
                canGoToNextMonth = header.canGoToNextMonth,
                budgetMonth = header.budgetMonth,
                budgetMonthIsCurrent = header.budgetMonthIsCurrent,
                expenses = this,
                categoryTotals = categoryTotals,
                outflowCents = spendingCents + savedCents,
                spendingCents = spendingCents,
                savedCents = savedCents,
                savingsRate = savingsRate(savedCents = savedCents, incomeCents = incomeCents),
                recentExpenses = sortedByDescending { it.date }.take(RECENT_ITEMS_LIMIT),
                budgets = budgets,
                incomeCents = incomeCents,
                netCents = incomeCents - spendingCents - savedCents,
                recentIncome = income.sortedByDescending { it.date }.take(RECENT_ITEMS_LIMIT),
            )
        }
    }
