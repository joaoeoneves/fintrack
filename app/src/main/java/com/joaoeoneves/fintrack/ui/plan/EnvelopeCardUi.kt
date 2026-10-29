package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.model.EnvelopeProgress
import com.joaoeoneves.fintrack.domain.model.EnvelopesSummary
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.PlannedSource

/**
 * One envelope card of the Plan tab. [id] is `null` for the card of the categories without an envelope. A card
 * is [Budgeted] when the envelope has its own amount or a budget row, [BillsOnly] when only recurring bills plan
 * anything, and [Tracking] when nothing is planned. [fundingLine] is what pays for the envelope (if anything) and
 * [overflowCents] how much the envelope takes beyond that funding
 * (of [fundingCents]).
 */
sealed interface EnvelopeCardUi {
    val id: String?
    val name: String?
    val spentCents: Long
    val fundingLine: PlannedIncome?
    val overflowCents: Long
    val fundingCents: Long

    data class Budgeted(
        override val id: String?,
        override val name: String?,
        val amountCents: Long,
        override val spentCents: Long,
        override val fundingLine: PlannedIncome? = null,
        override val overflowCents: Long = 0L,
        override val fundingCents: Long = 0L,
    ) : EnvelopeCardUi {
        val isOver: Boolean get() = spentCents > amountCents
        val remainingCents: Long get() = amountCents - spentCents
    }

    /** Only recurring bills plan this envelope: [paidCents] of [plannedCents]; never shown as over. */
    data class BillsOnly(
        override val id: String?,
        override val name: String?,
        val plannedCents: Long,
        override val spentCents: Long,
        override val fundingLine: PlannedIncome? = null,
        override val overflowCents: Long = 0L,
        override val fundingCents: Long = 0L,
    ) : EnvelopeCardUi {
        val paidCents: Long get() = spentCents
    }

    data class Tracking(
        override val id: String?,
        override val name: String?,
        override val spentCents: Long,
        override val fundingLine: PlannedIncome? = null,
        override val overflowCents: Long = 0L,
        override val fundingCents: Long = 0L,
    ) : EnvelopeCardUi
}

/** The card of one envelope. */
fun envelopeCardOf(progress: EnvelopeProgress): EnvelopeCardUi {
    val envelope = progress.envelope
    val hasBudgetRow = progress.rows.any { it.plannedSource == PlannedSource.BUDGET }
    val amount = progress.amountCents
    return when {
        amount != null && amount > 0L && (envelope.amountCents != null || hasBudgetRow) ->
            EnvelopeCardUi.Budgeted(
                id = envelope.id,
                name = envelope.name,
                amountCents = amount,
                spentCents = progress.spentCents,
                fundingLine = progress.fundingLine,
                overflowCents = progress.overflowCents,
                fundingCents = progress.fundingCents ?: 0L,
            )
        amount != null && amount > 0L && progress.rows.any { it.plannedSource == PlannedSource.RECURRING } ->
            EnvelopeCardUi.BillsOnly(
                id = envelope.id,
                name = envelope.name,
                plannedCents = amount,
                spentCents = progress.spentCents,
                fundingLine = progress.fundingLine,
                overflowCents = progress.overflowCents,
                fundingCents = progress.fundingCents ?: 0L,
            )
        else ->
            EnvelopeCardUi.Tracking(
                id = envelope.id,
                name = envelope.name,
                spentCents = progress.spentCents,
                fundingLine = progress.fundingLine,
                overflowCents = progress.overflowCents,
                fundingCents = progress.fundingCents ?: 0L,
            )
    }
}

/** The card of the categories without an envelope (`id`/`name` are `null`); `null` when there are none. */
fun unenvelopedCardOf(summary: EnvelopesSummary): EnvelopeCardUi? {
    if (summary.unenveloped.isEmpty()) return null
    val planned = summary.unenvelopedPlannedCents
    val spent = summary.unenvelopedSpentCents
    return if (planned > 0L) {
        EnvelopeCardUi.Budgeted(id = null, name = null, amountCents = planned, spentCents = spent)
    } else {
        EnvelopeCardUi.Tracking(id = null, name = null, spentCents = spent)
    }
}

/** All cards of a month: the envelopes in order, then the unenveloped one. */
fun envelopeCardsOf(summary: EnvelopesSummary): List<EnvelopeCardUi> =
    summary.envelopes.map(::envelopeCardOf) + listOfNotNull(unenvelopedCardOf(summary))
