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
