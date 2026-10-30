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
    val refundOf: String? = null,
    val refundOfLabel: String? = null,
    val awaitingRefund: Boolean = false,
    val splitEnabled: Boolean = false,
    val splitParts: List<SplitPartSnapshot> = emptyList(),
)

/** A split part as saved: the category id is empty while none is picked. */
@Serializable
data class SplitPartSnapshot(
    val categoryId: String,
    val amountCents: Long,
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
        refundOf = refundOf,
        refundOfLabel = refundOfLabel,
        awaitingRefund = awaitingRefund,
        splitEnabled = splitEnabled,
        splitParts =
            splitParts.map {
                SplitPartSnapshot(
                    it.category
                        ?.id
                        ?.value
                        .orEmpty(),
                    it.amountCents,
                )
            },
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
            refundOf = snapshot.refundOf?.takeIf { snapshot.isRefund },
            refundOfLabel = snapshot.refundOfLabel?.takeIf { snapshot.isRefund },
            awaitingRefund = snapshot.awaitingRefund && !snapshot.isRefund,
            splitEnabled = snapshot.splitEnabled && !isEditMode,
            splitParts =
                if (isEditMode) {
                    emptyList()
                } else {
                    snapshot.splitParts.map { part ->
                        SplitPartForm(
                            part.categoryId.takeIf { it.isNotEmpty() }?.let { catalog.resolve(CategoryId(it)) },
                            part.amountCents,
                        )
                    }
                },
        )
    }
