@file:Suppress("MatchingDeclarationName")

package com.joaoeoneves.fintrack.domain.model

import java.time.Instant

// TEMPORARY stubs of the 5.7 contract (Track A owns the real ones); dropped at merge.

data class RefundStatus(
    val refundedCents: Long,
    val originalCents: Long,
) {
    val percent: Int
        get() = if (originalCents <= 0L) 0 else (refundedCents * 100 / originalCents).toInt().coerceIn(0, 100)
    val netCents: Long get() = maxOf(0L, originalCents - refundedCents)
}

fun refundStatusOf(
    original: Expense,
    refunds: List<Expense>,
): RefundStatus? {
    val own = refunds.filter { it.isRefund && it.refundOf == original.id }
    return if (original.isRefund || own.isEmpty()) null else RefundStatus(own.sumOf { it.amountCents }, original.amountCents)
}

fun refundIndexOf(refunds: List<Expense>): Map<String, Long> =
    refunds
        .filter { it.isRefund && it.refundOf != null }
        .groupBy { it.refundOf.orEmpty() }
        .mapValues { (_, list) -> list.sumOf { it.amountCents } }

fun refundCandidates(
    expenses: List<Expense>,
    excludeId: String?,
): List<Expense> = expenses.filter { !it.isRefund && it.id != excludeId }.sortedByDescending { it.date }

const val MAX_SPLIT_PARTS = 10

data class SplitPart(
    val category: Category,
    val amountCents: Long,
)

fun splitExpenses(
    base: Expense,
    parts: List<SplitPart>,
    splitId: String,
): List<Expense> {
    require(!base.isRefund && parts.size in 2..MAX_SPLIT_PARTS && parts.sumOf { it.amountCents } == base.amountCents)
    return parts.map { base.copy(category = it.category, amountCents = it.amountCents, splitId = splitId) }
}

fun Expense.duplicated(today: Instant): Expense =
    copy(id = "", date = today, refundOf = null, splitId = null, needsReview = false)

fun categoriesAboveAverage(
    series: Map<CategoryId, List<Long>>,
    index: Int,
    window: Int = COMPARISON_WINDOW_MONTHS,
    minMonths: Int = 3,
): Set<CategoryId> =
    series
        .filter { (_, values) ->
            val c = seriesComparisonOf(values, index, window)
            c.averageMonths >= minMonths && (c.averageCents ?: Long.MAX_VALUE) < c.currentCents
        }.keys
