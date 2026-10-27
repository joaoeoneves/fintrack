package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class FirestoreBudgetRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : BudgetRepository {
        private fun budgetsCollection(uid: String) = firestore.collection("users").document(uid).collection("budgets")

        companion object {
            private const val TAG = "FirestoreBudgetRepo"
        }

        override fun observeBudgets(): Flow<List<Budget>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

            return callbackFlow {
                val registration =
                    budgetsCollection(uid).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(snapshot.toBudgets())
                        }
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun setBudget(
            categoryId: CategoryId,
            limitCents: Long,
        ): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val data =
                    mapOf(
                        "limitCents" to limitCents,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                val docRef = budgetsCollection(uid).document(categoryId.value)
                // Not awaited: the write lands in Firestore's local cache immediately and syncs when
                // the device is online. Awaiting would hang indefinitely while offline.
                docRef
                    .set(data, SetOptions.merge())
                    .addOnFailureListener { Log.w(TAG, "Set budget write ${docRef.id} failed", it) }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: Firestore Task failures aren't narrowly typed (network, permission,
                // and server errors all surface as generic/undocumented exception subtypes here);
                // convert any failure into a Result for the caller.
                Result.failure(e)
            }
        }

        override suspend fun clearBudget(categoryId: CategoryId): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val docRef = budgetsCollection(uid).document(categoryId.value)
                // Not awaited (see setBudget above); deleting a missing document is a no-op.
                docRef.delete().addOnFailureListener { Log.w(TAG, "Clear budget write ${docRef.id} failed", it) }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: see setBudget above.
                Result.failure(e)
            }
        }

        private fun QuerySnapshot.toBudgets(): List<Budget> = documents.mapNotNull { it.toBudgetOrNull() }

        // The doc id is the category id. Any id is kept (even one matching no known category), so a
        // budget left behind by a deleted category still shows up and can be cleared.
        private fun DocumentSnapshot.toBudgetOrNull(): Budget? = mapOrNull("budget") { parseBudget() }

        private fun DocumentSnapshot.parseBudget(): Budget? {
            val limitCents = getLong("limitCents")
            if (limitCents == null) {
                Log.w(TAG, "Dropping malformed budget doc $id: missing or invalid 'limitCents'")
            }
            return limitCents?.let { Budget(categoryId = CategoryId(id), limitCents = it) }
        }
    }
