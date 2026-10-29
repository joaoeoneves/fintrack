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
object IncomeGraph

@Serializable
object SpendingGraph

/** Start destination of [HomeGraph]. */
@Serializable
object Home

/**
 * Start destination of [ActivityGraph]. [period] is an encoded `Period` token, [type] is
 * `"EXPENSE"` or `"INCOME"`, [category] a category id and [envelope] an envelope id (`""` for no envelope); all
 * are only initial values.
 */
@Serializable
data class Activity(
    val period: String? = null,
    val type: String? = null,
    val category: String? = null,
    val envelope: String? = null,
)

/** Start destination of [IncomeGraph]; [period] is an encoded `Period` token (month or year; null: this month). */
@Serializable
data class IncomeHome(
    val period: String? = null,
)

/** Start destination of [SpendingGraph]; [period] is an encoded `Period` token (month or year; null: this month). */
@Serializable
data class SpendingHome(
    val period: String? = null,
)

/** The expense form; [period] is the encoded past month it was opened from (a new expense then defaults to it). */
@Serializable
data class AddEditExpense(
    val expenseId: String? = null,
    val period: String? = null,
    /** Optional prefill for a new expense (quick-add chips); ignored when editing. */
    val prefillName: String? = null,
    val prefillAmountCents: Long? = null,
    val prefillCategoryId: String? = null,
)

/** The income form; [period] is the encoded past month it was opened from (a new income then defaults to it). */
@Serializable
data class AddEditIncome(
    val incomeId: String? = null,
    val period: String? = null,
)

@Serializable
object Settings

/** The categories list; a non-null [editId] opens that category's editor once, on arrival. */
@Serializable
data class Categories(
    val editId: String? = null,
)

@Serializable
object RecurringList

/** The envelope editor; a null [envelopeId] creates a new envelope. */
@Serializable
data class EditEnvelope(
    val envelopeId: String? = null,
)

/** The salary profile editor, pushed on top of whichever tab is current; it takes no arguments. */
@Serializable
object EditSalaryProfile

/** The three-step salary set-up wizard; with a stored profile it forwards to [EditSalaryProfile]. */
@Serializable
object SalarySetup

@Serializable
data class AddEditRecurring(
    val recurringId: String? = null,
    /** Optional prefill for a new recurring expense ("make it recurring?" suggestion); ignored when editing. */
    val prefillName: String? = null,
    val prefillAmountCents: Long? = null,
    val prefillCategoryId: String? = null,
    val prefillDay: Int? = null,
)

/**
 * A category's detail page: 12 months of spending and the movements of one month. [period] is an encoded `Period`
 * token (the month shown first).
 */
@Serializable
data class CategoryDetail(
    val categoryId: String,
    val period: String,
)

/**
 * An income source's detail page. [sourceKey] is `IncomeSourceKey.encode()`; [period] an encoded `Period` token
 * (the month shown first).
 */
@Serializable
data class IncomeSourceDetail(
    val sourceKey: String,
    val period: String,
)

/** [Activity.type] token selecting income; any other value (or none) means expenses. */
const val ACTIVITY_TYPE_INCOME = "INCOME"
