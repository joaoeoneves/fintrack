package com.joaoeoneves.fintrack.di

import com.joaoeoneves.fintrack.data.session.SessionPlannedIncomeRepository
import com.joaoeoneves.fintrack.data.session.SessionRecurringRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Bindings for the planning data: expected income and recurring bills/income. */
@Module
@InstallIn(SingletonComponent::class)
interface PlanRepositoryModule {
    @Binds
    fun bindPlannedIncomeRepository(impl: SessionPlannedIncomeRepository): PlannedIncomeRepository

    @Binds
    fun bindRecurringRepository(impl: SessionRecurringRepository): RecurringRepository
}
