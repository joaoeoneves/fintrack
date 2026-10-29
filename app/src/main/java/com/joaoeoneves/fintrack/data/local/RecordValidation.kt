package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_CATEGORY_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MAX_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MAX_NOTE_LENGTH
import com.joaoeoneves.fintrack.domain.model.MIN_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.codePointLength
import java.time.DateTimeException
import java.time.Instant

// Local mirror of the checks firestore.rules applies to the signed-in store. Every function throws
// IllegalArgumentException for a violation.

private const val MAX_CATEGORY_ID_LENGTH = 64
private const val MAX_KEY_LENGTH = 32
private val CATEGORY_ID_REGEX = Regex("^[A-Za-z0-9_-]+$")
private val KEY_REGEX = Regex("^[a-z0-9_]+$")

/** Ids a record may reference as its category: the built-ins plus every stored category. */
internal fun knownCategoryIds(doc: LocalDataDocument): Set<String> =
    BuiltInCategories.all.mapTo(mutableSetOf()) { it.id.value } + doc.categories.map { it.id }

internal fun validateExpense(
    expense: LocalExpense,
    knownCategoryIds: Set<String>,
) {
    requireIdShape(expense.id, "expense")
    requireText(expense.name, MAX_NAME_LENGTH, "Expense name")
    require(expense.amountCents in 1..MAX_AMOUNT_CENTS) { "Expense amount must be 1..$MAX_AMOUNT_CENTS cents" }
    requireKnownCategory(expense.categoryId, knownCategoryIds)
    requireValidDate(expense.date)
    requireValidNote(expense.note)
}

internal fun validateIncome(income: LocalIncome) {
    requireIdShape(income.id, "income")
    requireText(income.source, MAX_NAME_LENGTH, "Income source")
    require(income.amountCents in 1..MAX_AMOUNT_CENTS) { "Income amount must be 1..$MAX_AMOUNT_CENTS cents" }
    requireValidDate(income.date)
    requireValidNote(income.note)
}

internal fun validateBudget(
    categoryId: String,
    limitCents: Long,
    knownCategoryIds: Set<String>,
    skipMonths: Collection<Int> = emptyList(),
) {
    requireKnownCategory(categoryId, knownCategoryIds)
    require(limitCents in 1..MAX_AMOUNT_CENTS) { "Budget limit must be 1..$MAX_AMOUNT_CENTS cents" }
    val months = MIN_MONTH_OF_YEAR..MAX_MONTH_OF_YEAR
    require(skipMonths.size <= MAX_MONTH_OF_YEAR && skipMonths.all { it in months }) {
        "Skipped months must be at most 12 values of 1..12"
    }
}

/** A category is written whole; a built-in's override may leave the name null, a user one may not. */
internal fun validateCategory(category: LocalCategory) {
    requireCategoryIdShape(category.id)
    val name = category.name
    require(name == null || (name.isNotEmpty() && name.codePointLength() <= MAX_CATEGORY_NAME_LENGTH)) {
        "Category name must be 1..$MAX_CATEGORY_NAME_LENGTH characters"
    }
    require(KEY_REGEX.matches(category.iconKey) && category.iconKey.length <= MAX_KEY_LENGTH) { "Invalid icon key" }
    require(KEY_REGEX.matches(category.colorKey) && category.colorKey.length <= MAX_KEY_LENGTH) { "Invalid color key" }
    require(CategoryKind.entries.any { it.name == category.kind }) { "Invalid category kind ${category.kind}" }
    category.groupId?.let(::requireCategoryIdShape)
    // Blank-after-trim counts as no name: the mappers treat it that way, so validation must agree.
    val builtIn = category.id in BuiltInCategories.all.map { it.id.value }
    require(builtIn || !name.isNullOrBlank()) { "A user category needs a name" }
}

internal fun requireCategoryIdShape(id: String) = requireIdShape(id, "category")

/** The shape firestore.rules' `isCategoryIdShape` demands of an id: 1..64 characters of `[A-Za-z0-9_-]`. */
internal fun requireIdShape(
    id: String,
    what: String,
) {
    require(id.isNotEmpty() && id.length <= MAX_CATEGORY_ID_LENGTH && CATEGORY_ID_REGEX.matches(id)) {
        "Invalid $what id"
    }
}

internal fun requireKnownCategory(
    id: String,
    known: Set<String>,
) {
    requireCategoryIdShape(id)
    require(id in known) { "Unknown category $id" }
}

internal fun requireText(
    value: String,
    max: Int,
    label: String,
) {
    require(value.isNotEmpty() && value.codePointLength() <= max) { "$label must be 1..$max characters" }
}

internal fun requireValidNote(note: String?) {
    require(note == null || note.codePointLength() <= MAX_NOTE_LENGTH) { "Note exceeds $MAX_NOTE_LENGTH characters" }
}

private fun requireValidDate(date: String) {
    try {
        Instant.parse(date)
    } catch (e: DateTimeException) {
        throw IllegalArgumentException("Invalid date", e)
    }
}
