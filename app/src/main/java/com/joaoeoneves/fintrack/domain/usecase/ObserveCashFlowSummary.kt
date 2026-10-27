package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.CashFlowSummary
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.cashFlowSummaryOf
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Observes the [CashFlowSummary] of the transactions within a range. */
class ObserveCashFlowSummary
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val categoryRepository: CategoryRepository,
    ) {
        operator fun invoke(range: InstantRange): Flow<CashFlowSummary> =
            combine(
                transactionRepository.observeTransactions(range),
                categoryRepository.observeCategories(),
            ) { tx, categories -> cashFlowSummaryOf(tx.expenses, tx.income, categories) }
    }
