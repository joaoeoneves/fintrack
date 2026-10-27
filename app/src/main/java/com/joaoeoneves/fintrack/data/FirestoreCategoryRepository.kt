package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.mergeCategories
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

private const val TAG = "FirestoreCategoryRepo"
private const val COLLECTION = "categories"

/**
 * Categories stored under `users/{uid}/categories`, merged with the [BuiltInCategories]. Writes
 * are not awaited (they land in the local cache immediately and sync when online), and always
 * write the full document, merged so `createdAt` survives updates.
 */
class FirestoreCategoryRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : CategoryRepository {
        private fun collection(uid: String) = firestore.collection("users").document(uid).collection(COLLECTION)

        override fun observeCategories(): Flow<List<Category>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(BuiltInCategories.all)

            return callbackFlow {
                val registration =
                    collection(uid).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            // Fall back to the built-ins instead of failing every screen that needs
                            // the catalog (e.g. permission denied before the rules are deployed).
                            Log.w(TAG, "Category listener failed; using built-in categories", error)
                            trySend(BuiltInCategories.all)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(mergeCategories(snapshot.documents.mapNotNull { it.toCategoryOrNull() }))
                        }
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun addCategory(
            name: String,
            iconKey: String,
            colorKey: String,
            kind: CategoryKind,
            groupId: String?,
        ): Result<CategoryId> =
            withUid { uid ->
                val docRef = collection(uid).document()
                val nextOrder =
                    observeCategories()
                        .first()
                        .map { it.order }
                        .filter { it != Int.MAX_VALUE }
                        .maxOrNull()
                        ?.plus(1) ?: 0
                val category =
                    Category(
                        id = CategoryId(docRef.id),
                        customName = name,
                        iconKey = iconKey,
                        colorKey = colorKey,
                        kind = kind,
                        order = nextOrder,
                        archived = false,
                        builtIn = false,
                        groupId = groupId,
                    )
                val data = category.toFirestoreMap() + ("createdAt" to FieldValue.serverTimestamp())
                docRef.set(data).addOnFailureListener { Log.w(TAG, "Add category write ${docRef.id} failed", it) }
                category.id
            }

        override suspend fun updateCategory(category: Category): Result<Unit> = withUid { uid -> write(uid, category) }

        override suspend fun setArchived(
            id: CategoryId,
            archived: Boolean,
        ): Result<Unit> =
            withUid { uid ->
                val current =
                    requireNotNull(observeCategories().first().find { it.id == id }) { "Unknown category ${id.value}" }
                write(uid, current.copy(archived = archived))
            }

        override suspend fun setGroup(
            id: CategoryId,
            groupId: String?,
        ): Result<Unit> =
            withUid { uid ->
                val current =
                    requireNotNull(observeCategories().first().find { it.id == id }) { "Unknown category ${id.value}" }
                write(uid, current.copy(groupId = groupId))
            }

        override suspend fun reorder(orderedIds: List<CategoryId>): Result<Unit> =
            withUid { uid ->
                val catalog = observeCategories().first().associateBy { it.id }
                val batch = firestore.batch()
                orderedIds.forEachIndexed { index, id ->
                    val category = requireNotNull(catalog[id]) { "Unknown category ${id.value}" }
                    batch.set(
                        collection(uid).document(id.value),
                        category.copy(order = index).toFirestoreMap(),
                        SetOptions.merge(),
                    )
                }
                batch.commit().addOnFailureListener { Log.w(TAG, "Reorder categories write failed", it) }
            }

        override suspend fun deleteCategory(id: CategoryId): Result<Unit> =
            withUid { uid ->
                require(!BuiltInCategories.isBuiltIn(id)) { "Built-in category ${id.value} can't be deleted" }
                val docRef = collection(uid).document(id.value)
                docRef.delete().addOnFailureListener { Log.w(TAG, "Delete category write ${docRef.id} failed", it) }
            }

        private fun write(
            uid: String,
            category: Category,
        ) {
            val docRef = collection(uid).document(category.id.value)
            docRef
                .set(category.toFirestoreMap(), SetOptions.merge())
                .addOnFailureListener { Log.w(TAG, "Category write ${docRef.id} failed", it) }
        }

        private inline fun <T> withUid(block: (String) -> T): Result<T> {
            val uid = firebaseAuth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable { block(uid) }
        }
    }
