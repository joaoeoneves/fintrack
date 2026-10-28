package com.joaoeoneves.fintrack.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/** How long one awaited server call may take before it counts as unreachable. */
internal val FIRESTORE_SERVER_TIMEOUT = 30.seconds

/** Runs [block], throwing [IOException] when it does not finish within [FIRESTORE_SERVER_TIMEOUT]. */
internal suspend fun <T : Any> awaitServer(block: suspend () -> T): T =
    withTimeoutOrNull(FIRESTORE_SERVER_TIMEOUT) { block() } ?: throw IOException("Firestore did not respond in time")

/** Deletes [refs] in one batch and awaits the server (with a timeout). The caller keeps [refs] within the batch cap. */
internal suspend fun deleteDocumentBatch(
    firestore: FirebaseFirestore,
    refs: List<DocumentReference>,
) {
    val batch = firestore.batch()
    refs.forEach { batch.delete(it) }
    awaitServer {
        batch.commit().await()
        Unit
    }
}
