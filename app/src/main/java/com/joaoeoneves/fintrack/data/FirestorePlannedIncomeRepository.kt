package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

private const val TAG = "FirestorePlannedIncomeRepo"

class FirestorePlannedIncomeRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : PlannedIncomeRepository {
        private fun collection(uid: String) = firestore.collection("users").document(uid).collection("plannedIncome")

        override fun observePlannedIncome(): Flow<List<PlannedIncome>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

            return callbackFlow {
                val registration =
                    collection(uid).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(snapshot.toPlannedIncomeList())
                        }
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun savePlannedIncome(line: PlannedIncome): Result<String> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                val col = collection(uid)
                val docRef = if (line.id.isBlank()) col.document() else col.document(line.id)
                // Not awaited: the write lands in Firestore's local cache immediately and syncs when
                // the device is online. Awaiting would hang indefinitely while offline.
                docRef
                    .set(line.toFirestoreMap())
                    .addOnFailureListener { Log.w(TAG, "Save planned income write ${docRef.id} failed", it) }
                docRef.id
            }
        }

        override suspend fun deletePlannedIncome(id: String): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                val docRef = collection(uid).document(id)
                // Not awaited (see savePlannedIncome); deleting a missing document is a no-op.
                docRef.delete().addOnFailureListener { Log.w(TAG, "Delete planned income failed: $id", it) }
                Unit
            }
        }

        private fun <T> notSignedIn(): Result<T> = Result.failure(IllegalStateException("Not signed in"))
    }

/** Written whole (no merge); the id is the document id. Shared with the guest-data migration. */
internal fun PlannedIncome.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        "label" to label,
        "amountCents" to amountCents,
        "order" to order,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

private fun QuerySnapshot.toPlannedIncomeList(): List<PlannedIncome> =
    documents.mapNotNull { it.toPlannedIncomeOrNull() }.sortedWith(compareBy({ it.order }, { it.id }))

private fun DocumentSnapshot.toPlannedIncomeOrNull(): PlannedIncome? {
    val label = getString("label")
    val amountCents = getLong("amountCents")
    val order = getLong("order")

    return when {
        label == null -> dropped("label")
        amountCents == null -> dropped("amountCents")
        order == null -> dropped("order")
        else -> PlannedIncome(id = id, label = label, amountCents = amountCents, order = order.toInt())
    }
}

private fun DocumentSnapshot.dropped(field: String): PlannedIncome? {
    Log.w(TAG, "Dropping malformed planned income doc $id: missing or invalid '$field'")
    return null
}
