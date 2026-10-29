package com.joaoeoneves.fintrack.ui.plan.envelopes

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import java.time.YearMonth

/** How an envelope's monthly budget is set in the editor. */
enum class EnvelopeBudgetMode { SINGLE, PER_CATEGORY, NONE }

/** Why an [EnvelopeDraft] cannot be saved. */
enum class EnvelopeDraftError { BlankName, DuplicateName, MissingAmount }

/**
 * The editable state of the envelope editor. [id] is null for a new envelope; [limits] holds the per-category
 * monthly limits (null or 0 means no limit) and only matters in [EnvelopeBudgetMode.PER_CATEGORY].
 */
data class EnvelopeDraft(
    val id: String?,
    val name: String,
    val mode: EnvelopeBudgetMode,
    val amountCents: Long?,
    val categoryIds: List<CategoryId>,
    val limits: Map<CategoryId, Long?>,
    val fundedByLineId: String?,
    /** The plan month a changed amount or limit (or a new envelope) applies from; defaults to the current month. */
    val effectiveFrom: YearMonth = YearMonth.now(),
)

/** The draft of [envelope] (a blank single-amount draft when null) with its member categories and their budgets. */
fun draftOf(
    envelope: Envelope?,
    categories: List<Category>,
    budgets: List<Budget>,
    effectiveFrom: YearMonth = YearMonth.now(),
): EnvelopeDraft {
    if (envelope == null) {
        return EnvelopeDraft(null, "", EnvelopeBudgetMode.SINGLE, null, emptyList(), emptyMap(), null, effectiveFrom)
    }
    val members = categories.filter { it.groupId == envelope.id }.sortedBy { it.order }.map { it.id }
    val byCategory = budgets.associateBy { it.categoryId }
    val limits = members.associateWith { byCategory[it]?.limitCents }
    val mode =
        when {
            envelope.amountCents != null -> EnvelopeBudgetMode.SINGLE
            limits.values.any { it != null } -> EnvelopeBudgetMode.PER_CATEGORY
            else -> EnvelopeBudgetMode.NONE
        }
    return EnvelopeDraft(
        id = envelope.id,
        name = envelope.name,
        mode = mode,
        amountCents = envelope.amountCents,
        categoryIds = members,
        limits = if (mode == EnvelopeBudgetMode.PER_CATEGORY) limits else emptyMap(),
        fundedByLineId = envelope.fundedByLineId,
        effectiveFrom = effectiveFrom,
    )
}

/** The first problem of [draft], or null when it can be saved; [otherEnvelopeNames] are the other envelopes' names. */
fun validateEnvelope(
    draft: EnvelopeDraft,
    otherEnvelopeNames: Collection<String>,
): EnvelopeDraftError? {
    val name = draft.name.trim()
    return when {
        name.isEmpty() -> EnvelopeDraftError.BlankName
        otherEnvelopeNames.any { it.trim().equals(name, ignoreCase = true) } -> EnvelopeDraftError.DuplicateName
        draft.mode == EnvelopeBudgetMode.SINGLE && (draft.amountCents ?: 0L) <= 0L -> EnvelopeDraftError.MissingAmount
        else -> null
    }
}

/**
 * The repository write for [draft]. A single amount replaces the category limits, per-category mode writes the
 * typed limits (keeping each existing budget's skipped months) and no budget clears them; categories that were
 * in the envelope ([originalCategoryIds]) but are no longer lose their limit in every mode.
 */
fun envelopeEditOf(
    draft: EnvelopeDraft,
    existingBudgets: List<Budget>,
    originalCategoryIds: Set<CategoryId>,
): EnvelopeEdit {
    val members = draft.categoryIds.toSet()
    val existing = existingBudgets.associateBy { it.categoryId }
    val removed: Map<CategoryId, Budget?> = (originalCategoryIds - members).associateWith { null }
    val limits: Map<CategoryId, Budget?> =
        when (draft.mode) {
            EnvelopeBudgetMode.SINGLE -> removed
            EnvelopeBudgetMode.PER_CATEGORY ->
                removed +
                    members.associateWith { id ->
                        val cents = draft.limits[id] ?: 0L
                        if (cents > 0L) Budget(id, cents, existing[id]?.skipMonths.orEmpty()) else null
                    }
            EnvelopeBudgetMode.NONE -> removed + members.associateWith { null }
        }
    return EnvelopeEdit(
        id = draft.id,
        name = draft.name.trim(),
        amountCents = draft.amountCents.takeIf { draft.mode == EnvelopeBudgetMode.SINGLE },
        fundedByLineId = draft.fundedByLineId,
        categoryIds = members,
        limits = limits,
        effectiveFrom = draft.effectiveFrom,
    )
}

/**
 * Whether [current]'s budget differs from [original]'s: the mode, the single amount or the per-category limits.
 * The "Valid from" row of an existing envelope only shows once this is true.
 */
fun budgetChanged(
    original: EnvelopeDraft,
    current: EnvelopeDraft,
): Boolean = original.budgetSignature() != current.budgetSignature()

private fun EnvelopeDraft.budgetSignature(): Triple<EnvelopeBudgetMode, Long?, Map<CategoryId, Long>> =
    Triple(
        mode,
        amountCents.takeIf { mode == EnvelopeBudgetMode.SINGLE },
        if (mode == EnvelopeBudgetMode.PER_CATEGORY) {
            limits.mapNotNull { (id, cents) -> cents?.takeIf { it > 0L }?.let { id to it } }.toMap()
        } else {
            emptyMap()
        },
    )
