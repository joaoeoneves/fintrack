package com.joaoeoneves.fintrack.ui.expense.addedit

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.NameSuggestion
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
) {
    val isValid: Boolean
        get() = name.isNotBlank() && amountCents in 1..MAX_AMOUNT_CENTS

    val isBusy: Boolean
        get() = isSaving || isDeleting

    val canDelete: Boolean
        get() = isEditMode && expenseId != null
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
