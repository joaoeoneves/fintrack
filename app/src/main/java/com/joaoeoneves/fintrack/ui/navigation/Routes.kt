package com.joaoeoneves.fintrack.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe Compose Navigation routes for the app's authenticated nav graph.
 */
@Serializable
object Dashboard

/** [period] is an encoded [com.joaoeoneves.fintrack.domain.model.Period] token, see `Period.encode()`. */
@Serializable
data class ExpenseList(
    val period: String,
)

@Serializable
data class AddEditExpense(
    val expenseId: String? = null,
)

/** [period] is an encoded [com.joaoeoneves.fintrack.domain.model.Period] token, see `Period.encode()`. */
@Serializable
data class IncomeList(
    val period: String,
)

@Serializable
data class AddEditIncome(
    val incomeId: String? = null,
)

@Serializable
object Settings
