package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.plannedSlices
import com.joaoeoneves.fintrack.domain.model.spendingSplitOf

/** The analytics numbers over the whole period (before any list filter), shared by Activity and Evolution. */
internal fun PeriodOverview.analyticsOf(
    selectedEnvelope: String?,
    catalog: List<Category>,
): ActivityAnalytics {
    val split = spendingSplitOf(transactions.expenses)
    val envelopeSummary = periodEnvelopes
    val kept = envelopeSummary.envelopes.filter { it.spentCents > 0 || it.envelope.id == selectedEnvelope }
    val showUnenveloped = envelopeSummary.unenvelopedSpentCents > 0 || selectedEnvelope == NO_ENVELOPE
    val keys = envelopeColorKeysOf(kept.map { it.envelope.id }, catalog)
    val chips =
        kept.mapIndexed { i, e -> EnvelopeChip(e.envelope.id, e.envelope.name, e.spentCents, keys[i]) } +
            listOfNotNull(
                EnvelopeChip(
                    NO_ENVELOPE,
                    null,
                    envelopeSummary.unenvelopedSpentCents,
                    envelopeColorKeyOf(NO_ENVELOPE, catalog),
                ).takeIf { showUnenveloped },
            )
    return ActivityAnalytics(
        spentCents = split.spendingCents,
        split = split,
        breakdown =
            ActivityBreakdown(
                categoryTotals = summary.categoryTotals,
                plannedSlices = budgets.plannedSlices(),
                hasExpenses = transactions.expenses.isNotEmpty(),
                envelopes = envelopeSummary,
            ),
        envelopeChips = chips,
        envelopeIds = envelopeSummary.envelopes.map { it.envelope.id }.toSet(),
    )
}
