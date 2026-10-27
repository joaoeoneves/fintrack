package com.joaoeoneves.fintrack.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe Compose Navigation routes for the app's authenticated nav graph. No route takes an
 * enum argument (an enum in a serialized route crashed release builds under R8): filters travel as
 * `String` tokens.
 */
@Serializable
object HomeGraph

@Serializable
object ActivityGraph

@Serializable
object PlanGraph

@Serializable
object InsightsGraph

/** Start destination of [HomeGraph]. */
@Serializable
object Home

/**
 * Start destination of [ActivityGraph]. [period] is an encoded `Period` token, [type] is
 * `"EXPENSE"` or `"INCOME"` and [category] a category id; all are only initial values.
 */
@Serializable
data class Activity(
    val period: String? = null,
    val type: String? = null,
    val category: String? = null,
)

/** Start destination of [PlanGraph]. */
@Serializable
object PlanHome

/** Start destination of [InsightsGraph]; [period] is an encoded `Period` token. */
@Serializable
data class Insights(
    val period: String? = null,
)

/**
 * Superseded by [Activity] and no longer registered in the nav graph; removed with pass A2.
 * [period] is an encoded [com.joaoeoneves.fintrack.domain.model.Period] token, see `Period.encode()`.
 */
@Serializable
data class ExpenseList(
    val period: String,
)

@Serializable
data class AddEditExpense(
    val expenseId: String? = null,
)

/** Superseded by [Activity] like [ExpenseList]; removed with pass A2. */
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

@Serializable
object Categories

@Serializable
object RecurringList

@Serializable
data class AddEditRecurring(
    val recurringId: String? = null,
)

/** [Activity.type] token selecting income; any other value (or none) means expenses. */
const val ACTIVITY_TYPE_INCOME = "INCOME"
