package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
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
import java.time.DateTimeException
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "FirestoreRecurringRepo"

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
                    .addOnFailureListener { Log.w(TAG, "Save recurring write ${docRef.id} failed", it) }
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
                    .addOnFailureListener { Log.w(TAG, "Delete recurring failed: $id", it) }
                Unit
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
                    .addOnFailureListener { Log.w(TAG, "Set handledThrough failed: $id", it) }
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
                    .addOnFailureListener { Log.w(TAG, "Confirm occurrence $itemId/$occurrence failed", it) }
                transaction.id
            }
        }

        private fun <T> notSignedIn(): Result<T> = Result.failure(IllegalStateException("Not signed in"))
    }

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
        "updatedAt" to FieldValue.serverTimestamp(),
    )

private fun QuerySnapshot.toRecurringList(): List<RecurringItem> =
    documents.mapNotNull { it.toRecurringOrNull() }.sortedWith(compareBy({ it.startDate }, { it.id }))

/** Maps a document, or drops it (logged) when a required field is missing or invalid. */
internal fun DocumentSnapshot.toRecurringOrNull(): RecurringItem? {
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
            dayOfMonth = checkNotNull(dayOfMonth).toInt(),
            monthOfYear = getLong("monthOfYear")?.toInt(),
            startDate = checkNotNull(startDate),
            endDate = parseDate("endDate"),
            handledThrough = parseDate("handledThrough"),
            active = checkNotNull(active),
        )
    val consistent = item.hasValidSchedule && (item.type == RecurringType.INCOME || item.categoryId != null)
    return if (consistent) item else dropped("schedule/category")
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

/** A present (non-null) date string that does not parse. */
private fun DocumentSnapshot.isUnparsableDate(field: String): Boolean {
    val raw = getString(field)
    return raw != null && parseDate(field) == null
}
