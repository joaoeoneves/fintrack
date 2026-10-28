package com.joaoeoneves.fintrack.domain.model

/**
 * A named envelope of categories the plan is summed over (stored as a category group document, [id] is its
 * document id). A null [amountCents] means the envelope's monthly amount is what its categories plan; otherwise
 * it is a fixed amount for every month. [fundedByLineId] is the regular income line that pays for it, if any.
 */
data class Envelope(
    val id: String,
    val name: String,
    val order: Int,
    val amountCents: Long? = null,
    val fundedByLineId: String? = null,
)
