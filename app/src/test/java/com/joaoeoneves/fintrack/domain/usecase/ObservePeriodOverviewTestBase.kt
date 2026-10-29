package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.CombinedTransactionRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.testutil.CountingBudgetRepository
import com.joaoeoneves.fintrack.testutil.CountingExpenseRepository
import com.joaoeoneves.fintrack.testutil.CountingIncomeRepository
import com.joaoeoneves.fintrack.testutil.FakePlanSettingsRepository
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

    /** The month before [range]'s start, up to its start: where a salary funding the range's first month is paid. */
    protected fun monthBefore(range: InstantRange): InstantRange =
        InstantRange(
            range.startInclusive
                .atZone(zone)
                .minusMonths(1)
                .toInstant(),
            range.startInclusive,
        )

    protected fun monthBefore(month: YearMonth): InstantRange = monthBefore(month.toInstantRange(zone))

    protected val expenses = CountingExpenseRepository()
    protected val income = CountingIncomeRepository()
    protected val budgets = CountingBudgetRepository()
    protected val plannedIncome = FakePlannedIncomeRepository()
    protected val categories = FakeCategoryRepository()
    protected val groups = FakeEnvelopeRepository()
    protected val recurring = FakeRecurringRepository()
    protected val monthSettings = FakeMonthSettingsRepository()
    protected val planSettings = FakePlanSettingsRepository()

    protected val useCase =
        ObservePeriodOverview(
            transactionRepository = CombinedTransactionRepository(expenses, income),
            sources =
                PlanDataSources(
                    budgets = budgets,
                    plannedIncome = plannedIncome,
                    monthSettings = monthSettings,
                    categories = categories,
                    envelopes = groups,
                    recurring = recurring,
                    planSettings = planSettings,
                ),
        )

    protected suspend fun overview(
        period: Period,
        recentLimit: Int = 5,
    ) = useCase(period, now, zone, recentLimit).first()
}
