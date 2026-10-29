package com.joaoeoneves.fintrack.domain.model

private const val MONTHS_PER_YEAR = 12L

/**
 * The recurring bills: every expense item, including the monthly contribution of a savings plan (those show as
 * investments). Every "Recurring" figure in the app is computed over these, so Home and the Recurring screen agree.
 */
fun billsOf(items: List<RecurringItem>): List<RecurringItem> = items.filter { it.type == RecurringType.EXPENSE }

/** The monthly equivalent of a yearly amount, rounded to the nearest cent: the one rounding used for display. */
fun monthlyEquivalentCents(annualCents: Long): Long = (annualCents + MONTHS_PER_YEAR / 2) / MONTHS_PER_YEAR
