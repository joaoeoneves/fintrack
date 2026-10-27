package com.joaoeoneves.fintrack.domain.model

/**
 * A monthly spending limit, in cents, for the category with [categoryId]. Limits are not stored per
 * month: the same limit is evaluated against whichever calendar month the dashboard has selected
 * (the anchor month of the selected [Period], or the current month for rolling/custom periods).
 * [skipMonths] (month numbers 1..12) are the calendar months in which the limit does not apply.
 */
data class Budget(
    val categoryId: CategoryId,
    val limitCents: Long,
    val skipMonths: Set<Int> = emptySet(),
)
