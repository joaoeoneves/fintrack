package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import java.time.Instant
import java.time.YearMonth

internal const val PLAN_YEAR = 2026

internal fun planGroup(
    id: String,
    order: Int = 0,
) = CategoryGroup(id = id, name = id, order = order)

internal fun planCategory(
    id: String,
    groupId: String? = null,
) = Category(
    id = CategoryId(id),
    customName = id,
    iconKey = "icon",
    colorKey = "slate",
    kind = CategoryKind.SPENDING,
    order = 0,
    archived = false,
    builtIn = false,
    groupId = groupId,
)

internal fun planMonthly(
    id: String,
    category: Category,
    cents: Long,
    year: Int = PLAN_YEAR,
    startMonth: Int = 1,
) = recurringItem(
    id = id,
    name = id,
    amountCents = cents,
    categoryId = category.id,
    frequency = RecurringFrequency.MONTHLY,
    startDate = java.time.LocalDate.of(year, startMonth, 1),
)

internal fun planYearly(
    id: String,
    category: Category,
    cents: Long,
    month: Int,
    year: Int = PLAN_YEAR,
) = recurringItem(
    id = id,
    name = id,
    amountCents = cents,
    categoryId = category.id,
    frequency = RecurringFrequency.YEARLY,
    monthOfYear = month,
    startDate = java.time.LocalDate.of(year, 1, 1),
)

internal fun planExpense(
    id: String,
    category: Category,
    cents: Long,
    month: Int = 3,
    refund: Boolean = false,
) = testExpense(
    id = id,
    amountCents = cents,
    date = Instant.parse("$PLAN_YEAR-%02d-10T12:00:00Z".format(month)),
    category = category,
    isRefund = refund,
)

@Suppress("LongParameterList") // Mirrors AnnualPlanInputs so tests override only what they need.
internal fun planInputs(
    lines: List<PlannedIncome> = emptyList(),
    budgets: List<Budget> = emptyList(),
    recurring: List<RecurringItem> = emptyList(),
    categories: List<Category> = emptyList(),
    groups: List<CategoryGroup> = emptyList(),
    settings: List<MonthSettings> = emptyList(),
    expenses: List<Expense> = emptyList(),
) = AnnualPlanInputs(lines, budgets, recurring, categories, groups, settings, expenses)

internal fun planFixed(
    id: String,
    cents: Long,
    order: Int = 0,
    earmark: Earmark? = null,
    skip: Set<Int> = emptySet(),
) = PlannedIncome(id, id, cents, order, earmark = earmark, skipMonths = skip)

internal fun planPerDay(
    id: String,
    rate: Long,
    order: Int = 0,
    earmark: Earmark? = null,
    skip: Set<Int> = emptySet(),
) = PlannedIncome(
    id,
    id,
    rate,
    order,
    basis = IncomeBasis.PER_WORKING_DAY,
    earmark = earmark,
    skipMonths = skip,
)

internal fun planMonth(
    month: Int,
    year: Int = PLAN_YEAR,
) = YearMonth.of(year, month)
