package com.joaoeoneves.fintrack.ui.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.backup.BackupCounts
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.importexport.MessageDialog

/** Dialogs for the backup export and restore flows; renders nothing while both are idle or busy. */
@Composable
fun BackupDialogs(
    exportState: BackupExportState,
    importState: BackupImportState,
    onConfirmImport: () -> Unit,
    onDismissImport: () -> Unit,
    onDismissExport: () -> Unit,
) {
    when (exportState) {
        is BackupExportState.Done ->
            MessageDialog(
                title = stringResource(R.string.backup_export_done_title),
                message = stringResource(R.string.backup_export_done_message, countsText(exportState.counts)),
                onDismiss = onDismissExport,
            )

        is BackupExportState.Error ->
            MessageDialog(
                title = stringResource(R.string.backup_export_failed_title),
                message = exportState.message.asString(),
                onDismiss = onDismissExport,
            )

        BackupExportState.Idle, BackupExportState.Exporting -> Unit
    }
    when (importState) {
        is BackupImportState.Confirm ->
            RestoreConfirmDialog(state = importState, onConfirm = onConfirmImport, onDismiss = onDismissImport)

        is BackupImportState.Done ->
            MessageDialog(
                title = stringResource(R.string.backup_restore_done_title),
                message = stringResource(R.string.backup_restore_done_message, countsText(importState.counts)),
                onDismiss = onDismissImport,
            )

        is BackupImportState.Error ->
            MessageDialog(
                title = stringResource(R.string.backup_restore_failed_title),
                message = importState.message.asString(),
                onDismiss = onDismissImport,
            )

        BackupImportState.Idle, BackupImportState.Reading, BackupImportState.Restoring -> Unit
    }
}

@Composable
private fun countsText(counts: BackupCounts): String =
    stringResource(
        R.string.backup_counts,
        counts.expenses,
        counts.income,
        counts.budgets,
        counts.categories,
        counts.plannedIncome,
        counts.recurring,
    )

@Composable
private fun RestoreConfirmDialog(
    state: BackupImportState.Confirm,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.backup_restore_confirm_message, countsText(state.counts)))
                if (state.skippedCount > 0) {
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.backup_restore_skipped,
                                state.skippedCount,
                                state.skippedCount,
                            ),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (state.localHasData) {
                    Text(
                        text = stringResource(R.string.backup_restore_replace_warning),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_restore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
