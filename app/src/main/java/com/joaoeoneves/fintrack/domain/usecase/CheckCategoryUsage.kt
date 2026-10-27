package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Whether a category is referenced by any expense, a budget and/or a recurring item. */
data class CategoryUsage(
    val hasExpenses: Boolean,
    val hasBudget: Boolean,
    val hasRecurring: Boolean = false,
) {
    val inUse: Boolean get() = hasExpenses || hasBudget || hasRecurring
}

/**
 * Checks whether a category is still in use, e.g. before deleting it. Fails when either check
 * can't be completed (when signed in the expense check needs the server, so it fails while
 * offline; as a guest everything is on the device and it always works).
 */
class CheckCategoryUsage
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val budgetRepository: BudgetRepository,
        private val recurringRepository: RecurringRepository,
    ) {
        suspend operator fun invoke(id: CategoryId): Result<CategoryUsage> {
            val hasBudget =
                budgetRepository
                    .observeBudgets()
                    .map { budgets -> Result.success(budgets.any { it.categoryId == id }) }
                    .catch { emit(Result.failure(it)) }
                    .first()
            val hasRecurring =
                recurringRepository
                    .observeRecurring()
                    .map { items -> Result.success(items.any { it.categoryId == id }) }
                    .catch { emit(Result.failure(it)) }
                    .first()
            return expenseRepository.hasExpenseWithCategory(id).mapCatching { hasExpenses ->
                CategoryUsage(
                    hasExpenses = hasExpenses,
                    hasBudget = hasBudget.getOrThrow(),
                    hasRecurring = hasRecurring.getOrThrow(),
                )
            }
        }
    }
