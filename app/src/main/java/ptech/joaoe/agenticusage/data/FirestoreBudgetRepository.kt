package ptech.joaoe.agenticusage.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import ptech.joaoe.agenticusage.domain.model.Budget
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory
import ptech.joaoe.agenticusage.domain.repository.BudgetRepository
import javax.inject.Inject

class FirestoreBudgetRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : BudgetRepository {
        private fun budgetsCollection(uid: String) = firestore.collection("users").document(uid).collection("budgets")

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
                Result.failure(e)
            }
        }

        private fun QuerySnapshot.toBudgets(): List<Budget> = documents.mapNotNull { it.toBudgetOrNull() }

        private fun DocumentSnapshot.toBudgetOrNull(): Budget? {
            val category = ExpenseCategory.entries.find { it.name == id }
            val limitCents = getLong("limitCents")
            return if (category != null && limitCents != null) {
                Budget(category = category, limitCents = limitCents)
            } else {
                null
            }
        }
    }
