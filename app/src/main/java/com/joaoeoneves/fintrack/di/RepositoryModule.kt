package com.joaoeoneves.fintrack.di

import com.joaoeoneves.fintrack.data.CategoryResolvingExpenseRepository
import com.joaoeoneves.fintrack.data.CombinedTransactionRepository
import com.joaoeoneves.fintrack.data.FirebaseAuthRepository
import com.joaoeoneves.fintrack.data.FirestoreBudgetRepository
import com.joaoeoneves.fintrack.data.FirestoreCategoryRepository
import com.joaoeoneves.fintrack.data.FirestoreExpenseRepository
import com.joaoeoneves.fintrack.data.FirestoreIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.AuthRepository
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    @FirestoreExpenses
    fun bindFirestoreExpenseRepository(impl: FirestoreExpenseRepository): ExpenseRepository

    @Binds
    fun bindExpenseRepository(impl: CategoryResolvingExpenseRepository): ExpenseRepository

    @Binds
    fun bindCategoryRepository(impl: FirestoreCategoryRepository): CategoryRepository

    @Binds
    fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    fun bindBudgetRepository(impl: FirestoreBudgetRepository): BudgetRepository

    @Binds
    fun bindIncomeRepository(impl: FirestoreIncomeRepository): IncomeRepository

    @Binds
    fun bindTransactionRepository(impl: CombinedTransactionRepository): TransactionRepository
}
