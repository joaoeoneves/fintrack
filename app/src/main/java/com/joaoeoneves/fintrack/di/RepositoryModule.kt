package com.joaoeoneves.fintrack.di

import com.joaoeoneves.fintrack.data.FirebaseAuthRepository
import com.joaoeoneves.fintrack.data.FirestoreBudgetRepository
import com.joaoeoneves.fintrack.data.FirestoreExpenseRepository
import com.joaoeoneves.fintrack.data.FirestoreIncomeRepository
import com.joaoeoneves.fintrack.data.SharedPrefsThemeRepository
import com.joaoeoneves.fintrack.domain.repository.AuthRepository
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

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

    @Binds
    abstract fun bindThemeRepository(impl: SharedPrefsThemeRepository): ThemeRepository
}
