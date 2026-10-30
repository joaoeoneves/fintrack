package com.joaoeoneves.fintrack.ui.expense.addedit

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.NameSuggestion
import com.joaoeoneves.fintrack.domain.model.RefundStatus
import com.joaoeoneves.fintrack.domain.model.active
import com.joaoeoneves.fintrack.ui.common.UiText
import java.time.Instant

data class ExpenseFormState(
    val name: String = "",
    val amountCents: Long = 0L,
    val category: Category = BuiltInCategories.SHOPPING,
    val date: Instant = Instant.now(),
    val note: String = "",
    val isRefund: Boolean = false,
    /** Paid with the meal card; only meaningful (and shown) while [mealCardAvailable]. */
    val paidWithMealCard: Boolean = false,
    /** Whether the meal-card switch applies: meals are paid on a card and the meal rate is above 0. */
    val mealCardAvailable: Boolean = false,
    val mealCardTouched: Boolean = false,
    /** The expense month's meal-card plafond is already used up: new expenses then default to the bank. */
    val mealCardPlafondUsed: Boolean = false,
    val categoryTouched: Boolean = false,
    val suggestedCategory: Boolean = false,
    /** Earlier names matching what is typed (new forms only); not part of the saved snapshot. */
    val nameSuggestions: List<NameSuggestion> = emptyList(),
    val nameError: UiText? = null,
    val amountError: UiText? = null,
    val categoryError: UiText? = null,
    val saveError: UiText? = null,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isEditMode: Boolean = false,
    val expenseId: String? = null,
    /** The original expense this refund returns money for (only meaningful while [isRefund]). */
    val refundOf: String? = null,
    /** The original's name, shown in the "Refund of" row; `null` when unlinked. */
    val refundOfLabel: String? = null,
    /** How much of this (original) expense was refunded, in edit mode; not part of the saved snapshot. */
    val refundStatus: RefundStatus? = null,
    /** The split this (existing) expense is a part of. */
    val splitId: String? = null,
    /** A new expense being divided across categories: [splitParts] then replace the single category. */
    val splitEnabled: Boolean = false,
    val splitParts: List<SplitPartForm> = emptyList(),
    val splitError: UiText? = null,
    /** Expenses a refund can link to; not part of the saved snapshot. */
    val refundCandidates: List<Expense> = emptyList(),
) {
    val isValid: Boolean
        get() = name.isNotBlank() && amountCents in 1..MAX_AMOUNT_CENTS

    val isBusy: Boolean
        get() = isSaving || isDeleting

    val isSplitting: Boolean
        get() = splitEnabled && !isEditMode && !isRefund

    /** The part of [amountCents] not assigned to any part yet (negative when over). */
    val splitRemainingCents: Long
        get() = amountCents - splitParts.sumOf { it.amountCents }

    val canDelete: Boolean
        get() = isEditMode && expenseId != null
}

/** One row of the split editor; the [category] is `null` until picked. */
data class SplitPartForm(
    val category: Category?,
    val amountCents: Long,
)

internal fun ExpenseFormState.withoutSplit(): ExpenseFormState {
    val none = emptyList<SplitPartForm>()
    return copy(splitEnabled = false, splitParts = none, splitError = null)
}

/** Why the split cannot be saved yet, or `null` when it can (or it is not a split). */
internal fun ExpenseFormState.splitValidationError(): UiText? =
    when {
        !isSplitting -> null
        splitParts.any { it.category == null } -> UiText.StringResource(R.string.error_split_category)
        splitParts.any { it.amountCents <= 0L } || splitRemainingCents != 0L ->
            UiText.StringResource(R.string.error_split_sum)
        else -> null
    }

/**
 * Whether a new expense starts as paid with the meal card: the switch applies, the expense is not a refund (refunds
 * go back to the bank), the month's card plafond is not used up yet ([plafondUsed], the user can still switch it)
 * and the [category] is a spending one usually paid that way.
 */
fun mealCardPrefill(
    category: Category,
    isRefund: Boolean,
    available: Boolean,
    plafondUsed: Boolean = false,
): Boolean {
    val spending = category.kind == CategoryKind.SPENDING && category.mealCardByDefault
    return available && !plafondUsed && !isRefund && spending
}

/** Whether the meal-card switch shows: the card applies, or the expense being edited is already flagged (N13). */
val ExpenseFormState.showsMealCardSwitch: Boolean get() = mealCardAvailable || (isEditMode && paidWithMealCard)

/** This form with the meal-card switch prefilled from its category (new, untouched expenses only). */
fun ExpenseFormState.withMealCardPrefill(): ExpenseFormState =
    if (isEditMode || mealCardTouched) {
        this
    } else {
        copy(
            paidWithMealCard = mealCardPrefill(category, isRefund, mealCardAvailable, mealCardPlafondUsed),
        )
    }

/**
 * This new-expense form with a quick-add / name prefill: the [name], the [amountCents] when positive, and the category
 * [categoryId] when it is an active one of [catalog] (then marked as suggested). Anything else stays as it was.
 * The date is never part of a prefill.
 */
fun ExpenseFormState.withPrefill(
    name: String?,
    amountCents: Long?,
    categoryId: String?,
    catalog: List<Category>,
): ExpenseFormState {
    val category = categoryId?.let { id -> catalog.active().find { it.id.value == id } }
    return copy(
        name = name?.take(MAX_NAME_LENGTH) ?: this.name,
        amountCents = amountCents?.takeIf { it in 1..MAX_AMOUNT_CENTS } ?: this.amountCents,
        category = category ?: this.category,
        suggestedCategory = category != null,
    )
}

sealed interface AddEditUiState {
    data object Loading : AddEditUiState

    data class Ready(
        val form: ExpenseFormState,
    ) : AddEditUiState

    data class Error(
        val message: UiText,
    ) : AddEditUiState
}
