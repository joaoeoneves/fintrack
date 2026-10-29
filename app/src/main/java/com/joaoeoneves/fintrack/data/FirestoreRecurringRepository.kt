@file:Suppress("TooManyFunctions") // One helper per Firestore mapping step.

package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.joaoeoneves.fintrack.data.migration.chunkedForRules
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.DateTimeException
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "FirestoreRecurringRepo"

@Suppress("TooManyFunctions") // One member per RecurringRepository operation.
class FirestoreRecurringRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : RecurringRepository {
        private fun userDoc(uid: String) = firestore.collection("users").document(uid)

        private fun collection(uid: String) = userDoc(uid).collection("recurring")

        override fun observeRecurring(): Flow<List<RecurringItem>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

            return callbackFlow {
                val registration =
                    collection(uid).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(snapshot.toRecurringList())
                        }
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun saveRecurring(item: RecurringItem): Result<String> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                val col = collection(uid)
                val docRef = if (item.id.isBlank()) col.document() else col.document(item.id)
                // Not awaited: the write lands in Firestore's local cache immediately and syncs when
                // the device is online. Awaiting would hang indefinitely while offline.
                docRef
                    .set(item.toFirestoreMap())
                    .addOnFailureListener(onFail(TAG, "Save recurring write ${docRef.id} failed"))
                docRef.id
            }
        }

        override suspend fun deleteRecurring(id: String): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                // Not awaited (see saveRecurring); deleting a missing document is a no-op.
                collection(uid)
                    .document(id)
                    .delete()
                    .addOnFailureListener(onFail(TAG, "Delete recurring failed: $id"))
                Unit
            }
        }

        override suspend fun moveRecurringCategory(
            source: CategoryId,
            target: CategoryId,
        ): Result<Int> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                val docs =
                    collection(uid)
                        .whereEqualTo("category", source.value)
                        .get(Source.SERVER)
                        .await()
                        .documents
                // Awaited, one batch at a time (see FirestoreExpenseRepository.moveExpensesToCategory).
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

        override suspend fun setHandledThrough(
            id: String,
            date: LocalDate?,
        ): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                // Not awaited (see saveRecurring).
                collection(uid)
                    .document(id)
                    .update(handledThroughUpdate(date))
                    .addOnFailureListener(onFail(TAG, "Set handledThrough failed: $id"))
                Unit
            }
        }

        // The FieldValue array API is vararg-only (the arrays hold a handful of dates), hence the spread.
        @Suppress("SpreadOperator")
        override suspend fun skipOccurrences(
            itemId: String,
            dates: Set<LocalDate>,
            advanceTo: LocalDate?,
        ): Result<Unit> = updateSkipped(itemId, FieldValue.arrayUnion(*dates.isoStrings()), advanceTo)

        @Suppress("SpreadOperator")
        override suspend fun unskipOccurrences(
            itemId: String,
            dates: Set<LocalDate>,
        ): Result<Unit> = updateSkipped(itemId, FieldValue.arrayRemove(*dates.isoStrings()), null)

        private fun updateSkipped(
            itemId: String,
            skipped: FieldValue,
            advanceTo: LocalDate?,
        ): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                val changes =
                    buildMap<String, Any?> {
                        put("skipped", skipped)
                        if (advanceTo != null) put("handledThrough", advanceTo.toString())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                // Not awaited (see saveRecurring); fails (in the log only) when the item is gone.
                collection(uid)
                    .document(itemId)
                    .update(changes)
                    .addOnFailureListener(onFail(TAG, "Update skipped occurrences failed: $itemId"))
                Unit
            }
        }

        override suspend fun confirmOccurrence(
            itemId: String,
            occurrence: LocalDate,
            transaction: Transaction,
        ): Result<String> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                val user = userDoc(uid)
                val batch = firestore.batch()
                val (collectionName, data) =
                    when (transaction) {
                        is Expense -> "expenses" to transaction.toFirestoreMap(includeCreatedAt = true)
                        is Income -> "income" to transaction.toFirestoreMap(includeCreatedAt = true)
                    }
                batch.set(user.collection(collectionName).document(transaction.id), data)
                batch.update(collection(uid).document(itemId), handledThroughUpdate(occurrence))
                // Not awaited (see saveRecurring): the batch is applied to the local cache at once.
                batch
                    .commit()
                    .addOnFailureListener(onFail(TAG, "Confirm occurrence $itemId/$occurrence failed"))
                transaction.id
            }
        }

        private fun <T> notSignedIn(): Result<T> = Result.failure(IllegalStateException("Not signed in"))
    }

private fun Set<LocalDate>.isoStrings(): Array<Any> = sorted().map { it.toString() }.toTypedArray()

private fun handledThroughUpdate(date: LocalDate?): Map<String, Any?> =
    mapOf("handledThrough" to date?.toString(), "updatedAt" to FieldValue.serverTimestamp())

/** Written whole (no merge); the id is the document id. Shared with the guest-data migration. */
internal fun RecurringItem.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        "type" to type.name,
        "name" to name,
        "amountCents" to amountCents,
        "category" to categoryId?.value,
        "note" to note,
        "frequency" to frequency.name,
        "dayOfMonth" to dayOfMonth,
        "monthOfYear" to monthOfYear,
        "startDate" to startDate.toString(),
        "endDate" to endDate?.toString(),
        "handledThrough" to handledThrough?.toString(),
        "active" to active,
        "autoPost" to autoPost,
        "skipped" to skipped.sorted().map { it.toString() },
        "pausedAt" to pausedAt?.toString(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

internal fun QuerySnapshot.toRecurringList(): List<RecurringItem> =
    documents.mapNotNull { it.toRecurringOrNull() }.sortedWith(compareBy({ it.startDate }, { it.id }))

/** Maps a document, or drops it (logged) when a required field is missing or invalid. */
internal fun DocumentSnapshot.toRecurringOrNull(): RecurringItem? = mapOrNull("recurring") { parseRecurring() }

private fun DocumentSnapshot.parseRecurring(): RecurringItem? {
    val type = getString("type")?.let { raw -> RecurringType.entries.find { it.name == raw } }
    val frequency = getString("frequency")?.let { raw -> RecurringFrequency.entries.find { it.name == raw } }
    val name = getString("name")
    val amountCents = getLong("amountCents")
    val dayOfMonth = getLong("dayOfMonth")
    val startDate = parseDate("startDate")
    val active = get("active") as? Boolean

    val invalidField =
        listOf(
            "type" to (type == null),
            "frequency" to (frequency == null),
            "name" to (name == null),
            "amountCents" to (amountCents == null),
            "dayOfMonth" to (dayOfMonth == null),
            "startDate" to (startDate == null),
            "active" to (active == null),
            "endDate" to isUnparsableDate("endDate"),
            "handledThrough" to isUnparsableDate("handledThrough"),
        ).firstOrNull { it.second }?.first
    if (invalidField != null) return dropped(invalidField)

    val item =
        RecurringItem(
            id = id,
            type = checkNotNull(type),
            name = checkNotNull(name),
            amountCents = checkNotNull(amountCents),
            categoryId = getString("category")?.takeIf { it.isNotBlank() }?.let(::CategoryId),
            note = getString("note"),
            frequency = checkNotNull(frequency),
            dayOfMonth = checkNotNull(dayOfMonth).toIntClamped(),
            monthOfYear = getLong("monthOfYear")?.toIntClamped(),
            startDate = checkNotNull(startDate),
            endDate = parseDate("endDate"),
            handledThrough = parseDate("handledThrough"),
            active = checkNotNull(active),
            autoPost = get("autoPost") as? Boolean ?: false,
            skipped = parseSkipped(),
            pausedAt = parseDate("pausedAt"),
        )
    val consistent = item.hasValidSchedule && item.type == RecurringType.EXPENSE && item.categoryId != null
    return if (consistent) item else dropped("type/schedule/category")
}

private fun DocumentSnapshot.dropped(field: String): RecurringItem? {
    Log.w(TAG, "Dropping malformed recurring doc $id: missing or invalid '$field'")
    return null
}

private fun DocumentSnapshot.parseDate(field: String): LocalDate? =
    try {
        getString(field)?.let(LocalDate::parse)
    } catch (e: DateTimeException) {
        Log.w(TAG, "Recurring doc $id: unparsable '$field'", e)
        null
    }

/** The skipped dates; an unparsable entry is ignored (logged), not a reason to drop the item. */
private fun DocumentSnapshot.parseSkipped(): Set<LocalDate> =
    (get("skipped") as? List<*>)
        .orEmpty()
        .mapNotNull { raw ->
            (raw as? String)?.let {
                try {
                    LocalDate.parse(it)
                } catch (e: DateTimeException) {
                    Log.w(TAG, "Recurring doc $id: unparsable skipped entry '$it'", e)
                    null
                }
            }
        }.toSet()

/** A present (non-null) date string that does not parse. */
private fun DocumentSnapshot.isUnparsableDate(field: String): Boolean {
    val raw = getString(field)
    return raw != null && parseDate(field) == null
}

private fun Long.toIntClamped(): Int = coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
