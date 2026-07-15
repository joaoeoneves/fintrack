package com.joaoeoneves.fintrack.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject

class FirestoreIncomeRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : IncomeRepository {
        private fun incomeCollection(uid: String) = firestore.collection("users").document(uid).collection("income")

        companion object {
            private const val BATCH_CHUNK_SIZE = 400
        }

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

        override suspend fun addIncome(income: Income): Result<String> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val data = income.toFirestoreMap(includeCreatedAt = true)
                val reference = incomeCollection(uid).add(data).await()
                Result.success(reference.id)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        override suspend fun updateIncome(income: Income): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val data = income.toFirestoreMap(includeCreatedAt = false)
                incomeCollection(uid).document(income.id).set(data, SetOptions.merge()).await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        override suspend fun deleteIncome(id: String): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                incomeCollection(uid).document(id).delete().await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        override suspend fun getIncome(id: String): Income? {
            val uid = firebaseAuth.currentUser?.uid ?: return null
            return try {
                incomeCollection(uid)
                    .document(id)
                    .get()
                    .await()
                    .toIncomeOrNull()
            } catch (e: Exception) {
                null
            }
        }

        override suspend fun getAllIncome(): Result<List<Income>> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val snapshot = incomeCollection(uid).get().await()
                Result.success(snapshot.toIncomeList())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        override suspend fun addIncomeList(income: List<Income>): Result<Int> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                var total = 0
                income.chunked(BATCH_CHUNK_SIZE).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { item ->
                        val docRef = incomeCollection(uid).document()
                        batch.set(docRef, item.toFirestoreMap(includeCreatedAt = true))
                    }
                    batch.commit().await()
                    total += chunk.size
                }
                Result.success(total)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        private fun Income.toFirestoreMap(includeCreatedAt: Boolean): Map<String, Any?> {
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
            return map
        }

        private fun QuerySnapshot.toIncomeList(): List<Income> = documents.mapNotNull { it.toIncomeOrNull() }

        private fun DocumentSnapshot.toIncomeOrNull(): Income? {
            val source = getString("source") ?: return null
            val amountCents = getLong("amountCents") ?: return null
            val date = getTimestamp("date") ?: return null
            val note = getString("note")

            return Income(
                id = id,
                source = source,
                amountCents = amountCents,
                date = Instant.ofEpochSecond(date.seconds, date.nanoseconds.toLong()),
                note = note,
            )
        }
    }
