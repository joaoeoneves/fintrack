package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.budgetProgressOf
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** Observes every category's budget progress for a calendar month (expenses only, no income). */
class ObserveBudgetProgress
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val budgetRepository: BudgetRepository,
    ) {
        operator fun invoke(
            month: YearMonth,
            zone: ZoneId,
        ): Flow<List<CategoryBudget>> {
            val monthRange = month.toInstantRange(zone)
            return combine(
                expenseRepository.observeExpenses(monthRange.startInclusive, monthRange.endExclusive),
                budgetRepository.observeBudgets(),
            ) { monthExpenses, budgets -> budgetProgressOf(monthExpenses, budgets) }
        }
    }
