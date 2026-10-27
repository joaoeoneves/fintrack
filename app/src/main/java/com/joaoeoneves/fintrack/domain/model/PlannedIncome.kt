package com.joaoeoneves.fintrack.domain.model

/**
 * One line of expected monthly income in the plan (e.g. salary, meal allowance). Planned income is
 * global like budgets: the same lines apply to every month. A blank [id] means "not saved yet".
 */
data class PlannedIncome(
    val id: String,
    val label: String,
    val amountCents: Long,
    val order: Int,
)
