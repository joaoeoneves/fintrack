package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
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

        override suspend fun setWorkingDaysOverride(
            month: YearMonth,
            days: Int?,
        ): Result<Unit> = writeField(month, FIELD_WORKING_DAYS, days, otherField = FIELD_DAYS_OFF)

        override suspend fun setDaysOff(
            month: YearMonth,
            days: Int,
        ): Result<Unit> = writeField(month, FIELD_DAYS_OFF, days.takeIf { it > 0 }, otherField = FIELD_WORKING_DAYS)

        /**
         * Sets [field] to [value] (or clears it when `null`) without touching [otherField]. The document is
         * deleted when clearing leaves neither set; the other field is read from the local cache so this
         * works offline (if the cache cannot tell, the cleared field is just removed from the document).
         */
        private suspend fun writeField(
            month: YearMonth,
            field: String,
            value: Int?,
            otherField: String,
        ): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable {
                val docRef = collection(uid).document(month.toString())
                // Not awaited: the write lands in Firestore's local cache immediately and syncs when
                // the device is online. Awaiting would hang indefinitely while offline.
                if (value == null && !otherFieldIsSet(docRef, otherField)) {
                    docRef.delete().addOnFailureListener(onFail(TAG, "Clear month settings ${docRef.id} failed"))
                } else {
                    val data =
                        mapOf(field to (value ?: FieldValue.delete()), "updatedAt" to FieldValue.serverTimestamp())
                    docRef
                        .set(data, SetOptions.merge())
                        .addOnFailureListener(onFail(TAG, "Set month settings ${docRef.id} failed"))
                }
                Unit
            }
        }

        // `true` when unknown, so a cache miss never deletes a field the user set.
        private suspend fun otherFieldIsSet(
            docRef: DocumentReference,
            otherField: String,
        ): Boolean {
            val cached = runCatchingCancellable { docRef.get(Source.CACHE).await() }.getOrNull() ?: return true
            return cached.getLong(otherField) != null
        }
    }

/** Written whole; only the fields that are set are included. The document id is the month (`yyyy-MM`). */
internal fun MonthSettings.toFirestoreMap(): Map<String, Any?> =
    buildMap {
        workingDaysOverride?.let { put(FIELD_WORKING_DAYS, it) }
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
        val days = getLong(FIELD_WORKING_DAYS)?.toInt()
        val daysOff = getLong(FIELD_DAYS_OFF)?.toInt() ?: 0
        when {
            month == null -> {
                Log.w(TAG, "Dropping malformed month settings doc $id")
                null
            }
            // A leftover document with nothing set (a cleared field offline) carries no information.
            days == null && daysOff == 0 -> null
            else -> MonthSettings(month = month, workingDaysOverride = days, daysOff = daysOff)
        }
    }
