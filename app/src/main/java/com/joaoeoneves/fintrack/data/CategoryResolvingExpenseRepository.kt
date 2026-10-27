package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.di.RawExpenses
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.resolvedWith
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

/**
 * [ExpenseRepository] decorator that replaces each expense's category with its current entry in
 * the category catalog (so renames, icon/color/kind changes and user categories show up), and
 * re-emits whenever the catalog changes. Everything else (writes, usage checks) is delegated as is.
 */
class CategoryResolvingExpenseRepository
    @Inject
    constructor(
        @param:RawExpenses private val delegate: ExpenseRepository,
        private val categoryRepository: CategoryRepository,
    ) : ExpenseRepository by delegate {
        override fun observeExpenses(
            startInclusive: Instant,
            endExclusive: Instant,
        ): Flow<List<Expense>> =
            combine(
                delegate.observeExpenses(startInclusive, endExclusive),
                categoryRepository.observeCategories(),
            ) { expenses, categories -> expenses.resolvedWith(categories) }

        override suspend fun getExpense(id: String): Result<Expense?> =
            delegate.getExpense(id).map { expense -> expense?.let { resolve(listOf(it)).single() } }

        override suspend fun getAllExpenses(): Result<List<Expense>> = delegate.getAllExpenses().map { resolve(it) }

        private suspend fun resolve(expenses: List<Expense>): List<Expense> =
            expenses.resolvedWith(categoryRepository.observeCategories().first())
    }
