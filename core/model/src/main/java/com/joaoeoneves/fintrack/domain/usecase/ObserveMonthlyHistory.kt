package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.IncomeMonth
import com.joaoeoneves.fintrack.domain.model.IncomeSourceHistory
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.Leftover
import com.joaoeoneves.fintrack.domain.model.MonthTotals
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.YearToDate
import com.joaoeoneves.fintrack.domain.model.categoryMonthlyOf
import com.joaoeoneves.fintrack.domain.model.incomeHistoryOf
import com.joaoeoneves.fintrack.domain.model.leftoverOf
import com.joaoeoneves.fintrack.domain.model.monthlyTotalsOf
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.domain.model.yearToDateOf
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * The history of the months ending at the observed month: [months] is oldest first, [categoryMonthly] has one
 * value per month for each category with expenses, [incomeSources] is per source and [yearToDate] covers January
 * through the last month.
 */
data class MonthlyHistory(
    val months: List<MonthTotals>,
    val categoryMonthly: Map<CategoryId, List<Long>>,
    val incomeSources: List<IncomeSourceHistory>,
    val yearToDate: YearToDate,
    val leftover: Leftover = Leftover(0L, null, 0),
)

/**
 * Observes the [MonthlyHistory] of the `months` months ending at `endMonth` (inclusive); income and spending are
 * attributed by date.
 */
class ObserveMonthlyHistory
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val sources: PlanDataSources,
    ) {
        operator fun invoke(
            endMonth: YearMonth,
            months: Int = DEFAULT_HISTORY_MONTHS,
            zone: ZoneId,
            today: YearMonth,
        ): Flow<MonthlyHistory> {
            val window = (months - 1 downTo 0).map { endMonth.minusMonths(it.toLong()) }
            val range =
                InstantRange(
                    endMonth.minusMonths(months.toLong()).toInstantRange(zone).startInclusive,
                    endMonth.toInstantRange(zone).endExclusive,
                )
            val inputs =
                combine(
                    sources.plannedIncome.observePlannedIncome(),
                    sources.categories.observeCategories(),
                    sources.monthSettings.observeMonthSettings(),
                ) { lines, categories, settings ->
                    HistoryInputs(lines, categories, IncomeMonth.of(settings))
                }
            return combine(transactionRepository.observeTransactions(range), inputs) { tx, plan ->
                val totals =
                    monthlyTotalsOf(window, tx.expenses, tx.income, plan.categories, zone)
                MonthlyHistory(
                    months = totals,
                    categoryMonthly = categoryMonthlyOf(window, tx.expenses, plan.categories, zone),
                    incomeSources = incomeHistoryOf(window, tx.income, plan.lines, plan.incomeMonth, zone, today),
                    yearToDate = yearToDateOf(totals, endMonth),
                    leftover = leftoverOf(totals, endMonth, today),
                )
            }
        }

        private companion object {
            const val DEFAULT_HISTORY_MONTHS = 12
        }
    }

private class HistoryInputs(
    val lines: List<PlannedIncome>,
    val categories: List<Category>,
    val incomeMonth: IncomeMonth,
)
