package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import java.time.DateTimeException
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "FirestoreGoalRepo"

/**
 * Savings goals stored under `users/{uid}/goals`. Writes are not awaited (they land in the local cache
 * immediately and sync when online).
 */
class FirestoreGoalRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : GoalRepository {
        private fun collection(uid: String) = firestore.collection("users").document(uid).collection("goals")

        override fun observeGoals(): Flow<List<SavingsGoal>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

            return callbackFlow {
                val registration =
                    collection(uid).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(
                                snapshot.documents
                                    .mapNotNull { it.toGoalOrNull() }
                                    .sortedWith(compareBy({ it.order }, { it.id })),
                            )
                        }
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun saveGoal(goal: SavingsGoal): Result<String> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                val col = collection(uid)
                val docRef = if (goal.id.isBlank()) col.document() else col.document(goal.id)
                // Not awaited: the write lands in Firestore's local cache immediately and syncs when online.
                docRef
                    .set(goal.toFirestoreMap())
                    .addOnFailureListener(onFail(TAG, "Save goal write ${docRef.id} failed"))
                docRef.id
            }
        }

        override suspend fun deleteGoal(id: String): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                collection(uid)
                    .document(id)
                    .delete()
                    .addOnFailureListener(onFail(TAG, "Delete goal $id failed"))
                Unit
            }
        }

        private fun <T> notSignedIn(): Result<T> = Result.failure(IllegalStateException("Not signed in"))
    }

/** Written whole (no merge); the id is the document id. Shared with the guest-data migration. */
internal fun SavingsGoal.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        "name" to name,
        "targetCents" to targetCents,
        "targetType" to targetType.name,
        "startDate" to startDate.toString(),
        "targetDate" to targetDate?.toString(),
        "initialCents" to initialCents,
        "linkedRecurringIds" to linkedRecurringIds.sorted(),
        "linkedCategoryId" to linkedCategoryId?.value,
        "archived" to archived,
        "order" to order,
        "recurringId" to recurringId,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

/** Maps a document, or drops it (logged) when a required field is missing or invalid. */
internal fun DocumentSnapshot.toGoalOrNull(): SavingsGoal? = mapOrNull("goal") { parseGoal() }

private fun DocumentSnapshot.parseGoal(): SavingsGoal? {
    val name = getString("name")?.takeIf { it.isNotBlank() }
    val targetCents = getLong("targetCents")
    val targetType = getString("targetType")?.let { raw -> GoalTargetType.entries.find { it.name == raw } }
    val startDate = getString("startDate")?.let(::parseDateOrNull)
    val endRaw = getString("targetDate")
    val targetDate = endRaw?.let(::parseDateOrNull)
    val order = getLong("order")
    val invalid = name == null || targetCents == null || targetType == null || startDate == null || order == null
    return if (invalid || (endRaw != null && targetDate == null)) {
        Log.w(TAG, "Dropping malformed goal doc $id")
        null
    } else {
        SavingsGoal(
            id = id,
            name = checkNotNull(name),
            targetCents = checkNotNull(targetCents),
            targetType = checkNotNull(targetType),
            startDate = checkNotNull(startDate),
            targetDate = targetDate,
            initialCents = getLong("initialCents") ?: 0L,
            linkedRecurringIds = (get("linkedRecurringIds") as? List<*>).orEmpty().filterIsInstance<String>().toSet(),
            linkedCategoryId = getString("linkedCategoryId")?.takeIf { it.isNotBlank() }?.let(::CategoryId),
            archived = get("archived") as? Boolean ?: false,
            recurringId = getString("recurringId")?.takeIf { it.isNotBlank() },
            order = checkNotNull(order).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
        )
    }
}

private fun parseDateOrNull(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw)
    } catch (e: DateTimeException) {
        Log.w(TAG, "Unparsable goal date '$raw'", e)
        null
    }
