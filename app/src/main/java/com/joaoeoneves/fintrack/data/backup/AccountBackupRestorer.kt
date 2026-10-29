package com.joaoeoneves.fintrack.data.backup

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.joaoeoneves.fintrack.data.automationSettingsDoc
import com.joaoeoneves.fintrack.data.awaitServer
import com.joaoeoneves.fintrack.data.deleteDocumentBatch
import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.data.migration.PendingWrite
import com.joaoeoneves.fintrack.data.migration.toBatches
import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.data.salaryProfileDoc
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.repository.CurrencyRepository
import com.joaoeoneves.fintrack.domain.repository.LanguageRepository
import com.joaoeoneves.fintrack.domain.repository.PlaceholderNamesProvider
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject

/** How a restore into the signed-in account ended. */
sealed interface AccountRestoreResult {
    /** Everything in the backup is in the account and everything else was removed. */
    data object Success : AccountRestoreResult

    /** The account could not be reached before anything was written; nothing changed. */
    data object Offline : AccountRestoreResult

    /** The restore stopped after [written] of [total] operations; retrying finishes it without duplicating. */
    data class Partial(
        val written: Int,
        val total: Int,
        val cause: Throwable,
    ) : AccountRestoreResult

    /** The restore could not start (not signed in, or the backup could not be turned into writes); nothing changed. */
    data class Failed(
        val cause: Throwable,
    ) : AccountRestoreResult
}

/** What the server holds before a restore: document ids per collection plus the two singletons. */
private class ServerState(
    val ids: Map<String, Set<String>>,
    val hasProfile: Boolean,
    val hasAutomation: Boolean,
)

/**
 * Replaces the signed-in account's data with a backup. Order, for safety:
 * 1. a pre-flight read (from the server, with a timeout) of every collection id; if it fails nothing is written;
 * 2. every record of the backup is upserted under its own id, in dependency order (envelopes, categories, budgets,
 *    expenses, income, planned income, recurring, month settings, salary profile, automation, goals), one awaited
 *    batch of at most 450 operations at a time;
 * 3. only then the account documents that are not in the backup are deleted (reverse order), then the preferences
 *    are applied.
 * A failure part-way leaves a superset of the backup (nothing is deleted before every upsert landed), and running
 * the restore again is idempotent (same ids, `set`, deletes of what is already gone).
 */
class AccountBackupRestorer
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
        private val placeholderNames: PlaceholderNamesProvider,
        private val themes: ThemeRepository,
        private val currencies: CurrencyRepository,
        private val languages: LanguageRepository,
    ) : AccountRestore {
        override suspend fun restore(
            document: BackupDocument,
            onProgress: (written: Int, total: Int) -> Unit,
        ): AccountRestoreResult {
            val uid = firebaseAuth.currentUser?.uid
            val prepared =
                if (uid == null) {
                    Result.failure(IllegalStateException("Not signed in"))
                } else {
                    runCatchingCancellable { prepare(uid, document.toLocalDataDocument()) }
                }
            val plan = prepared.getOrNull()
            return if (uid == null || plan == null) {
                val cause = prepared.exceptionOrNull() ?: IllegalStateException("Restore could not start")
                if (cause is PreflightOfflineException) {
                    AccountRestoreResult.Offline
                } else {
                    AccountRestoreResult.Failed(cause)
                }
            } else {
                commit(uid, plan, document, onProgress)
            }
        }

        private suspend fun commit(
            uid: String,
            plan: AccountRestorePlan,
            document: BackupDocument,
            onProgress: (written: Int, total: Int) -> Unit,
        ): AccountRestoreResult {
            val total = plan.total
            onProgress(0, total)
            var done = 0
            val failure =
                runCatchingCancellable {
                    val user = firestore.collection("users").document(uid)
                    for (batch in plan.writeBatches) {
                        commitWrites(user, batch)
                        done += batch.size
                        onProgress(done, total)
                    }
                    for (batch in plan.deleteBatches) {
                        deleteDocumentBatch(firestore, batch.map { user.collection(it.collection).document(it.id) })
                        done += batch.size
                        onProgress(done, total)
                    }
                }.exceptionOrNull()
            if (failure != null) return AccountRestoreResult.Partial(done, total, failure)
            applyPreferences(document.preferences)
            return AccountRestoreResult.Success
        }

        // Last, through the repositories so the change is pushed to the account; unknown names keep the current value.
        private suspend fun applyPreferences(backup: BackupPreferences?) {
            backup ?: return
            runCatchingCancellable {
                ThemePreference.entries.find { it.name == backup.theme }?.let { themes.setThemePreference(it) }
                CurrencyOption.entries.find { it.name == backup.currency }?.let { currencies.setCurrency(it) }
                AppLanguage.entries.find { it.name == backup.language }?.let { languages.setLanguage(it) }
            }
        }

        // Nothing is written here: reads the server, then builds the whole plan in memory.
        private suspend fun prepare(
            uid: String,
            local: LocalDataDocument,
        ): AccountRestorePlan {
            val server = readServerState(uid)
            val writes = restoreMigrationPlanOf(local, placeholderNames.deletedCategory()).toBatches()
            return accountRestorePlanOf(writes, server.ids, server.hasProfile, server.hasAutomation)
        }

        private suspend fun readServerState(uid: String): ServerState {
            val user = firestore.collection("users").document(uid)
            val ids =
                RESTORE_PRUNE_ORDER.associateWith { collection ->
                    preflight { user.collection(collection).get(Source.SERVER).await() }
                        .documents
                        .mapTo(mutableSetOf()) { it.id }
                }
            return ServerState(
                ids = ids,
                hasProfile = preflight { salaryProfileDoc(firestore, uid).get(Source.SERVER).await() }.exists(),
                hasAutomation = preflight { automationSettingsDoc(firestore, uid).get(Source.SERVER).await() }.exists(),
            )
        }

        /** Runs a server read; an unreachable server or a timeout becomes [PreflightOfflineException]. */
        private suspend fun <T : Any> preflight(block: suspend () -> T): T =
            try {
                awaitServer(block)
            } catch (e: IOException) {
                throw PreflightOfflineException(e)
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) throw PreflightOfflineException(e)
                throw e
            }

        private suspend fun commitWrites(
            user: DocumentReference,
            writes: List<PendingWrite>,
        ) {
            val batch = firestore.batch()
            writes.forEach {
                val doc = user.collection(it.collection).document(it.id)
                if (it.merge) batch.set(doc, it.data, SetOptions.merge()) else batch.set(doc, it.data)
            }
            awaitServer {
                batch.commit().await()
                Unit
            }
        }
    }

private class PreflightOfflineException(
    cause: Throwable,
) : Exception("Account unreachable", cause)
