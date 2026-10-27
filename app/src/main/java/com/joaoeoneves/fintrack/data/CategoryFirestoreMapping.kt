package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind

private const val TAG = "CategoryMapping"

/** Icon key used for a category document without one (resolved to a generic tag icon). */
internal const val DEFAULT_CATEGORY_ICON_KEY = "label"

/** Color key used for a category document without one (and the UI's fallback color). */
internal const val DEFAULT_CATEGORY_COLOR_KEY = "slate"

/**
 * Leniently maps a `users/{uid}/categories/{id}` document: an unknown kind becomes
 * [CategoryKind.SPENDING], a missing order sorts last, and missing icon/color keys fall back to
 * the built-in's own keys (for an override document) or to generic defaults. [Category.builtIn] is
 * derived from the id, never trusted from the document. A non-built-in document without a name is
 * malformed and dropped.
 */
internal fun DocumentSnapshot.toCategoryOrNull(): Category? = mapOrNull("category") { parseCategory() }

private fun DocumentSnapshot.parseCategory(): Category? {
    val categoryId = CategoryId(id)
    val builtIn = BuiltInCategories.byId(categoryId)
    val name = getString("name")?.takeIf { it.isNotBlank() }
    if (builtIn == null && name == null) {
        Log.w(TAG, "Dropping malformed category doc $id: missing or invalid 'name'")
        return null
    }
    val kindName = getString("kind")
    return Category(
        id = categoryId,
        customName = name,
        iconKey = getString("iconKey") ?: builtIn?.iconKey ?: DEFAULT_CATEGORY_ICON_KEY,
        colorKey = getString("colorKey") ?: builtIn?.colorKey ?: DEFAULT_CATEGORY_COLOR_KEY,
        kind = CategoryKind.entries.find { it.name == kindName } ?: builtIn?.kind ?: CategoryKind.SPENDING,
        order = getLong("order")?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt() ?: Int.MAX_VALUE,
        archived = getBoolean("archived") ?: builtIn?.archived ?: false,
        builtIn = builtIn != null,
    )
}

/**
 * The full document for this category (every field, so an override of a built-in never ends up
 * partial). `builtIn` is derived from the id, matching what firestore.rules requires.
 */
internal fun Category.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        "name" to customName,
        "iconKey" to iconKey,
        "colorKey" to colorKey,
        "kind" to kind.name,
        "order" to order,
        "archived" to archived,
        "builtIn" to BuiltInCategories.isBuiltIn(id),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
