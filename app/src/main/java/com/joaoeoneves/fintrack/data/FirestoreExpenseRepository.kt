package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.joaoeoneves.fintrack.data.migration.chunkedForRules
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.repository.BulkAddResult
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject

private const val TAG = "FirestoreExpenseRepo"
private const val REVIEW_BATCH_SIZE = 500

@Suppress("TooManyFunctions") // One override per ExpenseRepository operation.
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

        override fun observeHasAnyExpense(): Flow<Boolean> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(false)
            return callbackFlow {
                val registration =
                    expensesCollection(uid).limit(1).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(!snapshot.isEmpty)
                        }
                    }
                awaitClose { registration.remove() }
            }.distinctUntilChanged()
        }

        override fun observeNeedingReview(): Flow<List<Expense>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())
            val query = expensesCollection(uid).whereEqualTo("needsReview", true)
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

        override fun observeAwaitingRefund(): Flow<List<Expense>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())
            val query = expensesCollection(uid).whereEqualTo("awaitingRefund", true)
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

        override suspend fun getSplit(splitId: String): Result<List<Expense>> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable {
                val query = expensesCollection(uid).whereEqualTo("splitId", splitId)
                query.get().await().toExpenses()
            }
        }

        override suspend fun replaceSplit(
            update: List<Expense>,
            add: List<Expense>,
            deleteIds: Collection<String>,
        ): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable {
                val collection = expensesCollection(uid)
                val batch = firestore.batch()
                update.forEach { batch.update(collection.document(it.id), it.toFirestoreMap(includeCreatedAt = false)) }
                add.forEach {
                    val ref = if (it.id.isBlank()) collection.document() else collection.document(it.id)
                    batch.set(ref, it.toFirestoreMap(includeCreatedAt = true))
                }
                deleteIds.distinct().forEach { batch.delete(collection.document(it)) }
                // Not awaited: lands in the local cache at once and syncs later (see addExpense).
                batch.commit().addOnFailureListener(onFail(TAG, "Replace split failed"))
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
                        batch.update(expensesCollection(uid).document(id), changes)
                    }
                    // Not awaited: lands in the local cache at once and syncs later (see addExpense).
                    batch.commit().addOnFailureListener(onFail(TAG, "Mark expenses reviewed failed"))
                }
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
                docRef.set(data).addOnFailureListener(onFail(TAG, "Add expense write ${docRef.id} failed"))
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
                docRef.update(data).addOnFailureListener(onFail(TAG, "Update expense write ${docRef.id} failed"))
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
                docRef.delete().addOnFailureListener(onFail(TAG, "Delete expense write ${docRef.id} failed"))
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: see addExpense above.
                Result.failure(e)
            }
        }

        override suspend fun addExpensesKeepingIds(expenses: List<Expense>): Result<List<String>> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable {
                val collection = expensesCollection(uid)
                val withIds = expenses.map { if (it.id.isBlank()) it.copy(id = collection.document().id) else it }
                for (chunk in withIds.importChunks()) {
                    val batch = firestore.batch()
                    chunk.forEach { batch.set(collection.document(it.id), it.toFirestoreMap(includeCreatedAt = true)) }
                    // Not awaited: lands in the local cache at once and syncs later (see addExpense).
                    batch.commit().addOnFailureListener(onFail(TAG, "Bulk add expenses failed"))
                }
                withIds.map { it.id }
            }
        }

        override suspend fun updateExpenses(expenses: List<Expense>): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable {
                for (chunk in expenses.importChunks()) {
                    val batch = firestore.batch()
                    chunk.forEach { expense ->
                        val ref = expensesCollection(uid).document(expense.id)
                        batch.update(ref, expense.toFirestoreMap(includeCreatedAt = false))
                    }
                    // Not awaited: see addExpensesKeepingIds.
                    batch.commit().addOnFailureListener(onFail(TAG, "Bulk update expenses failed"))
                }
            }
        }

        override suspend fun deleteExpenses(ids: Collection<String>): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable {
                for (chunk in ids.distinct().chunked(REVIEW_BATCH_SIZE)) {
                    val batch = firestore.batch()
                    chunk.forEach { batch.delete(expensesCollection(uid).document(it)) }
                    // Not awaited: see addExpensesKeepingIds.
                    batch.commit().addOnFailureListener(onFail(TAG, "Bulk delete expenses failed"))
                }
            }
        }

        override fun observeLinkedRefunds(): Flow<List<Expense>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())
            val query = expensesCollection(uid).whereGreaterThan("refundOf", "")
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
            for (chunk in expenses.importChunks()) {
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

        override suspend fun hasExpenseWithCategory(categoryId: CategoryId): Result<Boolean> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            // Server-only on purpose: the local cache may hold only part of the collection, so a cache
            // miss can't prove the category is unused. Fails while offline.
            return runCatchingCancellable {
                !expensesCollection(uid)
                    .whereEqualTo("category", categoryId.value)
                    .limit(1)
                    .get(Source.SERVER)
                    .await()
                    .isEmpty
            }
        }

        override suspend fun expenseIdsBetween(
            firstId: String,
            lastId: String,
        ): Result<Set<String>> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            // Server-only on purpose (see hasExpenseWithCategory): a partial cache would report an existing
            // expense as missing. Fails while offline.
            return runCatchingCancellable {
                expensesCollection(uid)
                    .orderBy(FieldPath.documentId())
                    .startAt(firstId)
                    .endAt(lastId)
                    .get(Source.SERVER)
                    .await()
                    .documents
                    .map { it.id }
                    .toSet()
            }
        }

        override suspend fun countExpensesWithCategory(categoryId: CategoryId): Result<Int> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable {
                expensesCollection(uid)
                    .whereEqualTo("category", categoryId.value)
                    .count()
                    .get(AggregateSource.SERVER)
                    .await()
                    .count
                    .toInt()
            }
        }

        override suspend fun moveExpensesToCategory(
            source: CategoryId,
            target: CategoryId,
        ): Result<Int> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable {
                val docs =
                    expensesCollection(uid)
                        .whereEqualTo("category", source.value)
                        .get(Source.SERVER)
                        .await()
                        .documents
                // Awaited commits, one batch at a time: a failure leaves earlier batches applied, and
                // re-running the move picks up whatever is left. Every write references the target, so a
                // batch costs rules a single category lookup.
                for (chunk in docs.chunkedForRules { target }) {
                    val batch = firestore.batch()
                    chunk.forEach { doc ->
                        val changes = mapOf("category" to target.value, "updatedAt" to FieldValue.serverTimestamp())
                        batch.update(doc.reference, changes)
                    }
                    batch.commit().await()
                }
                docs.size
            }
        }
    }

/**
 * Splits these expenses into write batches of at most 400 expenses that reference at most 15
 * distinct non-built-in categories (see [chunkedForRules]).
 */
internal fun List<Expense>.importChunks(): List<List<Expense>> = chunkedForRules { it.category.id }

internal fun Expense.toFirestoreMap(includeCreatedAt: Boolean): Map<String, Any?> {
    val map =
        mutableMapOf<String, Any?>(
            "name" to name,
            "amountCents" to amountCents,
            "category" to category.id.value,
            "date" to Timestamp(date.epochSecond, date.nano),
            "note" to note,
            // Always written (including false) so an update that turns a refund back into a regular
            // expense clears the flag.
            "isRefund" to isRefund,
            "paidWithMealCard" to paidWithMealCard,
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
    if (awaitingRefund) {
        map["awaitingRefund"] = true
    } else if (!includeCreatedAt) {
        map["awaitingRefund"] = FieldValue.delete()
    }
    putOptional(map, "refundOf", refundOf, includeCreatedAt)
    putOptional(map, "splitId", splitId, includeCreatedAt)
    return map
}

/** Written when set; on an update (merge) a null value deletes the key explicitly. */
private fun putOptional(
    map: MutableMap<String, Any?>,
    key: String,
    value: String?,
    isCreate: Boolean,
) {
    if (value != null) {
        map[key] = value
    } else if (!isCreate) {
        map[key] = FieldValue.delete()
    }
}

internal fun QuerySnapshot.toExpenses(): List<Expense> = documents.mapNotNull { it.toExpenseOrNull() }

private fun DocumentSnapshot.toExpenseOrNull(): Expense? = mapOrNull("expense") { parseExpense() }

private fun DocumentSnapshot.parseExpense(): Expense? {
    val name = getString("name")
    val amountCents = getLong("amountCents")
    // An id matching no built-in (a user category, resolved later against the catalog, or a deleted
    // one) becomes a placeholder rather than dropping the expense; only a missing id is malformed.
    val category =
        getString("category")
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> BuiltInCategories.byId(CategoryId(raw)) ?: Category.unknown(CategoryId(raw)) }
    val date = getTimestamp("date")
    val note = getString("note")
    val isRefund = get("isRefund") as? Boolean ?: false
    val paidWithMealCard = get("paidWithMealCard") as? Boolean ?: false
    val needsReview = get("needsReview") as? Boolean ?: false
    val awaitingRefund = get("awaitingRefund") as? Boolean ?: false
    val refundOf = (get("refundOf") as? String)?.takeIf { it.isNotBlank() }
    val splitId = (get("splitId") as? String)?.takeIf { it.isNotBlank() }

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
                paidWithMealCard = paidWithMealCard,
                needsReview = needsReview,
                refundOf = refundOf,
                splitId = splitId,
                awaitingRefund = awaitingRefund,
            )
    }
}
