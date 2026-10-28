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

        override suspend fun setPaydayEnabled(
            enabled: Boolean,
            cursor: YearMonth?,
        ): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                val data =
                    buildMap<String, Any> {
                        put("paydayEnabled", enabled)
                        cursor?.let { put("paydayThrough", it.toString()) }
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                // Not awaited (see FirestoreSalaryProfileRepository): the write lands in the local cache at once.
                automationSettingsDoc(firestore, uid)
                    .set(data, SetOptions.merge())
                    .addOnFailureListener { Log.w(TAG, "Set payday enabled failed", it) }
                Unit
            }
        }

        override suspend fun recordPayday(
            month: YearMonth,
            incomes: List<Income>,
        ): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                val batch = firestore.batch()
                val user = firestore.collection("users").document(uid)
                incomes.forEach {
                    batch.set(user.collection("income").document(it.id), it.toFirestoreMap(includeCreatedAt = true))
                }
                val cursor = mapOf("paydayThrough" to month.toString(), "updatedAt" to FieldValue.serverTimestamp())
                batch.set(automationSettingsDoc(firestore, uid), cursor, SetOptions.merge())
                // Not awaited: the batch is applied to the local cache at once and syncs when online.
                batch.commit().addOnFailureListener { Log.w(TAG, "Record payday $month failed", it) }
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

/** Written whole; the cursor is absent while unset (the rules reject only a malformed one). */
internal fun AutomationSettings.toFirestoreMap(): Map<String, Any?> =
    buildMap {
        put("paydayEnabled", paydayEnabled)
        paydayRecordedThrough?.let { put("paydayThrough", it.toString()) }
        put("updatedAt", FieldValue.serverTimestamp())
    }

private fun DocumentSnapshot.toAutomationSettings(): AutomationSettings =
    AutomationSettings(
        paydayEnabled = getBoolean("paydayEnabled") ?: false,
        paydayRecordedThrough = getString("paydayThrough")?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
    )
