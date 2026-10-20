package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.repository.BulkAddResult
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject

private const val TAG = "FirestoreExpenseRepo"
private const val BATCH_CHUNK_SIZE = 400

class FirestoreExpenseRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : ExpenseRepository {
        private fun expensesCollection(uid: String) = firestore.collection("users").document(uid).collection("expenses")

        override fun observeExpenses(
            startInclusive: Instant,
            endExclusive: Instant,
        ): Flow<List<Expense>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

            val query: Query =
                expensesCollection(uid)
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
                            trySend(snapshot.toExpenses())
                        }
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun addExpense(expense: Expense): Result<String> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val data = expense.toFirestoreMap(includeCreatedAt = true)
                val collection = expensesCollection(uid)
                val docRef = if (expense.id.isBlank()) collection.document() else collection.document(expense.id)
                // Not awaited: the write lands in Firestore's local cache immediately and syncs when
                // the device is online. Awaiting would suspend until server acknowledgement, which
                // hangs the save indefinitely while offline.
                docRef.set(data).addOnFailureListener { Log.w(TAG, "Add expense write ${docRef.id} failed", it) }
                Result.success(docRef.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: Firestore can throw synchronously for invalid input/state with
                // undocumented exception subtypes; convert any failure into a Result for the caller.
                Result.failure(e)
            }
        }

        override suspend fun updateExpense(expense: Expense): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val data = expense.toFirestoreMap(includeCreatedAt = false)
                val docRef = expensesCollection(uid).document(expense.id)
                // Not awaited: see addExpense above.
                docRef.update(data).addOnFailureListener { Log.w(TAG, "Update expense write ${docRef.id} failed", it) }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: see addExpense above.
                Result.failure(e)
            }
        }

        override suspend fun deleteExpense(id: String): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val docRef = expensesCollection(uid).document(id)
                // Not awaited (see addExpense above); deleting a missing document is a no-op.
                docRef.delete().addOnFailureListener { Log.w(TAG, "Delete expense write ${docRef.id} failed", it) }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: see addExpense above.
                Result.failure(e)
            }
        }

        override suspend fun getExpense(id: String): Result<Expense?> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val expense =
                    expensesCollection(uid)
                        .document(id)
                        .get()
                        .await()
                        .toExpenseOrNull()
                Result.success(expense)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: see addExpense above.
                Result.failure(e)
            }
        }

        override suspend fun getAllExpenses(): Result<List<Expense>> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val snapshot = expensesCollection(uid).get().await()
                Result.success(snapshot.toExpenses())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: see addExpense above.
                Result.failure(e)
            }
        }

        override suspend fun addExpenses(expenses: List<Expense>): BulkAddResult {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return BulkAddResult(0, IllegalStateException("Not signed in"))
            var total = 0
            var failure: Throwable? = null
            for (chunk in expenses.chunked(BATCH_CHUNK_SIZE)) {
                try {
                    val batch = firestore.batch()
                    chunk.forEach { expense ->
                        val docRef = expensesCollection(uid).document()
                        batch.set(docRef, expense.toFirestoreMap(includeCreatedAt = true))
                    }
                    batch.commit().await()
                    total += chunk.size
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Broad catch: see addExpense above.
                    failure = e
                    break
                }
            }
            return BulkAddResult(total, failure)
        }
    }

private fun Expense.toFirestoreMap(includeCreatedAt: Boolean): Map<String, Any?> {
    val map =
        mutableMapOf<String, Any?>(
            "name" to name,
            "amountCents" to amountCents,
            "category" to category.name,
            "date" to Timestamp(date.epochSecond, date.nano),
            "note" to note,
            // Always written (including false) so an update that turns a refund back into a regular
            // expense clears the flag.
            "isRefund" to isRefund,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
    if (includeCreatedAt) {
        map["createdAt"] = FieldValue.serverTimestamp()
    }
    return map
}

private fun QuerySnapshot.toExpenses(): List<Expense> = documents.mapNotNull { it.toExpenseOrNull() }

private fun DocumentSnapshot.toExpenseOrNull(): Expense? {
    val name = getString("name")
    val amountCents = getLong("amountCents")
    val categoryName = getString("category")
    val category = ExpenseCategory.entries.find { it.name == categoryName }
    val date = getTimestamp("date")
    val note = getString("note")
    val isRefund = get("isRefund") as? Boolean ?: false

    return when {
        name == null -> {
            Log.w(TAG, "Dropping malformed expense doc $id: missing or invalid 'name'")
            null
        }
        amountCents == null -> {
            Log.w(TAG, "Dropping malformed expense doc $id: missing or invalid 'amountCents'")
            null
        }
        category == null -> {
            Log.w(TAG, "Dropping malformed expense doc $id: missing or invalid 'category'")
            null
        }
        date == null -> {
            Log.w(TAG, "Dropping malformed expense doc $id: missing or invalid 'date'")
            null
        }
        else ->
            Expense(
                id = id,
                name = name,
                amountCents = amountCents,
                category = category,
                date = Instant.ofEpochSecond(date.seconds, date.nanoseconds.toLong()),
                note = note,
                isRefund = isRefund,
            )
    }
}
