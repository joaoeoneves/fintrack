package com.joaoeoneves.fintrack.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.AutomationSettingsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.YearMonth
import javax.inject.Inject

private const val TAG = "FirestoreAutomationRepo"
private const val SERVER_READ_TIMEOUT_MS = 10_000L

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
        ): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                val removed = remove.associateWith<String, Any> { FieldValue.delete() }
                val cursors = removed + forwardOnly(uid, set).mapValues { it.value.toString() }
                val data =
                    buildMap<String, Any> {
                        if (cursors.isNotEmpty()) put("lineThrough", cursors)
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                // Not awaited (see FirestoreSalaryProfileRepository): the write lands in the local cache at once.
                automationSettingsDoc(firestore, uid)
                    .set(data, SetOptions.merge())
                    .addOnFailureListener(onFail(TAG, "Update line cursors failed", notify = false))
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
                    .addOnFailureListener(onFail(TAG, "Set salary shift failed"))
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
                // Create-only and forward-only, decided on the server's data: when it cannot be read (offline) the
                // pass is skipped rather than guessing from the cache and overwriting another device's receipt.
                val absent = absentOnServer(user.collection("income"), incomes)
                absent.forEach {
                    batch.set(user.collection("income").document(it.id), it.toFirestoreMap(includeCreatedAt = true))
                }
                val forward = forwardOnly(uid, cursors)
                val data =
                    buildMap<String, Any> {
                        if (forward.isNotEmpty()) put("lineThrough", forward.mapValues { it.value.toString() })
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                batch.set(automationSettingsDoc(firestore, uid), data, SetOptions.merge())
                // Not awaited: the batch is applied to the local cache at once and syncs when online.
                batch.commit().addOnFailureListener(onFail(TAG, "Record payday failed", notify = false))
                Unit
            }
        }

        // [wanted] with each cursor raised to the server's when that is later: cursors only ever move forward, even
        // when the local cache is behind. Reads the server only when there is something to move; throws when it
        // cannot be read.
        private suspend fun forwardOnly(
            uid: String,
            wanted: Map<String, YearMonth>,
        ): Map<String, YearMonth> {
            if (wanted.isEmpty()) return wanted
            val stored =
                serverRead(automationSettingsDoc(firestore, uid))
                    .toAutomationSettings()
                    .lineRecordedThrough
            return wanted.mapValues { (id, month) -> maxOf(month, stored[id] ?: month) }
        }

        // The [incomes] whose document does not exist on the server; throws when it cannot be read.
        private suspend fun absentOnServer(
            collection: CollectionReference,
            incomes: List<Income>,
        ): List<Income> = incomes.filter { !serverRead(collection.document(it.id)).exists() }

        // A timeout is a plain failure here (a TimeoutCancellationException would read as a cancellation).
        private suspend fun serverRead(doc: DocumentReference): DocumentSnapshot =
            withTimeoutOrNull(SERVER_READ_TIMEOUT_MS) { doc.get(Source.SERVER).await() }
                ?: error("Server unreachable")

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

/** Written whole; the line cursors are absent while there are none. */
internal fun AutomationSettings.toFirestoreMap(): Map<String, Any?> =
    buildMap {
        if (lineRecordedThrough.isNotEmpty()) put("lineThrough", lineRecordedThrough.mapValues { it.value.toString() })
        salaryPaysNextMonth?.let { put("salaryPaysNextMonth", it) }
        put("updatedAt", FieldValue.serverTimestamp())
    }

private fun DocumentSnapshot.toAutomationSettings(): AutomationSettings =
    AutomationSettings(
        lineRecordedThrough =
            (get("lineThrough") as? Map<*, *>)
                .orEmpty()
                .mapNotNull { (id, raw) ->
                    val month = (raw as? String)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                    if (id is String && month != null) id to month else null
                }.toMap(),
        salaryPaysNextMonth = getBoolean("salaryPaysNextMonth"),
    )
