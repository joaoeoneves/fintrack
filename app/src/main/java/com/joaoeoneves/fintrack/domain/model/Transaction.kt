package com.joaoeoneves.fintrack.domain.model

import java.time.Instant

/**
 * A dated money movement: either an [Expense] (including refunds) or an [Income]. [amountCents] is
 * always positive; [cashFlowCents] is its signed effect on the balance (income and refunds are
 * positive, regular expenses negative).
 */
sealed interface Transaction {
    val id: String
    val amountCents: Long
    val date: Instant
    val note: String?
    val cashFlowCents: Long
}
