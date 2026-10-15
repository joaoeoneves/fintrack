package com.joaoeoneves.fintrack.di

import com.joaoeoneves.fintrack.data.FirebaseAuthRepository
import com.joaoeoneves.fintrack.data.FirestoreBudgetRepository
import com.joaoeoneves.fintrack.data.FirestoreExpenseRepository
import com.joaoeoneves.fintrack.data.FirestoreIncomeRepository
import com.joaoeoneves.fintrack.data.SharedPrefsCurrencyRepository
import com.joaoeoneves.fintrack.data.SharedPrefsLanguageRepository
import com.joaoeoneves.fintrack.data.SharedPrefsThemeRepository
import com.joaoeoneves.fintrack.domain.repository.AuthRepository
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.CurrencyRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.LanguageRepository
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    fun bindExpenseRepository(impl: FirestoreExpenseRepository): ExpenseRepository

    @Binds
    fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    fun bindBudgetRepository(impl: FirestoreBudgetRepository): BudgetRepository

    @Binds
    fun bindIncomeRepository(impl: FirestoreIncomeRepository): IncomeRepository

    @Binds
    @Singleton
    fun bindThemeRepository(impl: SharedPrefsThemeRepository): ThemeRepository

    @Binds
    @Singleton
    fun bindCurrencyRepository(impl: SharedPrefsCurrencyRepository): CurrencyRepository

    @Binds
    @Singleton
    fun bindLanguageRepository(impl: SharedPrefsLanguageRepository): LanguageRepository
}
