package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate

private const val MONTHS_PER_YEAR = 12

/** The cost of the active, not-ended expense items, split by frequency (see [recurringCostOf]). */
data class RecurringCost(
    /** Sum of the monthly items' amounts: what they cost every month. */
    val monthlyItemsCents: Long,
    /** Sum of the yearly items' amounts: what they cost once a year. */
    val yearlyItemsCents: Long,
) {
    /** Everything over a year: monthly items 12 times plus the yearly ones. */
    val annualCents: Long get() = monthlyItemsCents * MONTHS_PER_YEAR + yearlyItemsCents

    /** [annualCents] spread over 12 months. */
    val monthlyEquivalentCents: Long get() = monthlyEquivalentCents(annualCents)

    /** The yearly items alone spread over 12 months. */
    val yearlyPerMonthCents: Long get() = monthlyEquivalentCents(yearlyItemsCents)
}

/** The [RecurringCost] of the active, not-ended expense items (the same set as [annualizedCents] for expenses). */
fun recurringCostOf(
    items: List<RecurringItem>,
    today: LocalDate,
): RecurringCost {
    val live = items.filter { it.type == RecurringType.EXPENSE && it.active && !it.isEnded(today) }
    return RecurringCost(
        monthlyItemsCents = live.filter { it.frequency == RecurringFrequency.MONTHLY }.sumOf { it.amountCents },
        yearlyItemsCents = live.filter { it.frequency != RecurringFrequency.MONTHLY }.sumOf { it.amountCents },
    )
}
