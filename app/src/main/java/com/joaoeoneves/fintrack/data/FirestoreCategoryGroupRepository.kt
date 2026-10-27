package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.joaoeoneves.fintrack.domain.model.CategoryGroup
import com.joaoeoneves.fintrack.domain.repository.CategoryGroupRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

private const val TAG = "FirestoreCategoryGroupRepo"

/**
 * Category groups stored under `users/{uid}/categoryGroups`. Writes are not awaited (they land in the local
 * cache immediately and sync when online). Deleting a group clears the `groupId` of its categories in the same
 * batch.
 */
class FirestoreCategoryGroupRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
        private val categoryRepository: FirestoreCategoryRepository,
    ) : CategoryGroupRepository {
        private fun userDoc(uid: String) = firestore.collection("users").document(uid)

        private fun collection(uid: String) = userDoc(uid).collection("categoryGroups")

        override fun observeGroups(): Flow<List<CategoryGroup>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

            return callbackFlow {
                val registration =
                    collection(uid).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(
                                snapshot.documents
                                    .mapNotNull { it.toCategoryGroupOrNull() }
                                    .sortedWith(compareBy({ it.order }, { it.id })),
                            )
                        }
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun addGroup(name: String): Result<String> =
            withUid { uid ->
                val docRef = collection(uid).document()
                val nextOrder = (observeGroups().first().maxOfOrNull { it.order } ?: -1) + 1
                docRef
                    .set(CategoryGroup(docRef.id, name, nextOrder).toFirestoreMap())
                    .addOnFailureListener { Log.w(TAG, "Add group write ${docRef.id} failed", it) }
                docRef.id
            }

        override suspend fun renameGroup(
            id: String,
            name: String,
        ): Result<Unit> =
            withUid { uid ->
                val docRef = collection(uid).document(id)
                docRef
                    .set(mapOf("name" to name, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                    .addOnFailureListener { Log.w(TAG, "Rename group write ${docRef.id} failed", it) }
                Unit
            }

        override suspend fun reorderGroups(orderedIds: List<String>): Result<Unit> =
            withUid { uid ->
                val batch = firestore.batch()
                orderedIds.forEachIndexed { index, id ->
                    batch.set(
                        collection(uid).document(id),
                        mapOf("order" to index, "updatedAt" to FieldValue.serverTimestamp()),
                        SetOptions.merge(),
                    )
                }
                batch.commit().addOnFailureListener { Log.w(TAG, "Reorder groups write failed", it) }
                Unit
            }

        override suspend fun deleteGroup(id: String): Result<Unit> =
            withUid { uid ->
                val batch = firestore.batch()
                categoryRepository
                    .observeCategories()
                    .first()
                    .filter { it.groupId == id }
                    .forEach { category ->
                        batch.set(
                            userDoc(uid).collection("categories").document(category.id.value),
                            category.copy(groupId = null).toFirestoreMap(),
                            SetOptions.merge(),
                        )
                    }
                batch.delete(collection(uid).document(id))
                batch.commit().addOnFailureListener { Log.w(TAG, "Delete group $id write failed", it) }
                Unit
            }

        private inline fun <T> withUid(block: (String) -> T): Result<T> {
            val uid = firebaseAuth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable { block(uid) }
        }
    }

/** Written whole (no merge); the id is the document id. Shared with the guest-data migration. */
internal fun CategoryGroup.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        "name" to name,
        "order" to order,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

internal fun DocumentSnapshot.toCategoryGroupOrNull(): CategoryGroup? =
    mapOrNull("categoryGroup") {
        val name = getString("name")?.takeIf { it.isNotBlank() }
        val order = getLong("order")
        if (name == null || order == null) {
            Log.w(TAG, "Dropping malformed category group doc $id")
            null
        } else {
            val clamped = order.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
            CategoryGroup(id = id, name = name, order = clamped)
        }
    }
