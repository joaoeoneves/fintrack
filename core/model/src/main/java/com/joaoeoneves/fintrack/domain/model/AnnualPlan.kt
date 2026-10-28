package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

private const val MONTHS_PER_YEAR = 12

/** Everything [annualPlanOf] reads, bundled for detekt's parameter limit; [yearExpenses] are the year's. */
data class AnnualPlanInputs(
    val lines: List<PlannedIncome>,
    val budgets: List<Budget>,
    val recurring: List<RecurringItem>,
    val categories: List<Category>,
    val groups: List<CategoryGroup>,
    val monthSettings: List<MonthSettings>,
    val yearExpenses: List<Expense> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(),
)

/** A yearly or one-off income line paying [cents] in [month] (1..12) of the plan's year. */
data class AnnualExtra(
    val line: PlannedIncome,
    val month: Int,
    val cents: Long,
)

/**
 * The year's expected income: [regularCents] from the monthly lines, plus the [extras] that land in the year;
 * [rows] is the same income grouped one row per component.
 */
data class AnnualIncome(
    val regularCents: Long,
    val extras: List<AnnualExtra>,
    val rows: List<IncomeRow> = emptyList(),
) {
    val extrasCents: Long get() = extras.sumOf { it.cents }
    val totalCents: Long get() = regularCents + extrasCents
}

/** One group's year: [plannedCents] over the 12 months, [actualCents] spent so far; `group == null` is "Other". */
data class AnnualGroup(
    val group: CategoryGroup?,
    val plannedCents: Long,
    val actualCents: Long,
) {
    val isOver: Boolean get() = plannedCents > 0 && actualCents > plannedCents
}

/** A pot over the year: the earmarked lines' income vs what its categories plan. */
data class AnnualPot(
    val target: Earmark,
    val group: CategoryGroup?,
    val category: Category?,
    val incomeCents: Long,
    val plannedCents: Long,
) {
    val leftoverCents: Long get() = incomeCents - plannedCents
}

/** A yearly recurring expense due in [month] (1..12) of the plan's year. */
data class AnnualSubscription(
    val item: RecurringItem,
    val month: Int,
)

/** A goal's year: the [plannedCents] its linked recurring items contribute in the plan's year against its target. */
data class AnnualGoal(
    val goal: SavingsGoal,
    val plannedCents: Long,
) {
    /** Whether the planned contributions reach the goal's target (an ANNUAL one; a TOTAL one needs several years). */
    val coversTarget: Boolean get() = plannedCents >= goal.targetCents
}

/**
 * The plan of one [year]. [plannedCents] is the sum over [groups] (and so over every planned category) and
 * [leftoverCents] what the year's total income leaves after it.
 */
data class AnnualPlan(
    val year: Int,
    val income: AnnualIncome,
    val groups: List<AnnualGroup>,
    val subscriptions: List<AnnualSubscription>,
    val pots: List<AnnualPot>,
    val goals: List<AnnualGoal> = emptyList(),
) {
    val plannedCents: Long get() = groups.sumOf { it.plannedCents }
    val leftoverCents: Long get() = income.totalCents - plannedCents
}

/**
 * Evaluates the plan for the whole [year]. Income: monthly lines summed over the 12 months with each month's
 * effective working days (per-working-day lines) and skip months; yearly/one-off lines that land in the year are
 * listed as extras. Per group: planned is the sum of [plannedForCategory] over the 12 months (budget skip months
 * and yearly recurring items respected), actual the year's net spend per category (refunds netted, floored at
 * zero) filed under the category's group; ungrouped categories fall into "Other". Pots are the earmarked monthly
 * lines' year income against the planned amounts of their categories.
 */
fun annualPlanOf(
    year: Int,
    inputs: AnnualPlanInputs,
): AnnualPlan {
    val months = (1..MONTHS_PER_YEAR).map { YearMonth.of(year, it) }
    val workingDays = months.associateWith { effectiveWorkingDaysOf(it, inputs.monthSettings) }
    val plannedByCategory = plannedByCategory(months, inputs)
    return AnnualPlan(
        year = year,
        income = annualIncomeOf(inputs.lines, workingDays),
        groups = annualGroupsOf(plannedByCategory, inputs),
        subscriptions = subscriptionsOf(year, inputs.recurring),
        pots = annualPotsOf(inputs, workingDays, plannedByCategory),
        goals = annualGoalsOf(months, inputs),
    )
}

/**
 * The active goals with what their linked EXPENSE recurring items plan over [months] (occurrences within the
 * year, start and end dates respected). Informational: those expenses already sit in their category's group.
 */
private fun annualGoalsOf(
    months: List<YearMonth>,
    inputs: AnnualPlanInputs,
): List<AnnualGoal> =
    inputs.goals
        .filterNot { it.archived }
        .sortedWith(compareBy({ it.order }, { it.id }))
        .map { goal ->
            val linked =
                inputs.recurring.filter {
                    it.type == RecurringType.EXPENSE && it.active && it.id in goal.linkedRecurringIds
                }
            AnnualGoal(goal, linked.sumOf { item -> item.amountCents * months.count { item.occursIn(it) } })
        }

private fun effectiveWorkingDaysOf(
    month: YearMonth,
    settings: List<MonthSettings>,
): Int {
    val monthSettings = settings.firstOrNull { it.month == month }
    return effectiveWorkingDays(month, monthSettings?.workingDaysOverride, monthSettings?.daysOff ?: 0)
}

private fun annualIncomeOf(
    lines: List<PlannedIncome>,
    workingDays: Map<YearMonth, Int>,
): AnnualIncome {
    val (extraLines, regularLines) = lines.partition { it.isExtra }
    val regular = regularLines.sumOf { line -> workingDays.entries.sumOf { (m, days) -> line.amountIn(m, days) } }
    val extras =
        extraLines
            .flatMap { line ->
                workingDays.mapNotNull { (month, days) ->
                    line.amountIn(month, days).takeIf { it > 0 }?.let { AnnualExtra(line, month.monthValue, it) }
                }
            }.sortedWith(compareBy({ it.month }, { it.line.order }, { it.line.id }))
    return AnnualIncome(regular, extras, annualIncomeRowsOf(lines, workingDays))
}

/** Planned cents per category id over the year, only for categories with something planned. */
private fun plannedByCategory(
    months: List<YearMonth>,
    inputs: AnnualPlanInputs,
): Map<CategoryId, Long> {
    val budgetById = inputs.budgets.associateBy { it.categoryId }
    val recurringById =
        inputs.recurring
            .mapNotNull { item -> item.categoryId?.let { it to item } }
            .groupBy({ it.first }, { it.second })
    return (budgetById.keys + recurringById.keys)
        .associateWith { id ->
            months.sumOf { plannedForCategory(it, budgetById[id], recurringById[id].orEmpty()).cents ?: 0L }
        }.filterValues { it > 0 }
}

private fun annualGroupsOf(
    plannedByCategory: Map<CategoryId, Long>,
    inputs: AnnualPlanInputs,
): List<AnnualGroup> {
    val groupIds = inputs.groups.mapTo(mutableSetOf()) { it.id }
    val actualByCategory =
        inputs.yearExpenses
            .groupBy { it.category.id }
            .mapValues { (_, expenses) -> expenses.netCents().coerceAtLeast(0) }
            .filterValues { it > 0 }

    fun groupIdOf(id: CategoryId) =
        inputs.categories
            .resolve(id)
            .groupId
            ?.takeIf { it in groupIds }
    val plannedByGroup = plannedByCategory.entries.groupBy({ groupIdOf(it.key) }, { it.value })
    val actualByGroup = actualByCategory.entries.groupBy({ groupIdOf(it.key) }, { it.value })

    fun annualGroup(group: CategoryGroup?) =
        AnnualGroup(
            group = group,
            plannedCents = plannedByGroup[group?.id].orEmpty().sum(),
            actualCents = actualByGroup[group?.id].orEmpty().sum(),
        )
    val defined = inputs.groups.sortedWith(compareBy({ it.order }, { it.id })).map(::annualGroup)
    val other = annualGroup(null).takeIf { it.plannedCents > 0 || it.actualCents > 0 }
    return defined + listOfNotNull(other)
}

/** Active yearly recurring EXPENSE items with an occurrence in [year], by month then name. */
private fun subscriptionsOf(
    year: Int,
    recurring: List<RecurringItem>,
): List<AnnualSubscription> =
    recurring
        .filter { it.type == RecurringType.EXPENSE && it.frequency == RecurringFrequency.YEARLY && it.active }
        .mapNotNull { item ->
            val month = item.monthOfYear ?: return@mapNotNull null
            AnnualSubscription(item, month).takeIf { item.occursIn(YearMonth.of(year, month)) }
        }.sortedWith(compareBy({ it.month }, { it.item.name }))

private fun annualPotsOf(
    inputs: AnnualPlanInputs,
    workingDays: Map<YearMonth, Int>,
    plannedByCategory: Map<CategoryId, Long>,
): List<AnnualPot> {
    val groupById = inputs.groups.associateBy { it.id }
    val categoryIds =
        inputs.categories.mapTo(mutableSetOf()) { it.id.value } + BuiltInCategories.all.map { it.id.value }
    val earmarked =
        inputs.lines
            .filterNot { it.isExtra }
            .filter { line ->
                when (val e = line.earmark) {
                    is Earmark.Group -> e.id in groupById
                    is Earmark.Category -> e.id in categoryIds
                    null -> false
                }
            }
    val byTarget = earmarked.groupBy { checkNotNull(it.earmark) }
    return byTarget.entries
        .sortedBy { (_, lines) -> lines.minOf { it.order } }
        .map { (target, lines) ->
            val planned =
                plannedByCategory.entries
                    .filter { (id, _) -> potTargetOf(inputs.categories.resolve(id), byTarget.keys) == target }
                    .sumOf { it.value }
            AnnualPot(
                target = target,
                group = (target as? Earmark.Group)?.let { groupById[it.id] },
                category = (target as? Earmark.Category)?.let { inputs.categories.resolve(CategoryId(it.id)) },
                incomeCents = lines.sumOf { line -> workingDays.entries.sumOf { (m, d) -> line.amountIn(m, d) } },
                plannedCents = planned,
            )
        }.filter { it.incomeCents > 0 || it.plannedCents > 0 }
}

/** A category's pot: its own category earmark, else its group's earmark, else none (the base). */
private fun potTargetOf(
    category: Category,
    targets: Set<Earmark>,
): Earmark? =
    Earmark.Category(category.id.value).takeIf { it in targets }
        ?: category.groupId?.let { Earmark.Group(it) }?.takeIf { it in targets }
