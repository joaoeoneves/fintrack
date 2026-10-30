package com.joaoeoneves.fintrack.ui.plan.envelopes

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit

/** Why an [EnvelopeDraft] cannot be saved. */
enum class EnvelopeDraftError { BlankName, DuplicateName }

/**
 * The editable state of the group editor (an envelope in the data model): a name and its categories. [id] is null
 * for a new group.
 */
data class EnvelopeDraft(
    val id: String?,
    val name: String,
    val categoryIds: List<CategoryId>,
)

/** The draft of [envelope] (a blank draft when null) with its member categories. */
fun draftOf(
    envelope: Envelope?,
    categories: List<Category>,
): EnvelopeDraft {
    if (envelope == null) return EnvelopeDraft(null, "", emptyList())
    val members = categories.filter { it.groupId == envelope.id }.sortedBy { it.order }.map { it.id }
    return EnvelopeDraft(id = envelope.id, name = envelope.name, categoryIds = members)
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

/** The repository write for [draft]: its name and membership. */
fun envelopeEditOf(draft: EnvelopeDraft): EnvelopeEdit =
    EnvelopeEdit(id = draft.id, name = draft.name.trim(), categoryIds = draft.categoryIds.toSet())
