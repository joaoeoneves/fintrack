package com.joaoeoneves.fintrack.di

import com.joaoeoneves.fintrack.data.AppBuiltInCategoryNames
import com.joaoeoneves.fintrack.data.AppPlaceholderNames
import com.joaoeoneves.fintrack.data.AppSalaryLineLabels
import com.joaoeoneves.fintrack.data.AppStarterCategoryNames
import com.joaoeoneves.fintrack.domain.model.StarterCategoryNames
import com.joaoeoneves.fintrack.domain.repository.BuiltInCategoryNames
import com.joaoeoneves.fintrack.domain.repository.PlaceholderNamesProvider
import com.joaoeoneves.fintrack.domain.repository.SalaryLineLabelsProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface CategoryNamesModule {
    @Binds
    @Singleton
    fun bindBuiltInCategoryNames(impl: AppBuiltInCategoryNames): BuiltInCategoryNames

    @Binds
    @Singleton
    fun bindStarterCategoryNames(impl: AppStarterCategoryNames): StarterCategoryNames

    @Binds
    @Singleton
    fun bindPlaceholderNames(impl: AppPlaceholderNames): PlaceholderNamesProvider

    @Binds
    @Singleton
    fun bindSalaryLineLabels(impl: AppSalaryLineLabels): SalaryLineLabelsProvider
}
