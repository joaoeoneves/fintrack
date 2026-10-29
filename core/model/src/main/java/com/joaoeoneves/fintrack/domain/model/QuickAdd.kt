@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename")

package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate
import java.time.ZoneId

/** A frequently repeated expense offered as a one-tap prefill on the home screen. */
data class QuickAddSuggestion(
    val name: String,
    val amountCents: Long,
    val categoryId: CategoryId,
    val count: Int,
)

/**
 * The expenses repeated at least [minCount] times in the last [windowDays] days (today included), as prefill
 * suggestions taken from the latest record of each name; at most [limit], most frequent first.
 */
@Suppress("UnusedParameter", "LongParameterList") // Body lands with the quick-add implementation.
fun quickAddSuggestions(
    expenses: List<Expense>,
    today: LocalDate,
    zone: ZoneId,
    windowDays: Int = 90,
    minCount: Int = 2,
    limit: Int = 4,
): List<QuickAddSuggestion> = emptyList()
