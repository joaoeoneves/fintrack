package com.joaoeoneves.fintrack.di

import com.joaoeoneves.fintrack.data.CategoryResolvingExpenseRepository
import com.joaoeoneves.fintrack.data.CombinedTransactionRepository
import com.joaoeoneves.fintrack.data.FirebaseAuthRepository
import com.joaoeoneves.fintrack.data.session.DefaultSessionRepository
import com.joaoeoneves.fintrack.data.session.SessionBudgetRepository
import com.joaoeoneves.fintrack.data.session.SessionCategoryRepository
import com.joaoeoneves.fintrack.data.session.SessionExpenseRepository
import com.joaoeoneves.fintrack.data.session.SessionIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.AuthRepository
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    @RawExpenses
    fun bindRawExpenseRepository(impl: SessionExpenseRepository): ExpenseRepository

    @Binds
    fun bindExpenseRepository(impl: CategoryResolvingExpenseRepository): ExpenseRepository

    @Binds
    fun bindCategoryRepository(impl: SessionCategoryRepository): CategoryRepository

    @Binds
    fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    fun bindBudgetRepository(impl: SessionBudgetRepository): BudgetRepository

    @Binds
    fun bindIncomeRepository(impl: SessionIncomeRepository): IncomeRepository

    @Binds
    @Singleton
    fun bindSessionRepository(impl: DefaultSessionRepository): SessionRepository

    @Binds
    fun bindTransactionRepository(impl: CombinedTransactionRepository): TransactionRepository
}
