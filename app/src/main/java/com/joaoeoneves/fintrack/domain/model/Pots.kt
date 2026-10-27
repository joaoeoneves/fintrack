package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * One envelope of the month's money. [target] is `null` for the shared base. [expectedIncomeCents] is the
 * money available to it, [plannedCents] / [spentCents] what its categories have planned / spent,
 * [committedCents] the per-category larger of the two (plus, for the base, the overflow of overspent pots)
 * and [freeCents] what is left; negative when overrun. [group] / [category] are the resolved target.
 */
data class Pot(
    val target: Earmark?,
    val expectedIncomeCents: Long,
    val plannedCents: Long,
    val spentCents: Long,
    val committedCents: Long,
    val group: CategoryGroup? = null,
    val category: Category? = null,
) {
    val freeCents: Long get() = expectedIncomeCents - committedCents
}

/** The earmarked [pots] (empty without earmarks) and the shared [base] (everything not earmarked). */
data class PotsSummary(
    val pots: List<Pot>,
    val base: Pot,
)

/** The month a plan is evaluated for: [month] and its [workingDays] (per-working-day lines multiply by it). */
data class PlanMonth(
    val month: YearMonth,
    val workingDays: Int,
)

/**
 * Splits the month's money into pots. Regular (monthly) income lines with an earmark whose target exists
 * ([groups], or a category among the [buckets]) are the pot's expected income; lines with the same target sum.
 * A bucket belongs to its category's pot, else its group's pot (a category earmark beats a group earmark), else
 * the base. The base income is the larger of the expected non-earmarked regular income and the month's actual
 * income minus the earmarked expected. An overspent pot's deficit spills into the base; a surplus stays in
 * its pot. Without earmarks the base equals [MonthlyPlanSummary.safeToSpendCents]. A dangling earmark counts
 * as no earmark.
 */
fun potsOf(
    buckets: List<CategoryBudget>,
    groups: List<CategoryGroup>,
    plannedIncome: List<PlannedIncome>,
    monthIncome: List<Income>,
    planMonth: PlanMonth,
): PotsSummary {
    val (month, workingDays) = planMonth
    val groupById = groups.associateBy { it.id }
    val categoryById = buckets.associate { it.category.id.value to it.category }
    val regular = plannedIncome.filterNot { it.isExtra }
    val earmarked =
        regular
            .filter { line -> line.earmark?.let { it.exists(groupById, categoryById) } == true }
            .filter { it.amountIn(month, workingDays) > 0 }
    val expectedByTarget = earmarked.groupBy { it.earmark }.filterKeys { it != null }
    val potOfBucket = buckets.associateWith { bucket -> bucket.targetIn(expectedByTarget.keys) }
    val pots =
        expectedByTarget.entries
            .sortedBy { (_, lines) -> lines.minOf { it.order } }
            .map { (target, lines) ->
                val own = potOfBucket.filterValues { it == target }.keys
                potOf(target, lines.sumOf { it.amountIn(month, workingDays) }, own).copy(
                    group = (target as? Earmark.Group)?.let { groupById[it.id] },
                    category = (target as? Earmark.Category)?.let { categoryById[it.id] },
                )
            }
    val earmarkedExpected = pots.sumOf { it.expectedIncomeCents }
    val baseExpected = regular.sumOf { it.amountIn(month, workingDays) } - earmarkedExpected
    val actual = monthIncome.sumOf { it.amountCents }
    val basis = maxOf(baseExpected, actual - earmarkedExpected)
    val overflow = pots.sumOf { maxOf(-it.freeCents, 0L) }
    val base = potOf(null, basis, potOfBucket.filterValues { it == null }.keys)
    return PotsSummary(pots, base.copy(committedCents = base.committedCents + overflow))
}

private fun Earmark.exists(
    groups: Map<String, CategoryGroup>,
    categories: Map<String, Category>,
): Boolean =
    when (this) {
        is Earmark.Group -> id in groups
        is Earmark.Category -> id in categories
    }

private fun CategoryBudget.targetIn(targets: Set<Earmark?>): Earmark? {
    val byCategory = Earmark.Category(category.id.value).takeIf { it in targets }
    return byCategory ?: category.groupId?.let { Earmark.Group(it) }?.takeIf { it in targets }
}

private fun potOf(
    target: Earmark?,
    expected: Long,
    buckets: Set<CategoryBudget>,
): Pot =
    Pot(
        target = target,
        expectedIncomeCents = expected,
        plannedCents = buckets.sumOf { it.planned ?: 0L },
        spentCents = buckets.sumOf { it.spentCents },
        committedCents = buckets.sumOf { maxOf(it.planned ?: 0L, it.spentCents) },
    )
