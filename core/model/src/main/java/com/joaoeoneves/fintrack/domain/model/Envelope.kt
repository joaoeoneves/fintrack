package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * A named envelope of categories the plan is summed over (stored as a category group document, [id] is its
 * document id). A null [amountCents] means the envelope's monthly amount is what its categories plan; otherwise
 * it is a fixed amount for every month. [fundedByLineId] is the regular income line that pays for it, if any.
 *
 * [startMonth] is the first plan month the envelope exists in (null = always); [versions] hold the amount history
 * (a null version amount = sum of categories from that month); [amountCents] is the latest.
 */
data class Envelope(
    val id: String,
    val name: String,
    val order: Int,
    val amountCents: Long? = null,
    val fundedByLineId: String? = null,
    val startMonth: YearMonth? = null,
    val versions: List<AmountVersion> = emptyList(),
)

/** This envelope as it is in plan [month] (amount per [versions]), or null before its [Envelope.startMonth]. */
fun Envelope.asOf(month: YearMonth): Envelope? =
    if (startMonth != null && month < startMonth) null else copy(amountCents = versions.amountAt(month, amountCents))

/** The envelopes that exist in plan [month], each resolved with [asOf]. */
fun List<Envelope>.asOf(month: YearMonth): List<Envelope> = mapNotNull { it.asOf(month) }
