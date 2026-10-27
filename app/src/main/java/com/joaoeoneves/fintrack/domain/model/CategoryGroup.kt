package com.joaoeoneves.fintrack.domain.model

/** A user-defined bucket of categories (e.g. Food, Investments) the plan is summed over. [id] is the document id. */
data class CategoryGroup(
    val id: String,
    val name: String,
    val order: Int,
)
