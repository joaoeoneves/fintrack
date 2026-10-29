package com.joaoeoneves.fintrack.data.preferences

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.joaoeoneves.fintrack.data.onFail
import com.joaoeoneves.fintrack.di.ApplicationScope
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.UserPreferences
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PreferencesSync"
private const val UPDATED_AT = "updatedAt"

/**
 * Syncs preferences with `users/{uid}/settings/preferences` while signed in (a guest never touches
 * Firestore, which is resolved lazily). Local edits are pushed fire-and-forget
 * (Firestore queues them offline); remote changes are applied to the DataStore only, so there is
 * no push/apply feedback loop.
 */
@Singleton
class FirestorePreferencesSync
    @Inject
    constructor(
        private val firestore: Lazy<FirebaseFirestore>,
        private val sessionRepository: SessionRepository,
        private val store: UserPreferencesStore,
        @ApplicationScope private val scope: CoroutineScope,
    ) : PreferencesSync {
        private val started = AtomicBoolean(false)

        @Volatile
        private var signedInUid: String? = null

        override fun start() {
            if (!started.compareAndSet(false, true)) return
            scope.launch {
                sessionRepository.session
                    .map { (it as? Session.SignedIn)?.user?.uid }
                    .distinctUntilChanged()
                    .collectLatest { uid ->
                        signedInUid = uid
                        if (uid != null) syncUser(uid)
                    }
            }
        }

        override fun push(
            field: PreferenceField,
            value: String,
        ) {
            val uid = signedInUid ?: return
            write(prefsDoc(uid), mapOf(field.key to value))
        }

        private suspend fun syncUser(uid: String) {
            val doc = prefsDoc(uid)
            snapshots(doc)
                .catch { Log.w(TAG, "Preferences listener failed", it) }
                .collect { snapshot ->
                    when (val action = decideSyncAction(snapshot)) {
                        SyncAction.None -> Unit
                        SyncAction.UploadAll ->
                            write(doc, store.data.first().fieldValues(PreferenceField.entries.toSet()))
                        is SyncAction.Merge -> merge(doc, action.remote)
                    }
                }
        }

        private suspend fun merge(
            doc: DocumentReference,
            remote: RemotePreferences,
        ) {
            val merged: UserPreferences = store.update { it.mergedWith(remote) }
            if (remote.missing.isNotEmpty()) write(doc, merged.fieldValues(remote.missing))
        }

        private fun snapshots(doc: DocumentReference): Flow<RemoteSnapshot> =
            callbackFlow {
                val registration =
                    doc.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                        if (error != null) {
                            close(error)
                        } else if (snapshot != null) {
                            trySend(
                                RemoteSnapshot(
                                    data = if (snapshot.exists()) snapshot.data.orEmpty() else null,
                                    fromCache = snapshot.metadata.isFromCache,
                                    hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                                ),
                            )
                        }
                    }
                awaitClose { registration.remove() }
            }

        private fun write(
            doc: DocumentReference,
            fields: Map<String, String>,
        ) {
            doc
                .set(fields + (UPDATED_AT to FieldValue.serverTimestamp()), SetOptions.merge())
                .addOnFailureListener(onFail(TAG, "Preferences upload failed"))
        }

        private fun prefsDoc(uid: String): DocumentReference =
            firestore
                .get()
                .collection("users")
                .document(uid)
                .collection("settings")
                .document("preferences")
    }
