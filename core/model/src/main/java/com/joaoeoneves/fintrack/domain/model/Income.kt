package com.joaoeoneves.fintrack.domain.model

import java.time.Instant

data class Income(
    override val id: String,
    val source: String,
    override val amountCents: Long,
    override val date: Instant,
    override val note: String?,
) : Transaction {
    /** Balance effect: income always raises the balance. */
    override val cashFlowCents: Long get() = amountCents
}
