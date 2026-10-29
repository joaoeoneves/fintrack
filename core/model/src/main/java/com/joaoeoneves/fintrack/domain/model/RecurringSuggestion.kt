@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename")

package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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
@Suppress("UnusedParameter") // Body lands with the recurring-suggestion implementation.
fun recurringCandidates(
    expenses: List<Expense>,
    recurring: List<RecurringItem>,
    dismissed: Set<String>,
    today: LocalDate,
    zone: ZoneId,
): List<RecurringCandidate> = emptyList()
