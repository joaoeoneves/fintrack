package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.AutomationSettingsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import java.time.YearMonth
import javax.inject.Inject

private const val TAG = "FirestoreAutomationRepo"

/** The automation settings, the single document `users/{uid}/plan/automation`. Writes are not awaited. */
class FirestoreAutomationSettingsRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : AutomationSettingsRepository {
        override fun observeAutomationSettings(): Flow<AutomationSettings> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(AutomationSettings())
            return callbackFlow {
                val registration =
                    automationSettingsDoc(firestore, uid).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) trySend(snapshot.toAutomationSettings())
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun updateLineCursors(
            set: Map<String, YearMonth>,
            remove: Set<String>,
            clearLegacyPayday: Boolean,
        ): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                val removed = remove.associateWith<String, Any> { FieldValue.delete() }
                val cursors = removed + set.mapValues { it.value.toString() }
                val data =
                    buildMap<String, Any> {
                        if (cursors.isNotEmpty()) put("lineThrough", cursors)
                        if (clearLegacyPayday) put("paydayEnabled", false)
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                // Not awaited (see FirestoreSalaryProfileRepository): the write lands in the local cache at once.
                automationSettingsDoc(firestore, uid)
                    .set(data, SetOptions.merge())
                    .addOnFailureListener { Log.w(TAG, "Update line cursors failed", it) }
                Unit
            }
        }

        override suspend fun setSalaryPaysNextMonth(enabled: Boolean): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                val data = mapOf("salaryPaysNextMonth" to enabled, "updatedAt" to FieldValue.serverTimestamp())
                // Not awaited, like the other settings writes.
                automationSettingsDoc(firestore, uid)
                    .set(data, SetOptions.merge())
                    .addOnFailureListener { Log.w(TAG, "Set salary shift failed", it) }
                Unit
            }
        }

        override suspend fun recordPayday(
            incomes: List<Income>,
            cursors: Map<String, YearMonth>,
        ): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                val batch = firestore.batch()
                val user = firestore.collection("users").document(uid)
                incomes.forEach {
                    batch.set(user.collection("income").document(it.id), it.toFirestoreMap(includeCreatedAt = true))
                }
                val data =
                    buildMap<String, Any> {
                        if (cursors.isNotEmpty()) put("lineThrough", cursors.mapValues { it.value.toString() })
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                batch.set(automationSettingsDoc(firestore, uid), data, SetOptions.merge())
                // Not awaited: the batch is applied to the local cache at once and syncs when online.
                batch.commit().addOnFailureListener { Log.w(TAG, "Record payday failed", it) }
                Unit
            }
        }

        private fun notSignedIn(): Result<Unit> = Result.failure(IllegalStateException("Not signed in"))
    }

/** The singleton document of [uid]'s automation settings. Shared with the guest-data migration. */
internal fun automationSettingsDoc(
    firestore: FirebaseFirestore,
    uid: String,
): DocumentReference =
    firestore
        .collection("users")
        .document(uid)
        .collection("plan")
        .document("automation")

/** Written whole; the legacy cursor is absent while unset (the rules reject only a malformed one). */
internal fun AutomationSettings.toFirestoreMap(): Map<String, Any?> =
    buildMap {
        put("paydayEnabled", legacyPaydayEnabled)
        legacyPaydayRecordedThrough?.let { put("paydayThrough", it.toString()) }
        if (lineRecordedThrough.isNotEmpty()) put("lineThrough", lineRecordedThrough.mapValues { it.value.toString() })
        salaryPaysNextMonth?.let { put("salaryPaysNextMonth", it) }
        put("updatedAt", FieldValue.serverTimestamp())
    }

private fun DocumentSnapshot.toAutomationSettings(): AutomationSettings =
    AutomationSettings(
        legacyPaydayEnabled = getBoolean("paydayEnabled") ?: false,
        legacyPaydayRecordedThrough =
            getString("paydayThrough")?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
        lineRecordedThrough =
            (get("lineThrough") as? Map<*, *>)
                .orEmpty()
                .mapNotNull { (id, raw) ->
                    val month = (raw as? String)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                    if (id is String && month != null) id to month else null
                }.toMap(),
        salaryPaysNextMonth = getBoolean("salaryPaysNextMonth"),
    )
