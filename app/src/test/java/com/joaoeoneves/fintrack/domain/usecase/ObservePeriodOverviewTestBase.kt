package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.CombinedTransactionRepository
import com.joaoeoneves.fintrack.data.FakeCategoryGroupRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.testutil.CountingBudgetRepository
import com.joaoeoneves.fintrack.testutil.CountingExpenseRepository
import com.joaoeoneves.fintrack.testutil.CountingIncomeRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/** Shared wiring for the [ObservePeriodOverview] tests: counting fakes and a fixed clock (2026-10-20 UTC). */
abstract class ObservePeriodOverviewTestBase {
    protected val zone: ZoneId = ZoneOffset.UTC
    protected val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    protected val october: YearMonth = YearMonth.of(2026, 10)
    protected val september: YearMonth = YearMonth.of(2026, 9)

    protected fun noonOn(
        month: YearMonth,
        day: Int,
    ): Instant = month.atDay(day).atTime(12, 0).toInstant(ZoneOffset.UTC)

    protected val expenses = CountingExpenseRepository()
    protected val income = CountingIncomeRepository()
    protected val budgets = CountingBudgetRepository()
    protected val plannedIncome = FakePlannedIncomeRepository()
    protected val categories = FakeCategoryRepository()
    protected val groups = FakeCategoryGroupRepository()
    protected val recurring = FakeRecurringRepository()

    protected val useCase =
        ObservePeriodOverview(
            transactionRepository = CombinedTransactionRepository(expenses, income),
            sources =
                PlanDataSources(
                    budgets = budgets,
                    plannedIncome = plannedIncome,
                    monthSettings = FakeMonthSettingsRepository(),
                    categories = categories,
                    groups = groups,
                    recurring = recurring,
                ),
        )

    protected suspend fun overview(
        period: Period,
        recentLimit: Int = 5,
    ) = useCase(period, now, zone, recentLimit).first()
}
