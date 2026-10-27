package com.joaoeoneves.fintrack.ui.settings.categories

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.MAX_CATEGORY_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.active
import com.joaoeoneves.fintrack.domain.model.builtInEnglishNames
import com.joaoeoneves.fintrack.domain.model.canonicalNameOf
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.model.effectiveName
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.common.categoryColorKeys

private const val DEFAULT_ICON_KEY = "label"

/**
 * Validates a category [name] typed in the editor. [editing] is the category being edited (`null`
 * when creating); [all] is the whole catalog, archived included. Blank is only allowed for a
 * built-in (it reverts to the localized default name). The built-ins' ids, English canonical names
 * and every localized default name in [localizedBuiltInNames] are reserved (except the edited
 * built-in's own), so switching the app language can't create a collision.
 */
fun validateCategoryName(
    name: String,
    editing: Category?,
    all: List<Category>,
    localizedBuiltInNames: Map<CategoryId, Set<String>> = emptyMap(),
): UiText? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> {
            if (editing?.builtIn == true) null else UiText.StringResource(R.string.error_category_name_required)
        }

        trimmed.codePointLength() > MAX_CATEGORY_NAME_LENGTH -> {
            UiText.StringResource(R.string.error_category_name_too_long)
        }

        isDuplicateName(trimmed, editing, all, localizedBuiltInNames) -> {
            UiText.StringResource(R.string.error_category_name_duplicate)
        }

        else -> null
    }
}

private fun isDuplicateName(
    trimmed: String,
    editing: Category?,
    all: List<Category>,
    localizedBuiltInNames: Map<CategoryId, Set<String>>,
): Boolean {
    val taken =
        all.filter { it.id != editing?.id }.map { it.effectiveName() } +
            builtInEnglishNames.filterKeys { it != editing?.id }.flatMap { (id, name) -> listOf(id.value, name) } +
            localizedBuiltInNames.filterKeys { it != editing?.id }.values.flatten()
    return taken.any { it.equals(trimmed, ignoreCase = true) }
}

/**
 * The `customName` to store for [name]: trimmed, or `null` when blank or (for a built-in) equal to
 * its own English default name or any of its own localized names, so it keeps following the app
 * language.
 */
fun resolveCustomName(
    name: String,
    editing: Category?,
    localizedBuiltInNames: Map<CategoryId, Set<String>> = emptyMap(),
): String? {
    val trimmed = name.trim()
    val isOwnDefault =
        editing?.builtIn == true &&
            (
                trimmed.equals(canonicalNameOf(editing.id), ignoreCase = true) ||
                    localizedBuiltInNames[editing.id].orEmpty().any { it.equals(trimmed, ignoreCase = true) }
            )
    return trimmed.takeUnless { it.isEmpty() || isOwnDefault }
}

/**
 * The new order of the active categories' ids after moving [id] one step in [direction]; unchanged
 * at the ends or for an unknown/archived id.
 */
fun List<Category>.moved(
    id: CategoryId,
    direction: MoveDirection,
): List<CategoryId> {
    val ids = active().map { it.id }
    val index = ids.indexOf(id)
    val target = if (direction == MoveDirection.UP) index - 1 else index + 1
    return if (index >= 0 && target in ids.indices) {
        ids.toMutableList().apply {
            this[index] = ids[target]
            this[target] = id
        }
    } else {
        ids
    }
}

/** Initial draft for a new category: first color no category uses yet (else the first color). */
fun newCategoryDefaults(
    all: List<Category>,
    colorKeys: List<String> = categoryColorKeys,
): CategoryDraft {
    val used = all.mapTo(mutableSetOf()) { it.colorKey }
    return CategoryDraft(
        name = "",
        iconKey = DEFAULT_ICON_KEY,
        colorKey = colorKeys.firstOrNull { it !in used } ?: colorKeys.first(),
        kind = CategoryKind.SPENDING,
    )
}
