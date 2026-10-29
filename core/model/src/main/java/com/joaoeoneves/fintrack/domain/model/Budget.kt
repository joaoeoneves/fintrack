package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * A monthly spending limit, in cents, for the category with [categoryId]. Limits are not stored per
 * month: the same limit is evaluated against whichever calendar month the dashboard has selected
 * (the anchor month of the selected [Period], or the current month for rolling/custom periods).
 * [skipMonths] (month numbers 1..12) are the calendar months in which the limit does not apply.
 *
 * [startMonth] is the first plan month the limit applies in (null = always); [versions] hold the limit history;
 * [limitCents] is the latest.
 */
data class Budget(
    val categoryId: CategoryId,
    val limitCents: Long,
    val skipMonths: Set<Int> = emptySet(),
    val startMonth: YearMonth? = null,
    val versions: List<AmountVersion> = emptyList(),
)

/** This limit as it is in plan [month] (amount per [versions]), or null before its [Budget.startMonth]. */
fun Budget.asOf(month: YearMonth): Budget? =
    if (startMonth != null && month < startMonth) {
        null
    } else {
        copy(limitCents = versions.amountAt(month, limitCents) ?: limitCents)
    }

/** The limits that apply in plan [month], each resolved with [asOf]. */
fun List<Budget>.asOf(month: YearMonth): List<Budget> = mapNotNull { it.asOf(month) }

/** This limit changed to [newLimit] from plan [month] on (see [editedVersions]). */
fun Budget.editedFrom(
    month: YearMonth,
    newLimit: Long,
): Budget {
    val edit = editedVersions(startMonth, versions, limitCents, month, newLimit)
    return if (edit.changedFrom(startMonth, versions)) {
        copy(limitCents = edit.latest ?: limitCents, startMonth = edit.startMonth, versions = edit.versions)
    } else {
        this
    }
}

/** This (new) limit starting in plan [month], with no history. */
fun Budget.startingAt(month: YearMonth): Budget = copy(startMonth = month, versions = emptyList())
