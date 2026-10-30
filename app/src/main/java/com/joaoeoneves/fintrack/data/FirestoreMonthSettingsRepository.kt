package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
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
private const val FIELD_WORKING_DAYS = "workingDays"
private const val FIELD_DAYS_OFF = "daysOff"

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
                            trySend(snapshot.toMonthSettings())
                        }
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun setDaysOff(
            month: YearMonth,
            days: Int,
        ): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable {
                val docRef = collection(uid).document(month.toString())
                // Not awaited: the write lands in Firestore's local cache immediately and syncs when
                // the device is online. Awaiting would hang indefinitely while offline.
                if (days <= 0) {
                    docRef.delete().addOnFailureListener(onFail(TAG, "Clear month settings ${docRef.id} failed"))
                } else {
                    // `workingDays` is the pre-5.5 manual override: dropped from the document on the next save.
                    val data =
                        mapOf(
                            FIELD_DAYS_OFF to days,
                            FIELD_WORKING_DAYS to FieldValue.delete(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    docRef
                        .set(data, SetOptions.merge())
                        .addOnFailureListener(onFail(TAG, "Set month settings ${docRef.id} failed"))
                }
                Unit
            }
        }
    }

/** Written whole; only the fields that are set are included. The document id is the month (`yyyy-MM`). */
internal fun MonthSettings.toFirestoreMap(): Map<String, Any?> =
    buildMap {
        if (daysOff > 0) put(FIELD_DAYS_OFF, daysOff)
        put("updatedAt", FieldValue.serverTimestamp())
    }

internal fun QuerySnapshot.toMonthSettings(): List<MonthSettings> = documents.mapNotNull { it.toMonthSettingsOrNull() }

private fun DocumentSnapshot.toMonthSettingsOrNull(): MonthSettings? =
    mapOrNull("monthSettings") {
        val month =
            try {
                YearMonth.parse(id)
            } catch (_: DateTimeException) {
                null
            }
        val daysOff = getLong(FIELD_DAYS_OFF)?.toInt() ?: 0
        when {
            month == null -> {
                Log.w(TAG, "Dropping malformed month settings doc $id")
                null
            }
            // A leftover document with nothing set (only the legacy working-days override, or a field cleared
            // offline) carries no information.
            daysOff == 0 -> null
            else -> MonthSettings(month = month, daysOff = daysOff)
        }
    }
