package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * Restores a backup into whichever store the session uses (the guest file or the signed-in account), with the
 * background syncs paused through [RestoreGate] for the whole restore.
 */
class BackupRestorer
    @Inject
    constructor(
        private val guest: GuestBackupRestorer,
        private val account: AccountBackupRestorer,
        private val sessions: SessionRepository,
        private val gate: RestoreGate,
    ) {
        /** Whether a restore replaces existing data: always for an account, only a non-empty store for a guest. */
        suspend fun hasData(): Boolean = sessions.session.value is Session.SignedIn || guest.localHasData()

        /** Replaces the current data with [document]; [onProgress] reports (done, total) operations of an account. */
        suspend fun restore(
            document: BackupDocument,
            onProgress: (written: Int, total: Int) -> Unit,
        ): AccountRestoreResult =
            gate.hold {
                when (sessions.session.value) {
                    is Session.SignedIn -> account.restore(document, onProgress)
                    Session.Guest ->
                        guest.restore(document).fold(
                            onSuccess = { AccountRestoreResult.Success },
                            onFailure = { AccountRestoreResult.Failed(it) },
                        )
                    else -> AccountRestoreResult.Failed(IllegalStateException("Restore needs a session"))
                }
            }
    }
