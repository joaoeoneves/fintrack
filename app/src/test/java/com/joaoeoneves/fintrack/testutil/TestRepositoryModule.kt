package com.joaoeoneves.fintrack.testutil

import com.joaoeoneves.fintrack.data.CategoryResolvingExpenseRepository
import com.joaoeoneves.fintrack.data.CombinedTransactionRepository
import com.joaoeoneves.fintrack.data.session.DefaultSessionRepository
import com.joaoeoneves.fintrack.data.session.SessionBudgetRepository
import com.joaoeoneves.fintrack.data.session.SessionCategoryRepository
import com.joaoeoneves.fintrack.data.session.SessionDataResetRepository
import com.joaoeoneves.fintrack.data.session.SessionExpenseRepository
import com.joaoeoneves.fintrack.data.session.SessionIncomeRepository
import com.joaoeoneves.fintrack.di.RawExpenses
import com.joaoeoneves.fintrack.di.RepositoryModule
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.DataResetRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** Lets a full-app test hold every transaction read (so screens stay in Loading) until it is released. */
@Singleton
class TransactionGate
    @Inject
    constructor() {
        @Volatile
        var hold: CompletableDeferred<Unit>? = null
    }

private class GatedTransactionRepository(
    private val delegate: TransactionRepository,
    private val gate: TransactionGate,
) : TransactionRepository {
    override fun observeTransactions(range: InstantRange): Flow<TransactionsInRange> =
        flow {
            gate.hold?.await()
            emitAll(delegate.observeTransactions(range))
        }
}

/** Same bindings as the production [RepositoryModule], except transactions go through [TransactionGate]. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])
abstract class TestRepositoryModule {
    @Binds
    @RawExpenses
    abstract fun bindRawExpenseRepository(impl: SessionExpenseRepository): ExpenseRepository

    @Binds
    abstract fun bindExpenseRepository(impl: CategoryResolvingExpenseRepository): ExpenseRepository

    @Binds
    abstract fun bindCategoryRepository(impl: SessionCategoryRepository): CategoryRepository

    @Binds
    abstract fun bindBudgetRepository(impl: SessionBudgetRepository): BudgetRepository

    @Binds
    abstract fun bindIncomeRepository(impl: SessionIncomeRepository): IncomeRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: DefaultSessionRepository): SessionRepository

    @Binds
    abstract fun bindDataResetRepository(impl: SessionDataResetRepository): DataResetRepository

    companion object {
        @Provides
        fun provideTransactionRepository(
            combined: CombinedTransactionRepository,
            gate: TransactionGate,
        ): TransactionRepository = GatedTransactionRepository(combined, gate)
    }
}
