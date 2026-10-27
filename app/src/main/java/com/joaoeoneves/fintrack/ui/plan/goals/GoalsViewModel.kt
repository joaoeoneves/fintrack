package com.joaoeoneves.fintrack.ui.plan.goals

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.MAX_GOAL_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.model.annualizedCents
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.model.goalProgressOf
import com.joaoeoneves.fintrack.domain.model.isEnded
import com.joaoeoneves.fintrack.domain.model.limitInput
import com.joaoeoneves.fintrack.domain.model.resolvedWith
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import javax.inject.Inject

private const val TAG = "GoalsViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** The repositories the goals are assembled from, bundled for detekt's LongParameterList. */
class GoalsSources
    @Inject
    constructor(
        val goals: GoalRepository,
        val recurring: RecurringRepository,
        val categories: CategoryRepository,
        val transactions: TransactionRepository,
    )

/** Goals of the Plan tab: their progress, plus add, edit, delete and "Create from investments". */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GoalsViewModel
    @Inject
    constructor(
        private val sources: GoalsSources,
        private val clock: Clock,
    ) : ViewModel() {
        private val errors = Channel<UiText>(Channel.BUFFERED)

        /** One-shot user-facing messages for goal writes that failed, shown as a snackbar. */
        val errorEvent: Flow<UiText> = errors.receiveAsFlow()

        val uiState: StateFlow<GoalsUiState> =
            combine(sources.goals.observeGoals(), sources.recurring.observeRecurring()) { goals, recurring ->
                goals to recurring
            }.flatMapLatest { (goals, recurring) ->
                val today = LocalDate.now(clock)
                val active = goals.filterNot { it.archived }
                val earliest = (active.filter { it.targetType == GoalTargetType.TOTAL }.map { it.startDate } + today)
                sources.transactions
                    .observeTransactions(rangeFrom(minOf(earliest.min(), LocalDate.of(today.year, 1, 1)), clock.zone))
                    .combine(sources.categories.observeCategories()) { transactions, categories ->
                        val expenses = transactions.expenses.resolvedWith(categories)
                        GoalsUiState(
                            loaded = true,
                            goals = active.map { goalProgressOf(it, expenses, recurring, today, clock.zone) },
                            options = optionsOf(recurring, categories),
                            investmentCandidates = investmentCandidates(recurring, goals, today),
                        )
                    }
            }.catch {
                Log.w(TAG, "Failed to observe the goals", it)
                emit(GoalsUiState(loaded = true))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), GoalsUiState())

        /** Saves [goal] (a blank id creates it, at the end of the list); an invalid one is ignored. */
        fun onSave(goal: SavingsGoal) {
            val trimmed = goal.copy(name = goal.name.trim())
            if (!isValid(trimmed)) return
            write {
                val order = if (trimmed.id.isBlank()) nextOrder() else trimmed.order
                sources.goals.saveGoal(trimmed.copy(order = order))
            }
        }

        fun onDelete(id: String) {
            write { sources.goals.deleteGoal(id) }
        }

        /** One ANNUAL goal per candidate item, named like it and aiming at its annualized amount. */
        fun onCreateFromInvestments() {
            val candidates = uiState.value.investmentCandidates
            if (candidates.isEmpty()) return
            val today = LocalDate.now(clock)
            write {
                runCatching {
                    val firstOrder = nextOrder()
                    candidates.forEachIndexed { index, item ->
                        sources.goals.saveGoal(item.toGoal(today, firstOrder + index)).getOrThrow()
                    }
                }
            }
        }

        private fun nextOrder(): Int =
            uiState.value.goals
                .maxOfOrNull { it.goal.order }
                ?.plus(1) ?: 0

        private fun write(block: suspend () -> Result<*>) {
            viewModelScope.launch {
                block().onFailure {
                    Log.w(TAG, "Failed to save the goals", it)
                    errors.send(UiText.StringResource(R.string.error_goal_save_failed))
                }
            }
        }
    }

private fun isValid(goal: SavingsGoal): Boolean {
    val targetDate = goal.targetDate
    return goal.name.isNotEmpty() &&
        goal.name.codePointLength() <= MAX_GOAL_NAME_LENGTH &&
        goal.targetCents > 0 &&
        goal.initialCents >= 0 &&
        (targetDate == null || targetDate >= goal.startDate)
}

private const val FAR_YEARS = 100

/** From [start] to far in the future: a TOTAL goal counts everything since its start date. */
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

private fun optionsOf(
    recurring: List<RecurringItem>,
    categories: List<Category>,
): GoalEditorOptions =
    GoalEditorOptions(
        recurring = recurring.filter { it.type == RecurringType.EXPENSE }.sortedBy { it.name.lowercase() },
        categories = categories.filterNot { it.archived || it.isUnknown },
    )

private fun investmentCandidates(
    recurring: List<RecurringItem>,
    goals: List<SavingsGoal>,
    today: LocalDate,
): List<RecurringItem> {
    val linked = goals.flatMapTo(mutableSetOf()) { it.linkedRecurringIds }
    return recurring
        .filter {
            it.type == RecurringType.EXPENSE &&
                it.active &&
                !it.isEnded(today) &&
                it.categoryId == BuiltInCategories.INVESTMENTS.id &&
                it.id !in linked
        }.sortedWith(compareBy({ it.startDate }, { it.id }))
}

private fun RecurringItem.toGoal(
    today: LocalDate,
    order: Int,
): SavingsGoal =
    SavingsGoal(
        id = "",
        name = limitInput("", name.trim(), MAX_GOAL_NAME_LENGTH),
        targetCents = annualizedCents(listOf(this), RecurringType.EXPENSE, today),
        targetType = GoalTargetType.ANNUAL,
        startDate = today,
        linkedRecurringIds = setOf(id),
        order = order,
    )
