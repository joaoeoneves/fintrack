package ptech.joaoe.agenticusage.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ptech.joaoe.agenticusage.data.FirestoreExpenseRepository
import ptech.joaoe.agenticusage.domain.repository.ExpenseRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindExpenseRepository(impl: FirestoreExpenseRepository): ExpenseRepository
}
