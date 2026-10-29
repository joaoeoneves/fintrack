@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename")

package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

/** An expense that showed up once in each of the last three months and could become a recurring item. */
data class RecurringCandidate(
    /** The normalized name; also the key under which a dismissal is stored. */
    val key: String,
    val name: String,
    val amountCents: Long,
    val categoryId: CategoryId,
    val dayOfMonth: Int,
    val lastMonth: YearMonth,
)

/** The recurring-item candidates among [expenses], latest first, skipping existing items and [dismissed] keys. */
fun recurringCandidates(
    expenses: List<Expense>,
    recurring: List<RecurringItem>,
    dismissed: Set<String>,
    today: LocalDate,
    zone: ZoneId,
): List<RecurringCandidate> {
    val current = YearMonth.from(today)
    val existing = recurring.filter { it.type == RecurringType.EXPENSE }.map { normalizedMerchantName(it.name) }.toSet()
    val latestOrder = compareBy<Expense> { it.date }.thenBy { it.id }
    return expenses
        .filter { !it.isRefund && it.name.isNotBlank() && !it.id.startsWith("rec_") }
        .groupBy { normalizedMerchantName(it.name) }
        .filterKeys { it !in existing && it !in dismissed }
        .mapNotNull { (key, group) ->
            val latest = group.maxWith(latestOrder)
            val month = YearMonth.from(latest.date.atZone(zone))
            val byMonth = group.groupBy { YearMonth.from(it.date.atZone(zone)) }
            val window = listOf(month.minusMonths(2), month.minusMonths(1), month)
            val ok =
                month >= current.minusMonths(1) &&
                    month <= current &&
                    window.all { byMonth[it]?.size == 1 } &&
                    window.all { withinTolerance(byMonth.getValue(it).single().amountCents, latest.amountCents) }
            if (!ok) {
                null
            } else {
                RecurringCandidate(
                    key,
                    latest.name,
                    latest.amountCents,
                    latest.category.id,
                    latest.date.atZone(zone).dayOfMonth,
                    month,
                ) to latest.date
            }
        }.sortedByDescending { it.second }
        .map { it.first }
}

private const val TOLERANCE_DIVISOR = 10

/** Whether [amount] is within 10% (inclusive) of [reference]. */
private fun withinTolerance(
    amount: Long,
    reference: Long,
): Boolean = abs(amount - reference) * TOLERANCE_DIVISOR <= reference
