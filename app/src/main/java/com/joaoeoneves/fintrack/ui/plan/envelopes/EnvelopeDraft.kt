package com.joaoeoneves.fintrack.ui.plan.envelopes

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import java.time.YearMonth

/** Why an [EnvelopeDraft] cannot be saved. */
enum class EnvelopeDraftError { BlankName, DuplicateName }

/**
 * The editable state of the group editor (an envelope in the data model): a name and its categories. [id] is null
 * for a new group. [amountCents] and [fundedByLineId] are not editable any more but are carried unchanged so a save
 * never loses what is stored. [effectiveFrom] only applies to a new group's start month.
 */
data class EnvelopeDraft(
    val id: String?,
    val name: String,
    val categoryIds: List<CategoryId>,
    val amountCents: Long? = null,
    val fundedByLineId: String? = null,
    val effectiveFrom: YearMonth = YearMonth.now(),
)

/** The draft of [envelope] (a blank draft when null) with its member categories. */
fun draftOf(
    envelope: Envelope?,
    categories: List<Category>,
    effectiveFrom: YearMonth = YearMonth.now(),
): EnvelopeDraft {
    if (envelope == null) return EnvelopeDraft(null, "", emptyList(), null, null, effectiveFrom)
    val members = categories.filter { it.groupId == envelope.id }.sortedBy { it.order }.map { it.id }
    return EnvelopeDraft(
        id = envelope.id,
        name = envelope.name,
        categoryIds = members,
        amountCents = envelope.amountCents,
        fundedByLineId = envelope.fundedByLineId,
        effectiveFrom = effectiveFrom,
    )
}

/** The first problem of [draft], or null when it can be saved; [otherEnvelopeNames] are the other groups' names. */
fun validateEnvelope(
    draft: EnvelopeDraft,
    otherEnvelopeNames: Collection<String>,
): EnvelopeDraftError? {
    val name = draft.name.trim()
    return when {
        name.isEmpty() -> EnvelopeDraftError.BlankName
        otherEnvelopeNames.any { it.trim().equals(name, ignoreCase = true) } -> EnvelopeDraftError.DuplicateName
        else -> null
    }
}

/**
 * The repository write for [draft]: name and membership change; the stored amount and funding line are copied
 * unchanged and no category limit is touched (the repository keeps the amount history and start month as they are).
 */
fun envelopeEditOf(draft: EnvelopeDraft): EnvelopeEdit =
    EnvelopeEdit(
        id = draft.id,
        name = draft.name.trim(),
        amountCents = draft.amountCents,
        fundedByLineId = draft.fundedByLineId,
        categoryIds = draft.categoryIds.toSet(),
        limits = emptyMap(),
        effectiveFrom = draft.effectiveFrom,
    )
