package com.joaoeoneves.fintrack.ui.expense.addedit

import com.joaoeoneves.fintrack.domain.model.mealCardMonthOf
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import java.time.ZoneId

/**
 * For a month, whether its meal-card plafond is used up (the balance of `mealCardMonthOf` is 0 or less). Incomes are
 * read from two months before to the end of the month so the incomes owned by the month before it are all there.
 */
internal fun mealCardPlafondUsedIn(
    expenses: ExpenseRepository,
    incomes: IncomeRepository,
    plannedIncome: PlannedIncomeRepository,
    zone: ZoneId,
): (YearMonth) -> Flow<Boolean> =
    { month ->
        val start = month.atDay(1).atStartOfDay(zone).toInstant()
        val end =
            month
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay(zone)
                .toInstant()
        val incomeStart =
            month
                .minusMonths(2)
                .atDay(1)
                .atStartOfDay(zone)
                .toInstant()
        combine(
            expenses.observeExpenses(start, end),
            incomes.observeIncome(incomeStart, end),
            plannedIncome.observePlannedIncome(),
        ) { monthExpenses, monthIncomes, lines ->
            mealCardMonthOf(month, monthIncomes, monthExpenses, lines, zone).balanceCents <= 0L
        }.catch { emit(false) }
    }
