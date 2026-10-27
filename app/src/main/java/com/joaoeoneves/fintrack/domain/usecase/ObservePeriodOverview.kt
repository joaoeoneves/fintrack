package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import com.joaoeoneves.fintrack.domain.model.anchorMonth
import com.joaoeoneves.fintrack.domain.model.budgetProgressOf
import com.joaoeoneves.fintrack.domain.model.cashFlowSummaryOf
import com.joaoeoneves.fintrack.domain.model.contains
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.domain.model.encloses
import com.joaoeoneves.fintrack.domain.model.monthlyPlanOf
import com.joaoeoneves.fintrack.domain.model.recentActivity
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Observes the [PeriodOverview] of a [Period]. Budgets and the monthly plan are evaluated against
 * the period's anchor month, or the current month for rolling/custom periods. When the period's
 * range already covers that whole month, the month's spend and income are derived from the period's
 * own transactions instead of opening a second listener.
 */
class ObservePeriodOverview
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val budgetRepository: BudgetRepository,
        private val plannedIncomeRepository: PlannedIncomeRepository,
        private val categoryRepository: CategoryRepository,
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
            val budgets = budgetRepository.observeBudgets()
            val plannedIncome = plannedIncomeRepository.observePlannedIncome()
            val categories = categoryRepository.observeCategories()
            return if (range.encloses(budgetRange)) {
                combine(transactions, budgets, plannedIncome, categories) { tx, budgetList, lines, catalog ->
                    val month =
                        tx.copy(
                            expenses = tx.expenses.filter { it.date in budgetRange },
                            income = tx.income.filter { it.date in budgetRange },
                        )
                    overviewOf(budgetMonth, tx, month, budgetList, lines, catalog, recentLimit)
                }
            } else {
                combine(
                    transactions,
                    transactionRepository.observeTransactions(budgetRange),
                    budgets,
                    plannedIncome,
                    categories,
                ) { tx, month, budgetList, lines, catalog ->
                    overviewOf(budgetMonth, tx, month, budgetList, lines, catalog, recentLimit)
                }
            }
        }

        @Suppress("LongParameterList") // private assembler of one value; a holder type would add nothing
        private fun overviewOf(
            budgetMonth: YearMonth,
            transactions: TransactionsInRange,
            monthTransactions: TransactionsInRange,
            budgets: List<Budget>,
            plannedIncome: List<PlannedIncome>,
            categories: List<Category>,
            recentLimit: Int,
        ): PeriodOverview {
            val rows = budgetProgressOf(monthTransactions.expenses, budgets, categories)
            return PeriodOverview(
                budgetMonth = budgetMonth,
                transactions = transactions,
                summary = cashFlowSummaryOf(transactions.expenses, transactions.income, categories),
                budgets = rows,
                plan = monthlyPlanOf(rows, plannedIncome, monthTransactions.income),
                recent = transactions.recentActivity(recentLimit),
            )
        }
    }
