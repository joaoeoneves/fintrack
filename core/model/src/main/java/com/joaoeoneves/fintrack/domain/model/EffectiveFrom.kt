package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/** Maximum number of [AmountVersion]s kept per item; older ones are dropped by [withVersion]. */
const val MAX_AMOUNT_VERSIONS = 12

/**
 * An amount that applies from plan month [from] (inclusive) until the next version. A null [amountCents] has
 * the item's own meaning of "no fixed amount" (for an [Envelope]: the sum of its categories).
 */
data class AmountVersion(
    val from: YearMonth,
    val amountCents: Long?,
)

/** The amount in force in [month]: the latest version with `from <= month`, or [fallback] if there is none. */
fun List<AmountVersion>.amountAt(
    month: YearMonth,
    fallback: Long?,
): Long? {
    val latest = filter { it.from <= month }.maxByOrNull { it.from }
    return if (latest == null) fallback else latest.amountCents
}

/**
 * [versions] with a new version ([from], [amount]) added: versions starting at or after [from] are dropped,
 * the result is sorted by month and, above [cap], the oldest versions are dropped.
 */
fun withVersion(
    versions: List<AmountVersion>,
    from: YearMonth,
    amount: Long?,
    cap: Int = MAX_AMOUNT_VERSIONS,
): List<AmountVersion> =
    (versions.filter { it.from < from } + AmountVersion(from, amount))
        .sortedBy { it.from }
        .takeLast(cap)

/** The month an item with no start month is treated as starting in when its first version is seeded. */
val EARLIEST_MONTH: YearMonth = YearMonth.of(EARLIEST_YEAR, 1)

private const val EARLIEST_YEAR = 2000

/** The result of [editedVersions]: the new start month, version history and latest amount. */
internal data class VersionEdit(
    val startMonth: YearMonth?,
    val versions: List<AmountVersion>,
    val latest: Long?,
) {
    fun changedFrom(
        start: YearMonth?,
        old: List<AmountVersion>,
    ): Boolean = startMonth != start || versions != old
}

/**
 * Applies "the amount is [newAmount] from [month]" to an item with [startMonth], [versions] and [latest] amount.
 * Unchanged (the same values come back) when [month] is not before the start and the amount in force in [month]
 * already is [newAmount]. A [month] before [startMonth] moves the start back to it. The amount that applied
 * before is seeded as a first version so earlier months keep it.
 */
internal fun editedVersions(
    startMonth: YearMonth?,
    versions: List<AmountVersion>,
    latest: Long?,
    month: YearMonth,
    newAmount: Long?,
): VersionEdit {
    val beforeStart = startMonth != null && month < startMonth
    if (!beforeStart && versions.amountAt(month, latest) == newAmount) return VersionEdit(startMonth, versions, latest)
    val base = versions.ifEmpty { listOf(AmountVersion(startMonth ?: EARLIEST_MONTH, latest)) }
    val updated = withVersion(base, month, newAmount)
    return VersionEdit(if (beforeStart) month else startMonth, updated, updated.last().amountCents)
}
