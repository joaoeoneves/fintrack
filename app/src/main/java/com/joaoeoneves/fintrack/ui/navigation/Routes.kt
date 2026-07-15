package com.joaoeoneves.fintrack.ui.navigation

import com.joaoeoneves.fintrack.domain.model.TimeRange
import kotlinx.serialization.Serializable

/**
 * Type-safe Compose Navigation routes for the app's authenticated nav graph.
 */
@Serializable
object Dashboard

@Serializable
data class ExpenseList(
    val timeRange: TimeRange,
)

@Serializable
data class AddEditExpense(
    val expenseId: String? = null,
)

@Serializable
data class IncomeList(
    val timeRange: TimeRange,
)

@Serializable
data class AddEditIncome(
    val incomeId: String? = null,
)
