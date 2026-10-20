package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.MonthlyTotals
import com.joaoeoneves.fintrack.domain.model.monthlyTotalsOf
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Observes per-month totals for every month from `from` to `to` (inclusive), using a single
 * transaction listener spanning the whole range.
 */
class ObserveMonthlyTotals
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
    ) {
        operator fun invoke(
            from: YearMonth,
            to: YearMonth,
            zone: ZoneId,
        ): Flow<List<MonthlyTotals>> {
            if (from > to) return flowOf(emptyList())
            val range =
                InstantRange(
                    startInclusive = from.toInstantRange(zone).startInclusive,
                    endExclusive = to.toInstantRange(zone).endExclusive,
                )
            return transactionRepository.observeTransactions(range).map { tx ->
                monthlyTotalsOf(tx.expenses, tx.income, from, to, zone)
            }
        }
    }
