package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import java.time.LocalDate
import javax.inject.Inject

// TEMPORARY stub of the 5.7 contract (Track A owns the real one); dropped at merge.

enum class BulkKind { CATEGORY, MEAL_CARD, DELETE }

data class BulkUndo(
    val kind: BulkKind,
    val previous: List<Expense>,
    val skipped: Map<String, Set<LocalDate>> = emptyMap(),
)

class BulkEditExpenses
    @Inject
    constructor() {
        suspend fun changeCategory(
            expenses: List<Expense>,
            category: Category,
        ): Result<BulkUndo> = Result.success(BulkUndo(BulkKind.CATEGORY, expenses))

        suspend fun setMealCard(
            expenses: List<Expense>,
            enabled: Boolean,
        ): Result<BulkUndo> = Result.success(BulkUndo(BulkKind.MEAL_CARD, expenses))

        suspend fun delete(expenses: List<Expense>): Result<BulkUndo> =
            Result.success(BulkUndo(BulkKind.DELETE, expenses))

        suspend fun undo(undo: BulkUndo): Result<Unit> = Result.success(Unit)
    }
