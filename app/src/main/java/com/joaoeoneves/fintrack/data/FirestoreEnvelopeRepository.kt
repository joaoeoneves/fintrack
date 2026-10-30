package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import com.joaoeoneves.fintrack.domain.repository.EnvelopeRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

private const val TAG = "FirestoreEnvelopeRepo"

/**
 * Envelopes stored under `users/{uid}/categoryGroups` (the collection keeps its pre-envelope name). Writes are not
 * awaited (they land in the local cache immediately and sync when online). An envelope is saved with its
 * categories' `groupId` in the same batch.
 */
class FirestoreEnvelopeRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
        private val categoryRepository: FirestoreCategoryRepository,
    ) : EnvelopeRepository {
        private fun userDoc(uid: String) = firestore.collection("users").document(uid)

        private fun collection(uid: String) = userDoc(uid).collection("categoryGroups")

        override fun observeEnvelopes(): Flow<List<Envelope>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

            return callbackFlow {
                val registration =
                    collection(uid).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(snapshot.toEnvelopeList())
                        }
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun saveEnvelope(edit: EnvelopeEdit): Result<String> =
            withUid { uid ->
                val envelopes = observeEnvelopes().first()
                val docRef = edit.id?.let { collection(uid).document(it) } ?: collection(uid).document()
                val order =
                    envelopes.find { it.id == docRef.id }?.order ?: ((envelopes.maxOfOrNull { it.order } ?: -1) + 1)
                val batch = firestore.batch()
                // The pre-5.5 fixed amount, funding line and amount history are removed from the document.
                batch.set(
                    docRef,
                    mapOf(
                        "name" to edit.name,
                        "order" to order,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ) + LEGACY_ENVELOPE_FIELDS.associateWith { FieldValue.delete() },
                    SetOptions.merge(),
                )
                fileCategories(batch, uid, docRef.id, edit)
                batch.commit().addOnFailureListener(onFail(TAG, "Save envelope ${docRef.id} write failed"))
                docRef.id
            }

        private suspend fun fileCategories(
            batch: WriteBatch,
            uid: String,
            envelopeId: String,
            edit: EnvelopeEdit,
        ) {
            categoryRepository
                .observeCategories()
                .first()
                .forEach { category ->
                    val member = category.id in edit.categoryIds
                    if (member != (category.groupId == envelopeId)) {
                        batch.set(
                            userDoc(uid).collection("categories").document(category.id.value),
                            category.copy(groupId = envelopeId.takeIf { member }).toFirestoreMap(),
                            SetOptions.merge(),
                        )
                    }
                }
        }

        override suspend fun reorderEnvelopes(orderedIds: List<String>): Result<Unit> =
            withUid { uid ->
                val batch = firestore.batch()
                orderedIds.forEachIndexed { index, id ->
                    batch.set(
                        collection(uid).document(id),
                        mapOf("order" to index, "updatedAt" to FieldValue.serverTimestamp()),
                        SetOptions.merge(),
                    )
                }
                batch.commit().addOnFailureListener(onFail(TAG, "Reorder envelopes write failed"))
                Unit
            }

        override suspend fun deleteEnvelope(id: String): Result<Unit> =
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
                batch.commit().addOnFailureListener(onFail(TAG, "Delete envelope $id write failed"))
                Unit
            }

        private inline fun <T> withUid(block: (String) -> T): Result<T> {
            val uid = firebaseAuth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable { block(uid) }
        }
    }

/** Envelope fields written before 5.5: tolerated by the rules, ignored on read, deleted on save. */
private val LEGACY_ENVELOPE_FIELDS = listOf("amountCents", "fundedBy", "startMonth", "versions")

/** Written whole (no merge); the id is the document id. */
internal fun Envelope.toFirestoreMap(): Map<String, Any?> =
    mapOf("name" to name, "order" to order, "updatedAt" to FieldValue.serverTimestamp())

internal fun QuerySnapshot.toEnvelopeList(): List<Envelope> =
    documents.mapNotNull { it.toEnvelopeOrNull() }.sortedWith(compareBy({ it.order }, { it.id }))

/** Lenient: the legacy amount, funding and timing fields are ignored. */
internal fun DocumentSnapshot.toEnvelopeOrNull(): Envelope? =
    mapOrNull("envelope") {
        val name = getString("name")?.takeIf { it.isNotBlank() }
        val order = getLong("order")
        if (name == null || order == null) {
            Log.w(TAG, "Dropping malformed envelope doc $id")
            null
        } else {
            val clamped = order.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
            Envelope(id = id, name = name, order = clamped)
        }
    }
