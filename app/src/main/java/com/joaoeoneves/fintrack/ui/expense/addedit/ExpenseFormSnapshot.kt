package com.joaoeoneves.fintrack.ui.expense.addedit

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.resolve
import kotlinx.serialization.Serializable
import java.time.Instant

/** The fields of the expense form that survive process death. */
@Serializable
data class ExpenseFormSnapshot(
    val expenseId: String?,
    val name: String,
    val amountCents: Long,
    val categoryId: String,
    val dateEpochMillis: Long,
    val note: String,
    val isRefund: Boolean,
    val paidWithMealCard: Boolean,
    val mealCardTouched: Boolean,
    val categoryTouched: Boolean,
)

internal fun ExpenseFormState.toSnapshot() =
    ExpenseFormSnapshot(
        expenseId = expenseId,
        name = name,
        amountCents = amountCents,
        categoryId = category.id.value,
        dateEpochMillis = date.toEpochMilli(),
        note = note,
        isRefund = isRefund,
        paidWithMealCard = paidWithMealCard,
        mealCardTouched = mealCardTouched,
        categoryTouched = categoryTouched,
    )

/** This form with the saved [snapshot]'s fields, when it belongs to the same expense (or both are new). */
internal fun ExpenseFormState.restoredFrom(
    snapshot: ExpenseFormSnapshot?,
    catalog: List<Category>,
): ExpenseFormState =
    if (snapshot == null || snapshot.expenseId != expenseId) {
        this
    } else {
        copy(
            name = snapshot.name,
            amountCents = snapshot.amountCents,
            category = catalog.resolve(CategoryId(snapshot.categoryId)),
            date = Instant.ofEpochMilli(snapshot.dateEpochMillis),
            note = snapshot.note,
            isRefund = snapshot.isRefund,
            paidWithMealCard = snapshot.paidWithMealCard,
            mealCardTouched = snapshot.mealCardTouched,
            categoryTouched = snapshot.categoryTouched,
        )
    }
