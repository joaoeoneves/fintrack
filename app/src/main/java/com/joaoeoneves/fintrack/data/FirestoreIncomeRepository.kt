package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.BulkAddResult
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject

private const val TAG = "FirestoreIncomeRepo"
private const val BATCH_CHUNK_SIZE = 400
private const val REVIEW_BATCH_SIZE = 500

class FirestoreIncomeRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : IncomeRepository {
        private fun incomeCollection(uid: String) = firestore.collection("users").document(uid).collection("income")

        override fun observeIncome(
            startInclusive: Instant,
            endExclusive: Instant,
        ): Flow<List<Income>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

            val query: Query =
                incomeCollection(uid)
                    .whereGreaterThanOrEqualTo("date", Timestamp(startInclusive.epochSecond, startInclusive.nano))
                    .whereLessThan("date", Timestamp(endExclusive.epochSecond, endExclusive.nano))

            return callbackFlow {
                val registration =
                    query.addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(snapshot.toIncomeList())
                        }
                    }
                awaitClose { registration.remove() }
            }
        }

        override fun observeNeedingReview(): Flow<List<Income>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())
            val query = incomeCollection(uid).whereEqualTo("needsReview", true)
            return callbackFlow {
                val registration =
                    query.addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(snapshot.toIncomeList())
                        }
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun markReviewed(ids: Collection<String>): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable {
                for (chunk in ids.distinct().chunked(REVIEW_BATCH_SIZE)) {
                    val batch = firestore.batch()
                    chunk.forEach { id ->
                        val changes =
                            mapOf("needsReview" to FieldValue.delete(), "updatedAt" to FieldValue.serverTimestamp())
                        batch.update(incomeCollection(uid).document(id), changes)
                    }
                    // Not awaited: lands in the local cache at once and syncs later (see addIncome).
                    batch.commit().addOnFailureListener(onFail(TAG, "Mark income reviewed failed"))
                }
            }
        }

        override suspend fun addIncome(income: Income): Result<String> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val data = income.toFirestoreMap(includeCreatedAt = true)
                val collection = incomeCollection(uid)
                val docRef = if (income.id.isBlank()) collection.document() else collection.document(income.id)
                // Not awaited: the write lands in Firestore's local cache immediately and syncs when
                // the device is online. Awaiting would suspend until server acknowledgement, which
                // hangs the save indefinitely while offline.
                docRef.set(data).addOnFailureListener(onFail(TAG, "Add income write ${docRef.id} failed"))
                Result.success(docRef.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: Firestore can throw synchronously for invalid input/state with
                // undocumented exception subtypes; convert any failure into a Result for the caller.
                Result.failure(e)
            }
        }

        override suspend fun updateIncome(income: Income): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val data = income.toFirestoreMap(includeCreatedAt = false)
                val docRef = incomeCollection(uid).document(income.id)
                // Not awaited: see addIncome above.
                docRef.update(data).addOnFailureListener(onFail(TAG, "Update income write ${docRef.id} failed"))
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: see addIncome above.
                Result.failure(e)
            }
        }

        override suspend fun deleteIncome(id: String): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val docRef = incomeCollection(uid).document(id)
                // Not awaited (see addIncome above); deleting a missing document is a no-op.
                docRef.delete().addOnFailureListener(onFail(TAG, "Delete income write ${docRef.id} failed"))
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: see addIncome above.
                Result.failure(e)
            }
        }

        override suspend fun getIncome(id: String): Result<Income?> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val income =
                    incomeCollection(uid)
                        .document(id)
                        .get()
                        .await()
                        .toIncomeOrNull()
                Result.success(income)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: see addIncome above.
                Result.failure(e)
            }
        }

        override suspend fun getAllIncome(): Result<List<Income>> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val snapshot = incomeCollection(uid).get().await()
                Result.success(snapshot.toIncomeList())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: see addIncome above.
                Result.failure(e)
            }
        }

        override suspend fun addIncomeList(income: List<Income>): BulkAddResult {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return BulkAddResult(0, IllegalStateException("Not signed in"))
            var total = 0
            var failure: Throwable? = null
            for (chunk in income.chunked(BATCH_CHUNK_SIZE)) {
                try {
                    val batch = firestore.batch()
                    chunk.forEach { item ->
                        val docRef = incomeCollection(uid).document()
                        batch.set(docRef, item.toFirestoreMap(includeCreatedAt = true))
                    }
                    batch.commit().await()
                    total += chunk.size
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Broad catch: see addIncome above.
                    failure = e
                    break
                }
            }
            return BulkAddResult(total, failure)
        }
    }

internal fun Income.toFirestoreMap(includeCreatedAt: Boolean): Map<String, Any?> {
    val map =
        mutableMapOf<String, Any?>(
            "source" to source,
            "amountCents" to amountCents,
            "date" to Timestamp(date.epochSecond, date.nano),
            "note" to note,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
    if (includeCreatedAt) {
        map["createdAt"] = FieldValue.serverTimestamp()
    }
    // Written only when true. Updates merge, so a false flag must delete the key explicitly.
    if (needsReview) {
        map["needsReview"] = true
    } else if (!includeCreatedAt) {
        map["needsReview"] = FieldValue.delete()
    }
    return map
}

internal fun QuerySnapshot.toIncomeList(): List<Income> = documents.mapNotNull { it.toIncomeOrNull() }

private fun DocumentSnapshot.toIncomeOrNull(): Income? = mapOrNull("income") { parseIncome() }

private fun DocumentSnapshot.parseIncome(): Income? {
    val source = getString("source")
    val amountCents = getLong("amountCents")
    val date = getTimestamp("date")
    val note = getString("note")
    val needsReview = get("needsReview") as? Boolean ?: false

    return when {
        source == null -> {
            Log.w(TAG, "Dropping malformed income doc $id: missing or invalid 'source'")
            null
        }
        amountCents == null -> {
            Log.w(TAG, "Dropping malformed income doc $id: missing or invalid 'amountCents'")
            null
        }
        date == null -> {
            Log.w(TAG, "Dropping malformed income doc $id: missing or invalid 'date'")
            null
        }
        else ->
            Income(
                id = id,
                source = source,
                amountCents = amountCents,
                date = Instant.ofEpochSecond(date.seconds, date.nanoseconds.toLong()),
                note = note,
                needsReview = needsReview,
            )
    }
}
