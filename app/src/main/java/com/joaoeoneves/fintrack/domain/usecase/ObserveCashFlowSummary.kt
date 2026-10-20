package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.CashFlowSummary
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.cashFlowSummaryOf
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Observes the [CashFlowSummary] of the transactions within a range. */
class ObserveCashFlowSummary
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
    ) {
        operator fun invoke(range: InstantRange): Flow<CashFlowSummary> =
            transactionRepository.observeTransactions(range).map { cashFlowSummaryOf(it.expenses, it.income) }
    }
