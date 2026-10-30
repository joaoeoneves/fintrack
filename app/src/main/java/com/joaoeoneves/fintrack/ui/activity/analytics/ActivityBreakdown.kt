package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.EnvelopesSummary
import com.joaoeoneves.fintrack.domain.model.SpendingSplit
import com.joaoeoneves.fintrack.ui.common.FALLBACK_COLOR_KEY
import com.joaoeoneves.fintrack.ui.dashboard.CategoryTotal

/** The [EnvelopeChip.id] of the implicit "no envelope" bucket (and the matching envelope filter value). */
const val NO_ENVELOPE = ""

/**
 * What the category breakdown needs: [categoryTotals] actually spent in the period, and whether the period has any
 * expense at all ([hasExpenses]). [envelopes] is the spend per envelope over the period (the implicit "no envelope"
 * row is its unenveloped part); the envelope view needs a real envelope.
 */
data class ActivityBreakdown(
    val categoryTotals: List<CategoryTotal>,
    val hasExpenses: Boolean,
    val envelopes: EnvelopesSummary = EnvelopesSummary(emptyList(), emptyList(), 0L),
) {
    /** Whether the user has defined any envelope (otherwise the envelope view would only say "No envelope"). */
    val hasEnvelopes: Boolean get() = envelopes.envelopes.isNotEmpty()

    /** `true` when there is no spending to show. */
    val isEmpty: Boolean get() = !hasExpenses
}

/** One envelope filter chip: [id] is the envelope id or [NO_ENVELOPE]; [name] is `null` for the no-envelope bucket. */
data class EnvelopeChip(
    val id: String,
    val name: String?,
    val spentCents: Long,
    /** Palette key of the colour of this envelope's bar segment and chip dot (see [envelopeColorKeyOf]). */
    val colorKey: String = FALLBACK_COLOR_KEY,
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

/** One row of the group view of the breakdown: [id] is the envelope id or [NO_ENVELOPE]. */
data class GroupTotal(
    val id: String,
    val name: String,
    val totalCents: Long,
)

/**
 * The spend of [categoryTotals] summed per group (the envelope a category is filed under), biggest first.
 * Categories without a group, or filed under an unknown one, add up under [noGroupLabel], which comes last.
 * Groups with nothing spent are left out.
 */
fun groupTotalsOf(
    categoryTotals: List<CategoryTotal>,
    envelopes: List<Envelope>,
    noGroupLabel: String,
): List<GroupTotal> {
    val known = envelopes.associateBy { it.id }
    val byGroup = categoryTotals.groupBy { it.category.groupId?.takeIf { id -> id in known } ?: NO_ENVELOPE }

    fun sum(id: String) = byGroup[id].orEmpty().sumOf { it.totalCents }
    val groups =
        envelopes
            .map { GroupTotal(it.id, it.name, sum(it.id)) }
            .filter { it.totalCents != 0L }
            .sortedByDescending { it.totalCents }
    val noGroup = GroupTotal(NO_ENVELOPE, noGroupLabel, sum(NO_ENVELOPE)).takeIf { it.totalCents != 0L }
    return groups + listOfNotNull(noGroup)
}
