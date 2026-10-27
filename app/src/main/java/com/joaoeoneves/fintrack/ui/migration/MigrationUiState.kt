package com.joaoeoneves.fintrack.ui.migration

import com.joaoeoneves.fintrack.data.backup.BackupCounts
import com.joaoeoneves.fintrack.ui.common.UiText

/** State of the "copy this device's data into your account" flow shown after signing in. */
sealed interface MigrationUiState {
    data object Hidden : MigrationUiState

    data class Offer(
        val counts: BackupCounts,
    ) : MigrationUiState

    /** Not dismissable; [total] is 0 until the plan is known. */
    data class Running(
        val done: Int,
        val total: Int,
    ) : MigrationUiState

    data class PartialFailure(
        val done: Int,
        val total: Int,
        val message: UiText,
    ) : MigrationUiState

    data class Done(
        val written: Int,
        val skipped: Int,
    ) : MigrationUiState

    data class Error(
        val message: UiText,
        /** Whether a retry makes sense (the account could not be read); shows a Retry button. */
        val retryable: Boolean = false,
    ) : MigrationUiState
}

/** What the migration dialogs can ask for. */
data class MigrationActions(
    val onAccept: () -> Unit,
    val onDecline: () -> Unit,
    val onRetry: () -> Unit,
    val onRemoveLocal: () -> Unit,
    val onKeepLocal: () -> Unit,
    val onDismiss: () -> Unit,
)
