package com.joaoeoneves.fintrack.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.joaoeoneves.fintrack.domain.repository.DataResetRepository
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/** Every subcollection of `users/{uid}` that can be listed (the rules allow whole-collection reads). */
private val WIPED_COLLECTIONS =
    listOf(
        "expenses",
        "income",
        "categories",
        "budgets",
        "plannedIncome",
        "recurring",
        "monthSettings",
        "categoryGroups",
        "goals",
    )

private const val PAGE_SIZE = 400L
private val SERVER_TIMEOUT = 30.seconds

/**
 * Deletes everything under `users/{uid}`: each collection is paged through the server and removed in
 * batches of at most [PAGE_SIZE] deletes, then the `plan/salaryProfile` and `plan/automation` singletons. The
 * `settings/preferences` document is left to the preferences sync, which overwrites it with the defaults. Calls are
 * awaited (unlike the normal repositories) so a failure surfaces and a retry resumes where the first run stopped.
 */
class FirestoreDataResetRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : DataResetRepository {
        override suspend fun deleteAllData(): Result<Unit> =
            runCatchingCancellable {
                val uid = firebaseAuth.currentUser?.uid ?: error("Not signed in")
                val user = firestore.collection("users").document(uid)
                WIPED_COLLECTIONS.forEach { wipe(user.collection(it)) }
                deleteDocuments(
                    listOf(
                        user.collection("plan").document("salaryProfile"),
                        user.collection("plan").document("automation"),
                    ),
                )
            }

        private suspend fun wipe(collection: CollectionReference) {
            while (true) {
                val page = serverCall { collection.limit(PAGE_SIZE).get(Source.SERVER).await() }
                if (page.isEmpty) return
                deleteDocuments(page.documents.map { it.reference })
            }
        }

        private suspend fun deleteDocuments(refs: List<DocumentReference>) {
            val batch = firestore.batch()
            refs.forEach { batch.delete(it) }
            serverCall { batch.commit().await() }
        }

        private suspend fun <T> serverCall(block: suspend () -> T): T =
            withTimeoutOrNull(SERVER_TIMEOUT) { block() } ?: throw IOException("Firestore did not respond in time")
    }
