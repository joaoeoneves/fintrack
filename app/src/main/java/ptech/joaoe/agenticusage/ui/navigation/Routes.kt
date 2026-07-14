package ptech.joaoe.agenticusage.ui.navigation

import kotlinx.serialization.Serializable
import ptech.joaoe.agenticusage.domain.model.TimeRange

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
