package com.joaoeoneves.fintrack.ui.backup

import com.joaoeoneves.fintrack.data.backup.BackupCounts
import com.joaoeoneves.fintrack.ui.common.UiText

sealed interface BackupExportState {
    data object Idle : BackupExportState

    data object Exporting : BackupExportState

    data class Done(
        val counts: BackupCounts,
    ) : BackupExportState

    data class Error(
        val message: UiText,
    ) : BackupExportState
}

sealed interface BackupImportState {
    data object Idle : BackupImportState

    data object Reading : BackupImportState

    /** Signed in: asks before the file picker opens, because the restore replaces the account's data everywhere. */
    data object AccountWarning : BackupImportState

    /**
     * A valid file is waiting for confirmation; [localHasData] means confirming replaces data and [account] that
     * the data is the signed-in account's.
     */
    data class Confirm(
        val counts: BackupCounts,
        val skippedCount: Int,
        val localHasData: Boolean,
        val account: Boolean = false,
    ) : BackupImportState

    /** [total] is 0 until the account restore has planned its operations (and for a guest restore). */
    data class Restoring(
        val written: Int = 0,
        val total: Int = 0,
    ) : BackupImportState

    /** An account restore stopped part-way; retrying finishes it without duplicating anything. */
    data class Partial(
        val written: Int,
        val total: Int,
    ) : BackupImportState

    data class Done(
        val counts: BackupCounts,
    ) : BackupImportState

    data class Error(
        val message: UiText,
    ) : BackupImportState
}
