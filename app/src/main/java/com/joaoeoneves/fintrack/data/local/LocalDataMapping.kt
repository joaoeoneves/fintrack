package com.joaoeoneves.fintrack.data.local

import android.util.Log
import com.joaoeoneves.fintrack.data.DEFAULT_CATEGORY_COLOR_KEY
import com.joaoeoneves.fintrack.data.DEFAULT_CATEGORY_ICON_KEY
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.mergeCategories
import java.time.DateTimeException
import java.time.Instant

private const val TAG = "LocalDataMapping"

internal fun parseDateOrNull(
    raw: String,
    recordKind: String,
    id: String,
): Instant? =
    try {
        Instant.parse(raw)
    } catch (e: DateTimeException) {
        Log.w(TAG, "Dropping malformed $recordKind $id: invalid 'date'", e)
        null
    }

/**
 * Maps to the domain expense, or `null` (logged) when the date is malformed. An id matching no
 * built-in becomes a [Category.unknown] placeholder; the category-resolving decorator maps it to
 * the user's catalog later, exactly as for Firestore expenses.
 */
internal fun LocalExpense.toDomainOrNull(): Expense? =
    parseDateOrNull(date, "expense", id)?.let { instant ->
        Expense(
            id = id,
            name = name,
            amountCents = amountCents,
            category = BuiltInCategories.byId(CategoryId(categoryId)) ?: Category.unknown(CategoryId(categoryId)),
            date = instant,
            note = note,
            isRefund = isRefund,
            paidWithMealCard = paidWithMealCard,
            needsReview = needsReview,
        )
    }

internal fun Expense.toLocal(): LocalExpense =
    LocalExpense(
        id = id,
        name = name,
        amountCents = amountCents,
        categoryId = category.id.value,
        date = date.toString(),
        note = note,
        isRefund = isRefund,
        paidWithMealCard = paidWithMealCard,
        needsReview = needsReview,
    )

internal fun LocalIncome.toDomainOrNull(): Income? =
    parseDateOrNull(date, "income", id)?.let { instant ->
        Income(
            id = id,
            source = source,
            amountCents = amountCents,
            date = instant,
            note = note,
            needsReview = needsReview,
        )
    }

internal fun Income.toLocal(): LocalIncome =
    LocalIncome(
        id = id,
        source = source,
        amountCents = amountCents,
        date = date.toString(),
        note = note,
        needsReview = needsReview,
    )

/**
 * Leniently maps a stored category like the Firestore mapper does: unknown kind falls back to the
 * built-in's (else spending), missing icon/color are not possible (required) but blank ones fall
 * back to the built-in's or generic defaults, and `builtIn` comes from the id. A non-built-in
 * without a name is malformed and dropped (`null`).
 */
internal fun LocalCategory.toDomainOrNull(): Category? {
    val categoryId = CategoryId(id)
    val builtIn = BuiltInCategories.byId(categoryId)
    val customName = name?.takeIf { it.isNotBlank() }
    if (builtIn == null && customName == null) {
        Log.w(TAG, "Dropping malformed category $id: missing or invalid 'name'")
        return null
    }
    return Category(
        id = categoryId,
        customName = customName,
        iconKey = iconKey.ifBlank { builtIn?.iconKey ?: DEFAULT_CATEGORY_ICON_KEY },
        colorKey = colorKey.ifBlank { builtIn?.colorKey ?: DEFAULT_CATEGORY_COLOR_KEY },
        kind = CategoryKind.entries.find { it.name == kind } ?: builtIn?.kind ?: CategoryKind.SPENDING,
        order = order,
        archived = archived,
        builtIn = builtIn != null,
        groupId = groupId?.takeIf { it.isNotBlank() },
        mealCardByDefault = mealCardByDefault,
    )
}

internal fun Category.toLocal(): LocalCategory =
    LocalCategory(
        id = id.value,
        name = customName,
        iconKey = iconKey,
        colorKey = colorKey,
        kind = kind.name,
        order = order,
        archived = archived,
        groupId = groupId,
        mealCardByDefault = mealCardByDefault,
    )

/** The merged catalog (built-ins plus stored categories), never empty. */
internal fun LocalDataDocument.catalog(): List<Category> {
    val stored = categories.mapNotNull { it.toDomainOrNull() }
    return mergeCategories(stored)
}
