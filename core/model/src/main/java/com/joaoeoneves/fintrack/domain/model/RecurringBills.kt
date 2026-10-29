package com.joaoeoneves.fintrack.domain.model

private const val MONTHS_PER_YEAR = 12L

/**
 * The recurring bills: expense items that are not the monthly contribution of a savings plan (those live in the
 * plan). Every "Recurring" figure in the app is computed over these, so Home, the plan and the Recurring screen agree.
 */
fun billsOf(
    items: List<RecurringItem>,
    goals: List<SavingsGoal>,
): List<RecurringItem> {
    val planOwned = goals.mapNotNullTo(mutableSetOf()) { it.recurringId }
    return items.filter { it.type == RecurringType.EXPENSE && it.id !in planOwned }
}

/** The monthly equivalent of a yearly amount, rounded to the nearest cent: the one rounding used for display. */
fun monthlyEquivalentCents(annualCents: Long): Long = (annualCents + MONTHS_PER_YEAR / 2) / MONTHS_PER_YEAR
