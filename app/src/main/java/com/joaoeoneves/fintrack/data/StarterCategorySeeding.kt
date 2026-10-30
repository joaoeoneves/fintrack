package com.joaoeoneves.fintrack.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.StarterCategories

private const val TAG = "StarterCategorySeeding"

/**
 * Writes the four starter categories under `users/{uid}/categories` for a brand-new account. Fixed ids make
 * repeating it harmless; the writes are not awaited, like every other write.
 */
internal fun seedStarterCategories(
    firestore: FirebaseFirestore,
    uid: String,
    names: Map<String, String>,
) {
    StarterCategories.specs.forEach { spec ->
        val name = names[spec.id] ?: return@forEach
        val category =
            Category(
                id = CategoryId(spec.id),
                customName = name,
                iconKey = spec.iconKey,
                colorKey = spec.colorKey,
                kind = CategoryKind.SPENDING,
                order = spec.order,
                archived = false,
                builtIn = false,
            )
        val docRef =
            firestore
                .collection("users")
                .document(uid)
                .collection("categories")
                .document(spec.id)
        val data = category.toFirestoreMap() + ("createdAt" to FieldValue.serverTimestamp())
        docRef.set(data).addOnFailureListener(onFail(TAG, "Seed starter category ${docRef.id} failed"))
    }
}
