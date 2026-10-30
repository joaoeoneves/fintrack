package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.di.IoDispatcher
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What moving a category's items would touch: [expenseCount] expenses and [recurringCount] recurring items. */
data class CategoryMovePreview(
    val expenseCount: Int,
    val recurringCount: Int,
) {
    val isEmpty: Boolean get() = expenseCount == 0 && recurringCount == 0
}

/** What a move changed. */
data class CategoryMoveResult(
    val expenseCount: Int,
    val recurringCount: Int,
)

/**
 * Moves everything that references a category (expenses and recurring items) to another one, e.g. to retire a
 * category without losing data. The steps run one after the other and stop at the first failure; running the move
 * again finishes what is left. Signed in this needs the network.
 */
class MoveCategoryItems
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val recurringRepository: RecurringRepository,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        /** Counts what moving [source] would touch. */
        suspend fun preview(source: CategoryId): Result<CategoryMovePreview> =
            withContext(dispatcher) {
                catching {
                    val recurring = recurringRepository.observeRecurring().first()
                    CategoryMovePreview(
                        expenseCount = expenseRepository.countExpensesWithCategory(source).getOrThrow(),
                        recurringCount = recurring.count { it.categoryId == source },
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
                    CategoryMoveResult(expenses, recurring)
                }
            }
    }

/** Like [runCatching] but rethrows cancellation instead of capturing it. */
private inline fun <T> catching(block: () -> T): Result<T> {
    val result = runCatching(block)
    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    return result
}
