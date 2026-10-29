package com.joaoeoneves.fintrack.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.joaoeoneves.fintrack.domain.repository.SuggestionRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

private const val TAG = "FirestoreSuggestionRepo"

/** The dismissed suggestions, the single document `users/{uid}/settings/suggestions`. Writes are not awaited. */
class FirestoreSuggestionRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : SuggestionRepository {
        override fun observeDismissedRecurring(): Flow<Set<String>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptySet())
            return callbackFlow {
                val registration =
                    suggestionsDoc(firestore, uid).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) trySend(snapshot.toDismissedRecurring())
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun dismissRecurring(key: String): Result<Unit> = change(FieldValue.arrayUnion(key), "Dismiss")

        override suspend fun undismissRecurring(key: String): Result<Unit> {
            val remove = FieldValue.arrayRemove(key)
            return change(remove, "Undismiss")
        }

        private suspend fun change(
            update: FieldValue,
            what: String,
        ): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable {
                val data = mapOf("dismissedRecurring" to update, "updatedAt" to FieldValue.serverTimestamp())
                // Not awaited, like the other settings writes: the local cache takes it at once.
                suggestionsDoc(firestore, uid)
                    .set(data, SetOptions.merge())
                    .addOnFailureListener(onFail(TAG, "$what recurring suggestion failed"))
                Unit
            }
        }
    }

/** The singleton document of [uid]'s dismissed suggestions. Shared with the guest-data migration and the reset. */
internal fun suggestionsDoc(
    firestore: FirebaseFirestore,
    uid: String,
): DocumentReference =
    firestore
        .collection("users")
        .document(uid)
        .collection("settings")
        .document("suggestions")

private fun DocumentSnapshot.toDismissedRecurring(): Set<String> =
    (get("dismissedRecurring") as? List<*>).orEmpty().filterIsInstance<String>().toSet()
