package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.requireValidDaysOff
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
private const val FIELD_VACATION_DAYS = "vacationDays"
private const val FIELD_SICK_DAYS = "sickDays"
private const val FIELD_OTHER_DAYS = "otherDays"

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
            months: Collection<YearMonth>,
            vacation: Int,
            other: Int,
        ): Result<Unit> {
            requireValidDaysOff(months, vacation, other)
            val uid = firebaseAuth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable {
                val batch = firestore.batch()
                months.distinct().forEach { month ->
                    val docRef = collection(uid).document(month.toString())
                    if (vacation + other == 0) {
                        batch.delete(docRef)
                    } else {
                        // `workingDays` (pre-5.5) and `daysOff` (pre-5.6 total) are dropped on the next save.
                        val data =
                            buildMap<String, Any> {
                                put(FIELD_VACATION_DAYS, vacation.orDelete())
                                put(FIELD_SICK_DAYS, FieldValue.delete())
                                put(FIELD_OTHER_DAYS, other.orDelete())
                                put(FIELD_DAYS_OFF, FieldValue.delete())
                                put(FIELD_WORKING_DAYS, FieldValue.delete())
                                put("updatedAt", FieldValue.serverTimestamp())
                            }
                        batch.set(docRef, data, SetOptions.merge())
                    }
                }
                // Not awaited: the write lands in Firestore's local cache immediately and syncs when
                // the device is online. Awaiting would hang indefinitely while offline.
                batch.commit().addOnFailureListener(onFail(TAG, "Set month settings failed"))
                Unit
            }
        }
    }

private fun Int.orDelete(): Any = if (this > 0) this else FieldValue.delete()

/** Written whole; only the fields that are set are included. The document id is the month (`yyyy-MM`). */
internal fun MonthSettings.toFirestoreMap(): Map<String, Any?> =
    buildMap {
        if (vacationDays > 0) put(FIELD_VACATION_DAYS, vacationDays)
        if (otherDays > 0) put(FIELD_OTHER_DAYS, otherDays)
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
        if (month == null) {
            Log.w(TAG, "Dropping malformed month settings doc $id")
            return@mapOrNull null
        }
        val vacation = getLong(FIELD_VACATION_DAYS)?.toInt()
        val sick = getLong(FIELD_SICK_DAYS)?.toInt()
        val other = getLong(FIELD_OTHER_DAYS)?.toInt()
        // A pre-5.6 document only has the total, which counts as vacation; once a typed field exists it is ignored.
        val legacy = vacation == null && sick == null && other == null
        val vacationDays = if (legacy) getLong(FIELD_DAYS_OFF)?.toInt() ?: 0 else vacation ?: 0
        // A leftover document with nothing set (only the legacy working-days override, or fields cleared
        // offline) carries no information.
        val otherDays = ((other ?: 0) + (sick ?: 0)).coerceAtMost(MAX_DAYS_OFF - vacationDays)
        MonthSettings(month, vacationDays, otherDays).takeUnless { it.isBlank }
    }
