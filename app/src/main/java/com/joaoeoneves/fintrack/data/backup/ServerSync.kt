package com.joaoeoneves.fintrack.data.backup

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.joaoeoneves.fintrack.data.automationSettingsDoc
import com.joaoeoneves.fintrack.data.awaitServer
import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.data.salaryProfileDoc
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** The server of the signed-in account could not be read, so an export would not be the real data. */
class ServerUnreachableException(
    cause: Throwable,
) : Exception("The account's server could not be reached", cause)

/** Makes sure what the repositories read next is the server's state, not a stale cache. */
interface ServerSync {
    /** Succeeds at once for a guest; for an account reads every collection from the server or fails. */
    suspend fun refresh(): Result<Unit>
}

/**
 * Reads every account collection and both singleton documents with [Source.SERVER]; a successful server read
 * updates Firestore's local cache, so the listeners read right after see the server's data. An unreachable server
 * (offline, timeout, permission) fails with [ServerUnreachableException].
 */
class FirestoreServerSync
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : ServerSync {
        override suspend fun refresh(): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return Result.success(Unit)
            return runCatchingCancellable {
                val user = firestore.collection("users").document(uid)
                RESTORE_PRUNE_ORDER.forEach { collection ->
                    awaitServer { user.collection(collection).get(Source.SERVER).await() }
                }
                awaitServer { salaryProfileDoc(firestore, uid).get(Source.SERVER).await() }
                awaitServer { automationSettingsDoc(firestore, uid).get(Source.SERVER).await() }
                Unit
            }.recoverCatching { throw ServerUnreachableException(it) }
        }
    }
