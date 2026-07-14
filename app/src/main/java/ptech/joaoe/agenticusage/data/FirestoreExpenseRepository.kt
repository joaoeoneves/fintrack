package ptech.joaoe.agenticusage.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import ptech.joaoe.agenticusage.domain.model.Expense
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory
import ptech.joaoe.agenticusage.domain.repository.ExpenseRepository

class FirestoreExpenseRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : ExpenseRepository {

    private fun expensesCollection(uid: String) =
        firestore.collection("users").document(uid).collection("expenses")

    override fun observeExpenses(startInclusive: Instant, endExclusive: Instant): Flow<List<Expense>> {
        val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

        val query: Query = expensesCollection(uid)
            .whereGreaterThanOrEqualTo("date", Timestamp(startInclusive.epochSecond, startInclusive.nano))
            .whereLessThan("date", Timestamp(endExclusive.epochSecond, endExclusive.nano))

        return callbackFlow {
            val registration = query.addSnapshotListener { snapshot, error ->
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
        val uid = firebaseAuth.currentUser?.uid
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
        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val data = expense.toFirestoreMap(includeCreatedAt = false)
            expensesCollection(uid).document(expense.id).set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteExpense(id: String): Result<Unit> {
        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            expensesCollection(uid).document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun Expense.toFirestoreMap(includeCreatedAt: Boolean): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "name" to name,
            "amountCents" to amountCents,
            "category" to category.name,
            "date" to Timestamp(date.epochSecond, date.nano),
            "note" to note,
            "updatedAt" to FieldValue.serverTimestamp()
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
        val category = try {
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
            note = note
        )
    }
}
