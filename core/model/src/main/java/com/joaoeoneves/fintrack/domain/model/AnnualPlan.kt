package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

private const val MONTHS_PER_YEAR = 12

/** Everything [annualPlanOf] reads, bundled for detekt's parameter limit; [yearExpenses] are the year's. */
data class AnnualPlanInputs(
    val lines: List<PlannedIncome>,
    val budgets: List<Budget>,
    val recurring: List<RecurringItem>,
    val categories: List<Category>,
    val envelopes: List<Envelope>,
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

/**
 * One envelope's year: [plannedCents] over the 12 months (a fixed amount times 12, else what its categories plan),
 * [actualCents] spent so far; `envelope == null` is the implicit "no envelope" row.
 */
data class AnnualEnvelope(
    val envelope: Envelope?,
    val plannedCents: Long,
    val actualCents: Long,
) {
    val isOver: Boolean get() = plannedCents > 0 && actualCents > plannedCents
}

/** A funded envelope over the year: its funding line's [incomeCents] vs what the envelope plans. */
data class AnnualFunding(
    val envelope: Envelope,
    val line: PlannedIncome,
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
 * The plan of one [year]. [plannedCents] is the sum over [envelopes] (and so over every planned category) and
 * [leftoverCents] what the year's total income leaves after it.
 */
data class AnnualPlan(
    val year: Int,
    val income: AnnualIncome,
    val envelopes: List<AnnualEnvelope>,
    val subscriptions: List<AnnualSubscription>,
    val funding: List<AnnualFunding>,
    val goals: List<AnnualGoal> = emptyList(),
) {
    val plannedCents: Long get() = envelopes.sumOf { it.plannedCents }
    val leftoverCents: Long get() = income.totalCents - plannedCents
}

/**
 * Evaluates the plan for the whole [year]. Income: monthly lines summed over the 12 months with each month's
 * effective working days (per-working-day lines) and skip months; yearly/one-off lines that land in the year are
 * listed as extras. Per envelope: planned is the sum of [plannedForCategory] over the 12 months (budget skip months
 * and yearly recurring items respected), actual the year's net spend per category (refunds netted, floored at
 * zero) filed under the category's envelope; categories in none fall into the implicit row. Funding compares a
 * funded envelope's line year income with what the envelope plans.
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
        envelopes = annualEnvelopesOf(plannedByCategory, inputs),
        subscriptions = subscriptionsOf(year, inputs.recurring),
        funding = annualFundingOf(inputs, workingDays, plannedByCategory),
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

private fun annualEnvelopesOf(
    plannedByCategory: Map<CategoryId, Long>,
    inputs: AnnualPlanInputs,
): List<AnnualEnvelope> {
    val envelopeIds = inputs.envelopes.mapTo(mutableSetOf()) { it.id }
    val actualByCategory =
        inputs.yearExpenses
            .groupBy { it.category.id }
            .mapValues { (_, expenses) -> expenses.netCents().coerceAtLeast(0) }
            .filterValues { it > 0 }

    fun envelopeIdOf(id: CategoryId) =
        inputs.categories
            .resolve(id)
            .groupId
            ?.takeIf { it in envelopeIds }
    val plannedByEnvelope = plannedByCategory.entries.groupBy({ envelopeIdOf(it.key) }, { it.value })
    val actualByEnvelope = actualByCategory.entries.groupBy({ envelopeIdOf(it.key) }, { it.value })

    fun annualEnvelope(envelope: Envelope?) =
        AnnualEnvelope(
            envelope = envelope,
            plannedCents =
                envelope?.amountCents?.let { it * MONTHS_PER_YEAR } ?: plannedByEnvelope[envelope?.id].orEmpty().sum(),
            actualCents = actualByEnvelope[envelope?.id].orEmpty().sum(),
        )
    val defined = inputs.envelopes.sortedWith(compareBy({ it.order }, { it.id })).map(::annualEnvelope)
    val other = annualEnvelope(null).takeIf { it.plannedCents > 0 || it.actualCents > 0 }
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

private fun annualFundingOf(
    inputs: AnnualPlanInputs,
    workingDays: Map<YearMonth, Int>,
    plannedByCategory: Map<CategoryId, Long>,
): List<AnnualFunding> {
    val lineById = inputs.lines.filterNot { it.isExtra }.associateBy { it.id }
    val usedLines = mutableSetOf<String>()
    return inputs.envelopes
        .sortedWith(compareBy({ it.order }, { it.id }))
        .mapNotNull { envelope ->
            val line = envelope.fundedByLineId?.let(lineById::get)?.takeIf { usedLines.add(it.id) }
            line?.let { envelope to it }
        }.map { (envelope, line) ->
            AnnualFunding(
                envelope = envelope,
                line = line,
                incomeCents = workingDays.entries.sumOf { (m, d) -> line.amountIn(m, d) },
                plannedCents =
                    envelope.amountCents?.let { it * MONTHS_PER_YEAR }
                        ?: plannedByCategory.entries
                            .filter { (id, _) -> inputs.categories.resolve(id).groupId == envelope.id }
                            .sumOf { it.value },
            )
        }.filter { it.incomeCents > 0 || it.plannedCents > 0 }
}
