package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit

private const val MAX_ID_LENGTH = 64

/**
 * The writes that move a pre-envelope plan to envelopes: [envelopes] to save (new ones with their members, existing
 * ones that gain a funding line) and the income [lines] to save again without their legacy earmark. Empty once the
 * data is migrated.
 */
data class EnvelopeMigration(
    val envelopes: List<EnvelopeEdit>,
    val lines: List<PlannedIncome>,
    /** Earmarks that could not become a funding line (a taken envelope or line, or a dangling target). */
    val dropped: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = envelopes.isEmpty() && lines.isEmpty()
}

/**
 * Computes the [EnvelopeMigration] of the plan. Groups stay envelopes (no amount: their categories' limits are
 * kept). A budgeted category in no (or an unknown) envelope gets its own envelope `env_<categoryId>`, named by
 * [nameOf]. A GROUP earmark funds that envelope, a CATEGORY earmark the envelope containing the category (created
 * when none). The line with the lowest order wins an envelope, and a line funds only its first envelope; every
 * line that had an earmark is saved again without it.
 */
fun envelopeMigrationOf(
    budgets: List<Budget>,
    categories: List<Category>,
    envelopes: List<Envelope>,
    lines: List<PlannedIncome>,
    nameOf: (Category) -> String,
): EnvelopeMigration {
    val state = MigrationState(categories, envelopes, nameOf)
    budgets.forEach { budget ->
        val category = categories.resolve(budget.categoryId)
        if (!category.isUnknown) state.envelopeOf(category)
    }
    val dropped = mutableListOf<String>()
    val usedLines = mutableSetOf<String>()
    val earmarked = lines.filter { it.earmark != null }.sortedWith(compareBy({ it.order }, { it.id }))
    earmarked.forEach { line ->
        val target = state.targetOf(line.earmark)
        if (target == null || line.id in usedLines || !state.fund(target, line.id)) {
            dropped += line.id
        } else {
            usedLines += line.id
        }
    }
    return EnvelopeMigration(state.edits(), earmarked.map { it.copy(earmark = null) }, dropped)
}

private class MigrationState(
    private val categories: List<Category>,
    existing: List<Envelope>,
    private val nameOf: (Category) -> String,
) {
    private val envelopes = existing.sortedWith(compareBy({ it.order }, { it.id })).associateBy { it.id }.toMutableMap()
    private val untouched = existing.associateBy { it.id }
    private val added = mutableMapOf<String, MutableSet<CategoryId>>()
    private var nextOrder = (existing.maxOfOrNull { it.order } ?: -1) + 1

    private fun groupOf(category: Category): String? = category.groupId?.takeIf { it in envelopes }

    /** The envelope [category] is in, created (as `env_<id>`) when it is in none. */
    fun envelopeOf(category: Category): String {
        groupOf(category)?.let { return it }
        val id = "env_${category.id.value}".take(MAX_ID_LENGTH)
        if (id !in envelopes) envelopes[id] = Envelope(id, nameOf(category).trim().take(MAX_NAME), nextOrder++)
        added.getOrPut(id) { mutableSetOf() }.add(category.id)
        return id
    }

    fun targetOf(earmark: Earmark?): String? =
        when (earmark) {
            is Earmark.Group -> earmark.id.takeIf { it in envelopes }
            is Earmark.Category ->
                categories.resolve(CategoryId(earmark.id)).takeIf { !it.isUnknown }?.let(::envelopeOf)
            null -> null
        }

    /** Names [lineId] as [envelopeId]'s funding; false when the envelope already has a funding line. */
    fun fund(
        envelopeId: String,
        lineId: String,
    ): Boolean {
        val current = envelopes.getValue(envelopeId)
        if (current.fundedByLineId != null) return false
        envelopes[envelopeId] = current.copy(fundedByLineId = lineId)
        added.getOrPut(envelopeId) { mutableSetOf() }
        return true
    }

    fun edits(): List<EnvelopeEdit> =
        envelopes.values
            .filter { envelope ->
                val before = untouched[envelope.id]
                envelope.id in added &&
                    (
                        before == null ||
                            before.fundedByLineId != envelope.fundedByLineId ||
                            added.getValue(envelope.id).isNotEmpty()
                    )
            }.map { envelope ->
                val members =
                    categories.filter { it.groupId == envelope.id }.mapTo(mutableSetOf()) { it.id } +
                        added.getValue(envelope.id)
                EnvelopeEdit(
                    id = envelope.id,
                    name = envelope.name,
                    amountCents = envelope.amountCents,
                    fundedByLineId = envelope.fundedByLineId,
                    categoryIds = members,
                    limits = emptyMap(),
                )
            }
}

private const val MAX_NAME = 50
