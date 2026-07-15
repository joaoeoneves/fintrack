package ptech.joaoe.agenticusage.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ptech.joaoe.agenticusage.data.FirebaseAuthRepository
import ptech.joaoe.agenticusage.data.FirestoreBudgetRepository
import ptech.joaoe.agenticusage.data.FirestoreExpenseRepository
import ptech.joaoe.agenticusage.data.FirestoreIncomeRepository
import ptech.joaoe.agenticusage.domain.repository.AuthRepository
import ptech.joaoe.agenticusage.domain.repository.BudgetRepository
import ptech.joaoe.agenticusage.domain.repository.ExpenseRepository
import ptech.joaoe.agenticusage.domain.repository.IncomeRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindExpenseRepository(impl: FirestoreExpenseRepository): ExpenseRepository

    @Binds
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    abstract fun bindBudgetRepository(impl: FirestoreBudgetRepository): BudgetRepository

    @Binds
    abstract fun bindIncomeRepository(impl: FirestoreIncomeRepository): IncomeRepository
}
