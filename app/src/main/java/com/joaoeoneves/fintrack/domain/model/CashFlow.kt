package com.joaoeoneves.fintrack.domain.model

/**
 * Share of [incomeCents] that was saved or invested ([savedCents]), as a fraction (0.25 = 25%).
 * Returns `null` when there is no positive income to compare against, since the rate is undefined.
 */
fun savingsRate(
    savedCents: Long,
    incomeCents: Long,
): Double? = if (incomeCents <= 0L) null else savedCents.toDouble() / incomeCents.toDouble()
