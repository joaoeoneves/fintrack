package com.joaoeoneves.fintrack.ui.savings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.GoalProgress
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.model.cashFlowSummaryOf
import com.joaoeoneves.fintrack.domain.model.goalProgressOf
import com.joaoeoneves.fintrack.domain.model.isEnded
import com.joaoeoneves.fintrack.domain.model.resolve
import com.joaoeoneves.fintrack.domain.model.resolvedWith
import com.joaoeoneves.fintrack.domain.model.savingsRate
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import javax.inject.Inject

private const val TAG = "SavingsViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
private const val MONTHS_IN_YEAR = 12L
private const val FAR_YEARS = 100

/** The repositories the Savings tab is assembled from, bundled for detekt's LongParameterList. */
class SavingsSources
    @Inject
    constructor(
        val goals: GoalRepository,
        val recurring: RecurringRepository,
        val categories: CategoryRepository,
        val transactions: TransactionRepository,
    )

/** The Savings tab: the year's saved total and rate, and one card per active savings plan. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavingsViewModel
    @Inject
    constructor(
        private val sources: SavingsSources,
        private val clock: Clock,
    ) : ViewModel() {
        val uiState: StateFlow<SavingsUiState> =
            combine(sources.goals.observeGoals(), sources.recurring.observeRecurring()) { goals, recurring ->
                goals to recurring
            }.flatMapLatest { (goals, recurring) ->
                val today = LocalDate.now(clock)
                val active = goals.filterNot { it.archived }
                val earliest = active.filter { it.targetType == GoalTargetType.TOTAL }.map { it.startDate }
                val from = (earliest + LocalDate.of(today.year, 1, 1)).min()
                sources.transactions
                    .observeTransactions(rangeFrom(from, clock.zone))
                    .combine(sources.categories.observeCategories()) { transactions, categories ->
                        val expenses = transactions.expenses.resolvedWith(categories)
                        val yearStart = today.withDayOfYear(1)
                        val inYear = { date: Instant -> date.atZone(clock.zone).toLocalDate() >= yearStart }
                        val summary =
                            cashFlowSummaryOf(
                                expenses = expenses.filter { inYear(it.date) },
                                income = transactions.income.filter { inYear(it.date) },
                                categories = categories,
                            )
                        SavingsUiState(
                            loaded = true,
                            year = today.year,
                            savedCents = summary.savedCents,
                            savingsRate = savingsRate(summary.savedCents, summary.incomeCents),
                            cards =
                                active.map { goal ->
                                    val progress = goalProgressOf(goal, expenses, recurring, today, clock.zone)
                                    cardOf(progress, recurring, categories, today)
                                },
                        )
                    }
            }.catch {
                Log.w(TAG, "Failed to observe the savings plans", it)
                emit(SavingsUiState(loaded = true))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), SavingsUiState())
    }

private fun cardOf(
    progress: GoalProgress,
    recurring: List<RecurringItem>,
    categories: List<Category>,
    today: LocalDate,
): SavingsCardUi {
    val item =
        progress.goal
            .ownedItem(recurring)
            ?.takeIf { it.active && !it.isEnded(today) }
    return SavingsCardUi(
        progress = progress,
        category = item?.categoryId?.let { categories.resolve(it) },
        monthlyCents = item?.monthlyCents(),
        dayOfMonth = item?.dayOfMonth,
        suggestedCents = progress.suggestedMonthlyCents(today)?.takeIf { item == null && it > 0L },
    )
}

/** The plan's own recurring item, `null` when it has none or [SavingsGoal.recurringId] dangles. */
internal fun SavingsGoal.ownedItem(recurring: List<RecurringItem>): RecurringItem? =
    recurringId?.let { id -> recurring.firstOrNull { it.id == id } }

/** What the item sets aside a month (a yearly item spread over twelve). */
internal fun RecurringItem.monthlyCents(): Long {
    val months = if (frequency == RecurringFrequency.YEARLY) MONTHS_IN_YEAR else 1L
    return amountCents / months
}

/** From [start] to far in the future: a TOTAL plan counts everything since its start date. */
private fun rangeFrom(
    start: LocalDate,
    zone: ZoneId,
): InstantRange =
    InstantRange(
        start.atStartOfDay(zone).toInstant(),
        Year
            .of(start.year + FAR_YEARS)
            .atDay(1)
            .atStartOfDay(zone)
            .toInstant(),
    )
