package com.joaoeoneves.fintrack.ui.backup

/** What the backup dialogs can trigger. */
class BackupDialogActions(
    val onConfirmImport: () -> Unit,
    val onDismissImport: () -> Unit,
    val onDismissExport: () -> Unit,
    val onExportFirst: () -> Unit,
    val onContinueFromWarning: () -> Unit,
    val onRetryRestore: () -> Unit,
)
