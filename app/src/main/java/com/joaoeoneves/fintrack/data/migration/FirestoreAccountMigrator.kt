package com.joaoeoneves.fintrack.data.migration

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.joaoeoneves.fintrack.data.automationSettingsDoc
import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.data.salaryProfileDoc
import com.joaoeoneves.fintrack.data.toCategoryOrNull
import com.joaoeoneves.fintrack.data.toEnvelopeOrNull
import com.joaoeoneves.fintrack.domain.repository.PlaceholderNamesProvider
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

private val SERVER_TIMEOUT = 30.seconds

/**
 * Moves guest data into `users/{uid}`: ids are preserved (`document(id)`), batches are committed
 * one at a time and awaited, in the order category groups, categories, budgets, expenses, income, planned income,
 * recurring, month
 * settings, salary profile, goals so the rules' `existsAfter` category lookup finds every custom category a later batch
 * references. Awaiting is deliberate here (unlike the normal repositories): the caller must know a batch
 * reached the server before the local copy may be removed, so each wait is bounded by a timeout.
 */
class FirestoreAccountMigrator
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
        private val placeholderNames: PlaceholderNamesProvider,
    ) : AccountMigrator {
        override suspend fun migrate(
            local: LocalDataDocument,
            onProgress: (written: Int, total: Int) -> Unit,
        ): MigrationResult {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return MigrationResult(0, 0, 0, IllegalStateException("Not signed in"))
            return runCatchingCancellable {
                val plan = planMigration(local, fetchSnapshot(uid), placeholderNames.deletedCategory())
                plan to plan.toBatches()
            }.fold(
                onSuccess = { (plan, batches) ->
                    onProgress(0, plan.total)
                    commit(uid, plan, batches, onProgress)
                },
                onFailure = { MigrationResult(0, 0, 0, it) },
            )
        }

        private fun userDoc(uid: String) = firestore.collection("users").document(uid)

        private suspend fun fetchSnapshot(uid: String): RemoteAccountSnapshot {
            suspend fun ids(collection: String) = serverIds(uid, collection)
            val categories =
                serverGet { userDoc(uid).collection("categories").get(Source.SERVER).await() }
                    .documents
                    .mapNotNull { it.toCategoryOrNull() }
            val groups =
                serverGet { userDoc(uid).collection("categoryGroups").get(Source.SERVER).await() }
                    .documents
                    .mapNotNull { it.toEnvelopeOrNull() }
            val lines = serverGet { userDoc(uid).collection("plannedIncome").get(Source.SERVER).await() }.documents
            return RemoteAccountSnapshot(
                expenseIds = ids("expenses"),
                incomeIds = ids("income"),
                budgetCategoryIds = ids("budgets"),
                plannedIncomeIds = ids("plannedIncome"),
                plannedIncomeLabels = lines.mapNotNullTo(mutableSetOf()) { it.getString("label") },
                plannedIncomeIdsByLabel = idsByLabel(lines),
                recurringIds = ids("recurring"),
                goalIds = ids("goals"),
                monthSettingsIds = ids("monthSettings"),
                categories = categories,
                categoryGroups = groups,
                hasSalaryProfile = hasSalaryProfile(uid),
                hasAutomation = serverGet { automationSettingsDoc(firestore, uid).get(Source.SERVER).await() }.exists(),
            )
        }

        private fun idsByLabel(lines: List<DocumentSnapshot>): Map<String, String> {
            val byLabel = LinkedHashMap<String, String>()
            lines.forEach { doc -> doc.getString("label")?.let { byLabel.putIfAbsent(it.labelKey(), doc.id) } }
            return byLabel
        }

        private suspend fun hasSalaryProfile(uid: String): Boolean =
            serverGet { salaryProfileDoc(firestore, uid).get(Source.SERVER).await() }.exists()

        private suspend fun serverIds(
            uid: String,
            collection: String,
        ): Set<String> =
            serverGet { userDoc(uid).collection(collection).get(Source.SERVER).await() }
                .documents
                .mapTo(mutableSetOf()) { it.id }

        /** Runs [block] with a timeout; an unreachable server (or the timeout) becomes [MigrationOfflineException]. */
        private suspend fun <T : Any> serverGet(block: suspend () -> T): T =
            try {
                withTimeoutOrNull(SERVER_TIMEOUT) { block() }
                    ?: throw MigrationOfflineException("Timed out waiting for the account")
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                    throw MigrationOfflineException("Account unreachable", e)
                }
                throw e
            }

        private suspend fun commit(
            uid: String,
            plan: MigrationPlan,
            batches: List<List<PendingWrite>>,
            onProgress: (written: Int, total: Int) -> Unit,
        ): MigrationResult {
            var written = 0
            val user = userDoc(uid)
            for (chunk in batches) {
                // Earlier batches stay written on failure; a retry skips them.
                val failure = runCatchingCancellable { commitBatch(user, chunk) }.exceptionOrNull()
                if (failure != null) return MigrationResult(written, plan.total, plan.skipped, failure)
                written += chunk.size
                onProgress(written, plan.total)
            }
            return MigrationResult(written, plan.total, plan.skipped)
        }

        private suspend fun commitBatch(
            user: DocumentReference,
            writes: List<PendingWrite>,
        ) {
            val batch = firestore.batch()
            writes.forEach {
                val doc = user.collection(it.collection).document(it.id)
                if (it.merge) batch.set(doc, it.data, SetOptions.merge()) else batch.set(doc, it.data)
            }
            serverGet {
                batch.commit().await()
                Unit
            }
        }
    }
