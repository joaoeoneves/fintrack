package com.joaoeoneves.fintrack.di

import com.joaoeoneves.fintrack.data.AppBuiltInCategoryNames
import com.joaoeoneves.fintrack.domain.repository.BuiltInCategoryNames
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
}
