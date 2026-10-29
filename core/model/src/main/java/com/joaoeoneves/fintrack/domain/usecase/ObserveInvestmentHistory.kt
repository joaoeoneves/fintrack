package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.InvestmentHistory
import com.joaoeoneves.fintrack.domain.model.investmentHistoryOf
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * The investments page's data: the [history] of the observed window, what was invested in `endMonth`'s year up to
 * `endMonth` ([yearCents]) and ever up to `endMonth` ([allTimeCents]).
 */
data class InvestmentOverview(
    val history: InvestmentHistory,
    val yearCents: Long,
    val allTimeCents: Long,
)

/**
 * Observes the [InvestmentOverview] of the `months` months ending at `endMonth` (inclusive). One expense listener
 * reads everything up to the end of `endMonth` (the all-time total needs it), and only while observed.
 */
class ObserveInvestmentHistory
    @Inject
    constructor(
        private val expenses: ExpenseRepository,
        private val categories: CategoryRepository,
    ) {
        operator fun invoke(
            endMonth: YearMonth,
            months: Int = DEFAULT_MONTHS,
            zone: ZoneId = ZoneId.systemDefault(),
        ): Flow<InvestmentOverview> {
            val window = (months - 1 downTo 0).map { endMonth.minusMonths(it.toLong()) }
            val end = endMonth.toInstantRange(zone).endExclusive
            val source = expenses.observeExpenses(Instant.EPOCH, end)
            return combine(source, categories.observeCategories()) { all, catalog ->
                val first = all.minOfOrNull { YearMonth.from(it.date.atZone(zone)) } ?: endMonth
                val sinceJanuary = (1..endMonth.monthValue).map { YearMonth.of(endMonth.year, it) }
                val ever = generateSequence(minOf(first, endMonth)) { it.plusMonths(1) }.takeWhile { it <= endMonth }
                InvestmentOverview(
                    history = investmentHistoryOf(window, all, catalog, zone),
                    yearCents = investmentHistoryOf(sinceJanuary, all, catalog, zone).totalCents,
                    allTimeCents = investmentHistoryOf(ever.toList(), all, catalog, zone).totalCents,
                )
            }
        }

        private companion object {
            const val DEFAULT_MONTHS = 12
        }
    }
