package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testIncome
import java.time.Instant
import java.time.YearMonth

/** Fixtures for the [monthlyPlanOf] / [dailyAllowanceOf] tests. */
abstract class MonthlyPlanTestBase {
    protected val date: Instant = Instant.parse("2026-10-10T12:00:00Z")

    protected fun bucket(
        category: Category,
        planned: Long?,
        spent: Long = 0L,
    ) = CategoryBudget(category, spent, planned)

    protected fun line(
        amount: Long,
        id: String = "l$amount",
    ) = PlannedIncome(id = id, label = id, amountCents = amount, order = 0)

    protected fun received(vararg amounts: Long) = amounts.mapIndexed { i, a -> testIncome("i$i", a, date) }

    protected fun plan(
        buckets: List<CategoryBudget> = emptyList(),
        lines: List<PlannedIncome> = listOf(line(200_000L)),
        income: List<Long> = emptyList(),
        month: YearMonth = YearMonth.of(2026, 10),
        workingDays: Int = 22,
    ) = monthlyPlanOf(buckets, lines, received(*income.toLongArray()), month, workingDays)
}
