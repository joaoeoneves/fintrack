package com.joaoeoneves.fintrack.data

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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject

class FirestoreExpenseRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : ExpenseRepository {
        private fun expensesCollection(uid: String) = firestore.collection("users").document(uid).collection("expenses")

        companion object {
            private const val BATCH_CHUNK_SIZE = 400
        }

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
                val reference = expensesCollection(uid).add(data).await()
                Result.success(reference.id)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        override suspend fun updateExpense(expense: Expense): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val data = expense.toFirestoreMap(includeCreatedAt = false)
                expensesCollection(uid).document(expense.id).update(data).await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        override suspend fun deleteExpense(id: String): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val docRef = expensesCollection(uid).document(id)
                firestore
                    .runTransaction<Unit> { transaction ->
                        val snapshot = transaction.get(docRef)
                        if (!snapshot.exists()) {
                            throw NoSuchElementException("Expense with id $id not found")
                        }
                        transaction.delete(docRef)
                        Unit
                    }.await()
                Result.success(Unit)
            } catch (e: Exception) {
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
            } catch (e: Exception) {
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
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        override suspend fun addExpenses(expenses: List<Expense>): BulkAddResult {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return BulkAddResult(0, IllegalStateException("Not signed in"))
            var total = 0
            expenses.chunked(BATCH_CHUNK_SIZE).forEach { chunk ->
                try {
                    val batch = firestore.batch()
                    chunk.forEach { expense ->
                        val docRef = expensesCollection(uid).document()
                        batch.set(docRef, expense.toFirestoreMap(includeCreatedAt = true))
                    }
                    batch.commit().await()
                    total += chunk.size
                } catch (e: Exception) {
                    return BulkAddResult(total, e)
                }
            }
            return BulkAddResult(total, null)
        }

        private fun Expense.toFirestoreMap(includeCreatedAt: Boolean): Map<String, Any?> {
            val map =
                mutableMapOf<String, Any?>(
                    "name" to name,
                    "amountCents" to amountCents,
                    "category" to category.name,
                    "date" to Timestamp(date.epochSecond, date.nano),
                    "note" to note,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            if (includeCreatedAt) {
                map["createdAt"] = FieldValue.serverTimestamp()
            }
            return map
        }

        private fun QuerySnapshot.toExpenses(): List<Expense> = documents.mapNotNull { it.toExpenseOrNull() }

        private fun DocumentSnapshot.toExpenseOrNull(): Expense? {
            val name = getString("name") ?: return null
            val amountCents = getLong("amountCents") ?: return null
            val categoryName = getString("category") ?: return null
            val category =
                try {
                    ExpenseCategory.valueOf(categoryName)
                } catch (e: IllegalArgumentException) {
                    return null
                }
            val date = getTimestamp("date") ?: return null
            val note = getString("note")

            return Expense(
                id = id,
                name = name,
                amountCents = amountCents,
                category = category,
                date = Instant.ofEpochSecond(date.seconds, date.nanoseconds.toLong()),
                note = note,
            )
        }
    }
