package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Whether a category is referenced by any expense and/or by a budget. */
data class CategoryUsage(
    val hasExpenses: Boolean,
    val hasBudget: Boolean,
) {
    val inUse: Boolean get() = hasExpenses || hasBudget
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
    ) {
        suspend operator fun invoke(id: CategoryId): Result<CategoryUsage> {
            val hasBudget =
                budgetRepository
                    .observeBudgets()
                    .map { budgets -> Result.success(budgets.any { it.categoryId == id }) }
                    .catch { emit(Result.failure(it)) }
                    .first()
            return expenseRepository.hasExpenseWithCategory(id).mapCatching { hasExpenses ->
                CategoryUsage(hasExpenses = hasExpenses, hasBudget = hasBudget.getOrThrow())
            }
        }
    }
