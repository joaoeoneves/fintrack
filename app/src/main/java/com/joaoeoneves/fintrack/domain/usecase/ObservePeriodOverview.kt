package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import com.joaoeoneves.fintrack.domain.model.anchorMonth
import com.joaoeoneves.fintrack.domain.model.budgetProgressOf
import com.joaoeoneves.fintrack.domain.model.cashFlowSummaryOf
import com.joaoeoneves.fintrack.domain.model.contains
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.domain.model.encloses
import com.joaoeoneves.fintrack.domain.model.recentActivity
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Observes the [PeriodOverview] of a [Period]. Budgets are evaluated against the period's anchor
 * month, or the current month for rolling/custom periods. When the period's range already covers
 * that whole month, budget spend is derived from the period's own expenses instead of opening a
 * second expense listener.
 */
class ObservePeriodOverview
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val budgetRepository: BudgetRepository,
        private val observeBudgetProgress: ObserveBudgetProgress,
    ) {
        operator fun invoke(
            period: Period,
            now: Instant,
            zone: ZoneId,
            recentLimit: Int,
        ): Flow<PeriodOverview> {
            val range = period.toInstantRange(now, zone)
            val budgetMonth = period.anchorMonth(now, zone) ?: currentYearMonth(now, zone)
            val budgetRange = budgetMonth.toInstantRange(zone)
            val transactions = transactionRepository.observeTransactions(range)
            return if (range.encloses(budgetRange)) {
                combine(transactions, budgetRepository.observeBudgets()) { tx, budgets ->
                    val monthExpenses = tx.expenses.filter { it.date in budgetRange }
                    overviewOf(budgetMonth, tx, budgetProgressOf(monthExpenses, budgets), recentLimit)
                }
            } else {
                combine(transactions, observeBudgetProgress(budgetMonth, zone)) { tx, budgets ->
                    overviewOf(budgetMonth, tx, budgets, recentLimit)
                }
            }
        }

        private fun overviewOf(
            budgetMonth: YearMonth,
            transactions: TransactionsInRange,
            budgets: List<CategoryBudget>,
            recentLimit: Int,
        ): PeriodOverview =
            PeriodOverview(
                budgetMonth = budgetMonth,
                transactions = transactions,
                summary = cashFlowSummaryOf(transactions.expenses, transactions.income),
                budgets = budgets,
                recent = transactions.recentActivity(recentLimit),
            )
    }
