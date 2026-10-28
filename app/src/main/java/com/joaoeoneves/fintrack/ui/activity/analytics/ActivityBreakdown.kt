package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.EnvelopesSummary
import com.joaoeoneves.fintrack.domain.model.SpendingSplit
import com.joaoeoneves.fintrack.ui.dashboard.CategoryTotal

/** The [EnvelopeChip.id] of the implicit "no envelope" bucket (and the matching envelope filter value). */
const val NO_ENVELOPE = ""

/**
 * What the category breakdown needs: [categoryTotals] actually spent in the period, the
 * [plannedSlices] of the plan, and whether the period has any expense at all ([hasExpenses]). [envelopes] is the
 * spend per envelope over the period (the implicit "no envelope" row is its unenveloped part); the envelope view
 * needs a real envelope.
 */
data class ActivityBreakdown(
    val categoryTotals: List<CategoryTotal>,
    val plannedSlices: List<CategoryTotal>,
    val hasExpenses: Boolean,
    val envelopes: EnvelopesSummary = EnvelopesSummary(emptyList(), emptyList(), 0L),
) {
    /** Whether the user has defined any envelope (otherwise the envelope view would only say "No envelope"). */
    val hasEnvelopes: Boolean get() = envelopes.envelopes.isNotEmpty()

    /** `true` when there is neither spending nor a plan to show. */
    val isEmpty: Boolean get() = !hasExpenses && plannedSlices.isEmpty()
}

/** One envelope filter chip: [id] is the envelope id or [NO_ENVELOPE]; [name] is `null` for the no-envelope bucket. */
data class EnvelopeChip(
    val id: String,
    val name: String?,
    val spentCents: Long,
)

/**
 * The analytics header's numbers for the whole period (before the list filters): [spentCents] excludes savings,
 * [split] is fixed vs variable, [envelopeChips] only holds envelopes with spend (plus the selected one) and
 * [envelopeIds] every existing envelope.
 */
data class ActivityAnalytics(
    val spentCents: Long,
    val split: SpendingSplit,
    val breakdown: ActivityBreakdown,
    val envelopeChips: List<EnvelopeChip>,
    val envelopeIds: Set<String> = emptySet(),
)
