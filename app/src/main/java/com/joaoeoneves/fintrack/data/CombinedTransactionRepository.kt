package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    ) : TransactionRepository {
        override fun observeTransactions(range: InstantRange): Flow<TransactionsInRange> =
            combine(
                expenseRepository.observeExpenses(range.startInclusive, range.endExclusive),
                incomeRepository.observeIncome(range.startInclusive, range.endExclusive),
            ) { expenses, income -> TransactionsInRange(range = range, expenses = expenses, income = income) }
    }
