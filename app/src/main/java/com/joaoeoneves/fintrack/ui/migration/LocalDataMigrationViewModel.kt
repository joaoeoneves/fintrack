package com.joaoeoneves.fintrack.ui.migration

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.backup.BackupCounts
import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.data.local.LocalDataStore
import com.joaoeoneves.fintrack.data.migration.AccountMigrator
import com.joaoeoneves.fintrack.data.migration.MigrationOfferStore
import com.joaoeoneves.fintrack.data.migration.MigrationOfflineException
import com.joaoeoneves.fintrack.data.migration.MigrationResult
import com.joaoeoneves.fintrack.data.migration.shouldOfferMigration
import com.joaoeoneves.fintrack.di.IoDispatcher
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "LocalDataMigrationVM"

/**
 * Offers to copy the guest data into the account after signing in, runs the copy, and records what
 * the user decided. The local file is only touched (removed) after a full success and an explicit
 * choice.
 */
@HiltViewModel
class LocalDataMigrationViewModel
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        private val localDataStore: LocalDataStore,
        private val offerStore: MigrationOfferStore,
        private val migrator: AccountMigrator,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<MigrationUiState>(MigrationUiState.Hidden)
        val uiState: StateFlow<MigrationUiState> = _uiState.asStateFlow()

        /** The local data being offered / migrated; only replaced while the flow is idle. */
        private var document: LocalDataDocument? = null

        /** Revision dismissed without a decision: not offered again until the data changes. */
        private var suppressedRevision: Long? = null

        /** Records already written by earlier attempts of the current run. */
        private var carriedWritten = 0

        init {
            viewModelScope.launch {
                combine(
                    sessionRepository.session,
                    localDataStore.data,
                    offerStore.handledRevision,
                    ::Triple,
                ).map { (session, local, handled) ->
                    local.takeIf { shouldOfferMigration(session, it, handled) }
                }.distinctUntilChanged()
                    .collect(::onEligibleData)
            }
        }

        private fun onEligibleData(eligible: LocalDataDocument?) {
            val current = _uiState.value
            if (current !is MigrationUiState.Hidden && current !is MigrationUiState.Offer) return
            val offered = eligible?.takeIf { it.revision != suppressedRevision }
            document = offered
            _uiState.value = offered?.let { MigrationUiState.Offer(it.counts()) } ?: MigrationUiState.Hidden
        }

        fun onAccept() {
            if (_uiState.value is MigrationUiState.Offer) {
                carriedWritten = 0
                run()
            }
        }

        fun onRetry() {
            val current = _uiState.value
            val retryableError = current is MigrationUiState.Error && current.retryable
            if (current is MigrationUiState.PartialFailure || retryableError) run()
        }

        private fun run() {
            val local = document ?: return
            _uiState.value = MigrationUiState.Running(carriedWritten, carriedWritten)
            viewModelScope.launch {
                val result =
                    withContext(ioDispatcher) {
                        migrator.migrate(local) { written, total ->
                            val base = carriedWritten
                            _uiState.value = MigrationUiState.Running(base + written, base + total)
                        }
                    }
                _uiState.value = result.toState()
            }
        }

        private fun MigrationResult.toState(): MigrationUiState {
            val failure = failure
            val done = carriedWritten + written
            if (failure == null) return MigrationUiState.Done(done, skipped)
            Log.w(TAG, "Migration failed after $done records", failure)
            val message =
                UiText.StringResource(
                    if (failure is MigrationOfflineException) {
                        R.string.migration_error_offline
                    } else {
                        R.string.migration_error_failed
                    },
                )
            val overallTotal = carriedWritten + total
            carriedWritten = done
            // Nothing planned means the account could not even be read; retrying re-reads it, which is safe.
            return if (total == 0) {
                MigrationUiState.Error(message, retryable = true)
            } else {
                MigrationUiState.PartialFailure(done, overallTotal, message)
            }
        }

        /** The user does not want the copy; remember that for this version of the local data. */
        fun onDecline() {
            if (_uiState.value !is MigrationUiState.Offer) return
            val revision = document?.revision
            _uiState.value = MigrationUiState.Hidden
            viewModelScope.launch { revision?.let { offerStore.markHandledLogged(it) } }
        }

        /** After a successful copy: remove the data from this device (a safety copy is kept internally). */
        fun onRemoveLocal() {
            if (_uiState.value !is MigrationUiState.Done) return
            viewModelScope.launch {
                localDataStore
                    .clearWithBackup()
                    .onSuccess { finish() }
                    .onFailure {
                        Log.w(TAG, "Could not clear the local data", it)
                        val message = UiText.StringResource(R.string.migration_error_remove_failed)
                        _uiState.value = MigrationUiState.Error(message)
                    }
            }
        }

        /** After a successful copy: leave the local data in place and do not offer it again. */
        fun onKeepLocal() {
            if (_uiState.value !is MigrationUiState.Done) return
            val revision = document?.revision
            viewModelScope.launch {
                revision?.let { offerStore.markHandledLogged(it) }
                finish()
            }
        }

        /** Closes an error / partial-failure / offer dialog without deciding; the data stays untouched. */
        fun onDismiss() {
            suppressedRevision = document?.revision
            finish()
        }

        private fun finish() {
            document = null
            carriedWritten = 0
            _uiState.value = MigrationUiState.Hidden
        }
    }

private fun LocalDataDocument.counts() = BackupCounts(expenses.size, income.size, budgets.size, categories.size)

private suspend fun MigrationOfferStore.markHandledLogged(revision: Long) {
    markHandled(revision).onFailure { Log.w(TAG, "Could not record the handled migration revision", it) }
}
