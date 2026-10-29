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
@Suppress("LongParameterList") // Window, threshold and limit are tunable defaults.
fun quickAddSuggestions(
    expenses: List<Expense>,
    today: LocalDate,
    zone: ZoneId,
    windowDays: Int = 90,
    minCount: Int = 2,
    limit: Int = 4,
): List<QuickAddSuggestion> {
    val from = today.minusDays(windowDays - 1L)
    val latestOrder = compareBy<Expense> { it.date }.thenBy { it.id }
    return expenses
        .filter { !it.isRefund && it.name.isNotBlank() && !it.id.startsWith("rec_") }
        .filter { it.date.atZone(zone).toLocalDate() in from..today }
        .groupBy { normalizedMerchantName(it.name) }
        .values
        .filter { it.size >= minCount }
        .map { group -> group.size to group.maxWith(latestOrder) }
        .sortedWith(
            compareByDescending<Pair<Int, Expense>> { it.first }
                .thenByDescending { it.second.date }
                .thenBy { normalizedMerchantName(it.second.name) },
        ).take(limit)
        .map { (count, e) -> QuickAddSuggestion(e.name, e.amountCents, e.category.id, count) }
}
