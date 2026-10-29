package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.di.IoDispatcher
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * What moving a category's items would touch: [expenseCount] expenses, [recurringCount] recurring
 * items and the budget/plan allocation ([hasBudget]). [targetHasBudget] (only known when a target was
 * given) means the target already has its own budget, which then wins over the source's.
 */
data class CategoryMovePreview(
    val expenseCount: Int,
    val recurringCount: Int,
    val hasBudget: Boolean,
    val targetHasBudget: Boolean,
    /** Savings goals linked to the category (their link moves too). */
    val goalCount: Int = 0,
) {
    val isEmpty: Boolean get() = expenseCount == 0 && recurringCount == 0 && !hasBudget && goalCount == 0
}

/** What a move changed; [budgetMoved] is whether the source had a budget (merged per the target's rule). */
data class CategoryMoveResult(
    val expenseCount: Int,
    val recurringCount: Int,
    val budgetMoved: Boolean,
    val goalCount: Int = 0,
)

/**
 * Moves everything that references a category (expenses, the budget which is also the monthly-plan
 * allocation, and recurring items) to another one, e.g. to retire a category without losing data. When
 * the target already has a budget it is kept and the source's is dropped. The steps run one after the
 * other and stop at the first failure; running the move again finishes what is left. Signed in this
 * needs the network.
 */
class MoveCategoryItems
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val budgetRepository: BudgetRepository,
        private val recurringRepository: RecurringRepository,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
        // Last, with a no-goals default for callers that build the use case by hand; Hilt always injects the real one.
        private val goalRepository: GoalRepository = NoGoalRepository,
    ) {
        /** Counts what moving [source] (optionally into [target]) would touch. */
        suspend fun preview(
            source: CategoryId,
            target: CategoryId? = null,
        ): Result<CategoryMovePreview> =
            withContext(dispatcher) {
                catching {
                    val budgets = budgetRepository.observeBudgets().first()
                    val recurring = recurringRepository.observeRecurring().first()
                    CategoryMovePreview(
                        expenseCount = expenseRepository.countExpensesWithCategory(source).getOrThrow(),
                        recurringCount = recurring.count { it.categoryId == source },
                        hasBudget = budgets.any { it.categoryId == source },
                        targetHasBudget = target != null && budgets.any { it.categoryId == target },
                        goalCount = goalRepository.observeGoals().first().count { it.linkedCategoryId == source },
                    )
                }
            }

        suspend operator fun invoke(
            source: CategoryId,
            target: CategoryId,
        ): Result<CategoryMoveResult> =
            withContext(dispatcher) {
                catching {
                    require(source != target) { "Source and target category are the same" }
                    val expenses = expenseRepository.moveExpensesToCategory(source, target).getOrThrow()
                    val recurring = recurringRepository.moveRecurringCategory(source, target).getOrThrow()
                    val budget = budgetRepository.moveBudget(source, target).getOrThrow()
                    val goals = moveGoalLinks(source, target)
                    CategoryMoveResult(expenses, recurring, budget, goals)
                }
            }

        /** Points the goals linked to [source] at [target]; returns how many. Stops at the first failure. */
        private suspend fun moveGoalLinks(
            source: CategoryId,
            target: CategoryId,
        ): Int {
            val linked = goalRepository.observeGoals().first().filter { it.linkedCategoryId == source }
            linked.forEach { goalRepository.saveGoal(it.copy(linkedCategoryId = target)).getOrThrow() }
            return linked.size
        }
    }

private object NoGoalRepository : GoalRepository {
    override fun observeGoals() = flowOf(emptyList<SavingsGoal>())

    override suspend fun saveGoal(goal: SavingsGoal): Result<String> = Result.success(goal.id)

    override suspend fun deleteGoal(id: String): Result<Unit> = Result.success(Unit)
}

/** Like [runCatching] but rethrows cancellation instead of capturing it. */
private inline fun <T> catching(block: () -> T): Result<T> {
    val result = runCatching(block)
    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    return result
}
