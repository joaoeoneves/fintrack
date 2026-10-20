package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import kotlinx.coroutines.flow.Flow

/** Read-only view over expenses and income together, for a given time range. */
interface TransactionRepository {
    fun observeTransactions(range: InstantRange): Flow<TransactionsInRange>
}
