package com.joaoeoneves.fintrack.di

import com.joaoeoneves.fintrack.data.session.SessionAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.session.SessionCategoryGroupRepository
import com.joaoeoneves.fintrack.data.session.SessionGoalRepository
import com.joaoeoneves.fintrack.data.session.SessionMonthSettingsRepository
import com.joaoeoneves.fintrack.data.session.SessionPlannedIncomeRepository
import com.joaoeoneves.fintrack.data.session.SessionRecurringRepository
import com.joaoeoneves.fintrack.data.session.SessionSalaryProfileRepository
import com.joaoeoneves.fintrack.domain.repository.AutomationSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.CategoryGroupRepository
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bindings for the planning data: income lines, month settings, groups, the salary profile, savings goals,
 * recurring items and the automation settings.
 */
@Module
@InstallIn(SingletonComponent::class)
interface PlanRepositoryModule {
    @Binds
    fun bindPlannedIncomeRepository(impl: SessionPlannedIncomeRepository): PlannedIncomeRepository

    @Binds
    fun bindMonthSettingsRepository(impl: SessionMonthSettingsRepository): MonthSettingsRepository

    @Binds
    fun bindRecurringRepository(impl: SessionRecurringRepository): RecurringRepository

    @Binds
    fun bindCategoryGroupRepository(impl: SessionCategoryGroupRepository): CategoryGroupRepository

    @Binds
    fun bindSalaryProfileRepository(impl: SessionSalaryProfileRepository): SalaryProfileRepository

    @Binds
    fun bindGoalRepository(impl: SessionGoalRepository): GoalRepository

    @Binds
    fun bindAutomationSettingsRepository(impl: SessionAutomationSettingsRepository): AutomationSettingsRepository
}
