package com.joaoeoneves.fintrack.ui.navigation

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.encode
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

/** The [Activity] route showing [category]'s transactions in [period] (a deep link from Insights). */
fun activityRouteFor(
    period: Period,
    category: CategoryId,
): Activity = Activity(period = period.encode(), category = category.value)

/** Start destination of [PlanGraph]. */
@Serializable
object PlanHome

/** Start destination of [InsightsGraph]; [period] is an encoded `Period` token. */
@Serializable
data class Insights(
    val period: String? = null,
)

@Serializable
data class AddEditExpense(
    val expenseId: String? = null,
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

/** The envelopes list, pushed from the Plan tab. */
@Serializable
object PlanEnvelopes

/** The income screen, pushed from the Plan tab; [month] is an encoded `Period` token (null: the current month). */
@Serializable
data class PlanIncome(
    val month: String? = null,
)

/** The envelope editor; a null [envelopeId] creates a new envelope. */
@Serializable
data class EditEnvelope(
    val envelopeId: String? = null,
)

/** The salary profile editor, pushed from the Plan tab; it takes no arguments. */
@Serializable
object EditSalaryProfile

@Serializable
data class AddEditRecurring(
    val recurringId: String? = null,
)

/** [Activity.type] token selecting income; any other value (or none) means expenses. */
const val ACTIVITY_TYPE_INCOME = "INCOME"
