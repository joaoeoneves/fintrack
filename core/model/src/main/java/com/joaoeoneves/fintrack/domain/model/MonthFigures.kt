package com.joaoeoneves.fintrack.domain.model

/**
 * The month's headline numbers, computed once and shown by Home and the monthly plan alike. They add up:
 * [incomeCents] - [recurringCents] - [envelopesCents] - [savingCents] = [availableCents].
 *
 * @property incomeCents the income basis: the larger of the expected regular income and what was received.
 * @property recurringCents recurring bills of the month (planned, or spent when higher).
 * @property envelopesCents what envelopes and loose categories set aside beyond the recurring bills.
 * @property savingCents what is planned (or already put) into savings.
 */
data class MonthFigures(
    val incomeCents: Long,
    val recurringCents: Long,
    val envelopesCents: Long,
    val savingCents: Long,
) {
    val availableCents: Long get() = incomeCents - recurringCents - envelopesCents - savingCents
}

/** The month's [MonthFigures] from its [EnvelopesSummary]; each month stands on its own, nothing rolls over. */
fun monthFiguresOf(summary: EnvelopesSummary): MonthFigures =
    MonthFigures(
        incomeCents = summary.incomeCents,
        recurringCents = summary.recurringCents,
        envelopesCents =
            summary.incomeCents - summary.freeCents - summary.savingCommittedCents - summary.recurringCents,
        savingCents = summary.savingCommittedCents,
    )
