package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
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
            category: ExpenseCategory,
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
                budgetsCollection(uid).document(category.name).set(data, SetOptions.merge()).await()
                Result.success(Unit)
            } catch (e: Exception) {
                // Broad catch: Firestore Task failures aren't narrowly typed (network, permission,
                // and server errors all surface as generic/undocumented exception subtypes here);
                // convert any failure into a Result for the caller.
                Result.failure(e)
            }
        }

        private fun QuerySnapshot.toBudgets(): List<Budget> = documents.mapNotNull { it.toBudgetOrNull() }

        private fun DocumentSnapshot.toBudgetOrNull(): Budget? {
            val category = ExpenseCategory.entries.find { it.name == id }
            val limitCents = getLong("limitCents")
            return when {
                category == null -> {
                    Log.w(TAG, "Dropping malformed budget doc $id: missing or invalid 'category'")
                    null
                }
                limitCents == null -> {
                    Log.w(TAG, "Dropping malformed budget doc $id: missing or invalid 'limitCents'")
                    null
                }
                else -> Budget(category = category, limitCents = limitCents)
            }
        }
    }
