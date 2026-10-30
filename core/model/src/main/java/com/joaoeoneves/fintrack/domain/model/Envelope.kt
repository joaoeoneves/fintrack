package com.joaoeoneves.fintrack.domain.model

/**
 * A named envelope of categories the plan is summed over (stored as a category group document, [id] is its
 * document id). Its monthly amount is what its categories plan.
 */
data class Envelope(
    val id: String,
    val name: String,
    val order: Int,
)
