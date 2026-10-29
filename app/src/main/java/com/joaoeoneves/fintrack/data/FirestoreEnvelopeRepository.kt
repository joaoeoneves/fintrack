package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
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
 * categories' `groupId` and budgets in the same batch.
 */
class FirestoreEnvelopeRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
        private val categoryRepository: FirestoreCategoryRepository,
        private val budgetRepository: FirestoreBudgetRepository,
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
                            trySend(
                                snapshot.documents
                                    .mapNotNull { it.toEnvelopeOrNull() }
                                    .sortedWith(compareBy({ it.order }, { it.id })),
                            )
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
                val template = Envelope(docRef.id, edit.name, order, edit.amountCents, edit.fundedByLineId)
                val saved = envelopes.find { it.id == docRef.id }.savedAs(template, edit.amountCents, edit.effectiveFrom)
                batch.set(
                    docRef,
                    mapOf(
                        "name" to edit.name,
                        "order" to order,
                        "amountCents" to (saved.amountCents ?: FieldValue.delete()),
                        "fundedBy" to (edit.fundedByLineId ?: FieldValue.delete()),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ) + planTimingFields(saved.startMonth, saved.versions),
                    SetOptions.merge(),
                )
                edit.fundedByLineId?.let { line ->
                    envelopes.filter { it.id != docRef.id && it.fundedByLineId == line }.forEach {
                        batch.set(
                            collection(uid).document(it.id),
                            mapOf("fundedBy" to FieldValue.delete(), "updatedAt" to FieldValue.serverTimestamp()),
                            SetOptions.merge(),
                        )
                    }
                }
                fileCategories(batch, uid, docRef.id, edit)
                writeLimits(batch, uid, edit, budgetRepository.observeBudgets().first())
                batch.commit().addOnFailureListener { Log.w(TAG, "Save envelope ${docRef.id} write failed", it) }
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

        // The limits to set or clear; a fixed amount then clears every member's budget.
        private fun writeLimits(
            batch: WriteBatch,
            uid: String,
            edit: EnvelopeEdit,
            existing: List<Budget>,
        ) {
            val budgets = userDoc(uid).collection("budgets")
            edit.limits.forEach { (categoryId, budget) ->
                if (budget == null) {
                    batch.delete(budgets.document(categoryId.value))
                } else {
                    val saved =
                        existing
                            .find { it.categoryId == categoryId }
                            .savedAs(categoryId, budget.limitCents, budget.skipMonths, edit.effectiveFrom)
                    batch.set(
                        budgets.document(categoryId.value),
                        mapOf(
                            "limitCents" to saved.limitCents,
                            "skipMonths" to budget.skipMonths.sorted(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ) + planTimingFields(saved.startMonth, saved.versions),
                        SetOptions.merge(),
                    )
                }
            }
            if (edit.amountCents != null) edit.categoryIds.forEach { batch.delete(budgets.document(it.value)) }
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
                batch.commit().addOnFailureListener { Log.w(TAG, "Reorder envelopes write failed", it) }
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
                        batch.delete(userDoc(uid).collection("budgets").document(category.id.value))
                    }
                batch.delete(collection(uid).document(id))
                batch.commit().addOnFailureListener { Log.w(TAG, "Delete envelope $id write failed", it) }
                Unit
            }

        private inline fun <T> withUid(block: (String) -> T): Result<T> {
            val uid = firebaseAuth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable { block(uid) }
        }
    }

/** Written whole (no merge); the id is the document id, an absent amount or funding is omitted. */
internal fun Envelope.toFirestoreMap(): Map<String, Any?> =
    buildMap {
        put("name", name)
        put("order", order)
        amountCents?.let { put("amountCents", it) }
        fundedByLineId?.let { put("fundedBy", it) }
        putAll(planTimingFields(startMonth, versions))
        put("updatedAt", FieldValue.serverTimestamp())
    }

/** Lenient: an amount outside 1..[MAX_AMOUNT_CENTS] and a blank funding line count as absent. */
internal fun DocumentSnapshot.toEnvelopeOrNull(): Envelope? =
    mapOrNull("envelope") {
        val name = getString("name")?.takeIf { it.isNotBlank() }
        val order = getLong("order")
        if (name == null || order == null) {
            Log.w(TAG, "Dropping malformed envelope doc $id")
            null
        } else {
            val clamped = order.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
            Envelope(
                id = id,
                name = name,
                order = clamped,
                amountCents = getLong("amountCents")?.takeIf { it in 1..MAX_AMOUNT_CENTS },
                fundedByLineId = getString("fundedBy")?.takeIf { it.isNotBlank() },
                startMonth = startMonthOrNull(),
                versions = versionsOrEmpty(),
            )
        }
    }
