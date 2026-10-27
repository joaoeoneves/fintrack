package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import java.time.DateTimeException
import java.time.YearMonth
import javax.inject.Inject

private const val TAG = "FirestoreMonthSettingsRepo"

class FirestoreMonthSettingsRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : MonthSettingsRepository {
        private fun collection(uid: String) = firestore.collection("users").document(uid).collection("monthSettings")

        override fun observeMonthSettings(): Flow<List<MonthSettings>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

            return callbackFlow {
                val registration =
                    collection(uid).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(snapshot.documents.mapNotNull { it.toMonthSettingsOrNull() })
                        }
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun setWorkingDaysOverride(
            month: YearMonth,
            days: Int?,
        ): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable {
                val docRef = collection(uid).document(month.toString())
                // Not awaited: the write lands in Firestore's local cache immediately and syncs when
                // the device is online. Awaiting would hang indefinitely while offline.
                if (days == null) {
                    docRef.delete().addOnFailureListener { Log.w(TAG, "Clear working days ${docRef.id} failed", it) }
                } else {
                    docRef
                        .set(MonthSettings(month, days).toFirestoreMap())
                        .addOnFailureListener { Log.w(TAG, "Set working days ${docRef.id} failed", it) }
                }
                Unit
            }
        }
    }

/** Written whole (no merge); the document id is the month (`yyyy-MM`). Shared with the guest-data migration. */
internal fun MonthSettings.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        "workingDays" to workingDaysOverride,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

private fun DocumentSnapshot.toMonthSettingsOrNull(): MonthSettings? =
    mapOrNull("monthSettings") {
        val month =
            try {
                YearMonth.parse(id)
            } catch (_: DateTimeException) {
                null
            }
        val days = getLong("workingDays")?.toInt()
        if (month == null || days == null) {
            Log.w(TAG, "Dropping malformed month settings doc $id")
            null
        } else {
            MonthSettings(month = month, workingDaysOverride = days)
        }
    }
