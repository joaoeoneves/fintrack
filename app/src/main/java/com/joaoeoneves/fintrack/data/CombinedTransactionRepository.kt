package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import com.joaoeoneves.fintrack.domain.model.filterOwnedIn
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import javax.inject.Inject

/**
 * [TransactionRepository] built on the expense and income repositories: one listener each per
 * collected flow, combined into a [TransactionsInRange]. Stateless, so it needs no scoping.
 */
class CombinedTransactionRepository
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val incomeRepository: IncomeRepository,
        private val clock: Clock = Clock.systemDefaultZone(),
    ) : TransactionRepository {
        // Income is fetched a month wider on each side and re-attributed by owner month, so a payday income dated
        // outside its own month still counts in it (and not in the month it was dated in).
        override fun observeTransactions(range: InstantRange): Flow<TransactionsInRange> {
            val zone = clock.zone
            val start =
                range.startInclusive
                    .atZone(zone)
                    .minusMonths(1)
                    .toInstant()
            val end =
                range.endExclusive
                    .atZone(zone)
                    .plusMonths(1)
                    .toInstant()
            return combine(
                expenseRepository.observeExpenses(range.startInclusive, range.endExclusive),
                incomeRepository.observeIncome(start, end),
            ) { expenses, income ->
                TransactionsInRange(range = range, expenses = expenses, income = filterOwnedIn(income, range, zone))
            }
        }
    }
