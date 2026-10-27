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

    /** A valid file is waiting for confirmation; [localHasData] means confirming replaces data. */
    data class Confirm(
        val counts: BackupCounts,
        val skippedCount: Int,
        val localHasData: Boolean,
    ) : BackupImportState

    data object Restoring : BackupImportState

    data class Done(
        val counts: BackupCounts,
    ) : BackupImportState

    data class Error(
        val message: UiText,
    ) : BackupImportState
}
