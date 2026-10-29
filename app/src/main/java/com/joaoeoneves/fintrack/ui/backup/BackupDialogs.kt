package com.joaoeoneves.fintrack.ui.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.backup.BackupCounts
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.DialogTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.ThinProgressBar
import com.joaoeoneves.fintrack.ui.importexport.MessageDialog
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** Dialogs for the backup export and restore flows; renders nothing while both are idle or busy. */
@Composable
fun BackupDialogs(
    exportState: BackupExportState,
    importState: BackupImportState,
    actions: BackupDialogActions,
) {
    when (exportState) {
        is BackupExportState.Done ->
            MessageDialog(
                title = stringResource(R.string.backup_export_done_title),
                message = stringResource(R.string.backup_export_done_message, countsText(exportState.counts)),
                onDismiss = actions.onDismissExport,
            )

        is BackupExportState.Error ->
            MessageDialog(
                title = stringResource(R.string.backup_export_failed_title),
                message = exportState.message.asString(),
                onDismiss = actions.onDismissExport,
            )

        BackupExportState.Idle, BackupExportState.Exporting -> Unit
    }
    when (importState) {
        BackupImportState.AccountWarning -> AccountRestoreWarningDialog(actions)

        is BackupImportState.Confirm ->
            RestoreConfirmDialog(
                state = importState,
                onConfirm = actions.onConfirmImport,
                onDismiss = actions.onDismissImport,
            )

        is BackupImportState.Restoring -> RestoringDialog(importState)

        is BackupImportState.Partial -> PartialRestoreDialog(actions)

        is BackupImportState.Done ->
            MessageDialog(
                title = stringResource(R.string.backup_restore_done_title),
                message = stringResource(R.string.backup_restore_done_message, countsText(importState.counts)),
                onDismiss = actions.onDismissImport,
            )

        is BackupImportState.Error ->
            MessageDialog(
                title = stringResource(R.string.backup_restore_failed_title),
                message = importState.message.asString(),
                onDismiss = actions.onDismissImport,
            )

        BackupImportState.Idle, BackupImportState.Reading -> Unit
    }
}

/** The per-kind record counts as the multi-line summary shared by the backup and migration dialogs. */
@Composable
internal fun countsText(counts: BackupCounts): String =
    stringResource(
        R.string.backup_counts,
        counts.expenses,
        counts.income,
        counts.categoryGroups,
        counts.categories,
        counts.plannedIncome,
        counts.recurring,
        counts.goals,
        stringResource(if (counts.salaryProfile > 0) R.string.backup_salary_yes else R.string.backup_salary_no),
    )

@Composable
private fun RestoreConfirmDialog(
    state: BackupImportState.Confirm,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FinTrackAlertDialog(
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
                        modifier = Modifier.padding(top = Dimens.Space8),
                    )
                }
                if (state.localHasData) {
                    Text(
                        text =
                            stringResource(
                                if (state.account) {
                                    R.string.backup_restore_account_replace_warning
                                } else {
                                    R.string.backup_restore_replace_warning
                                },
                            ),
                        color = FinTrackTheme.colors.negative,
                        modifier = Modifier.padding(top = Dimens.Space8),
                    )
                }
            }
        },
        confirmButton = {
            // Restoring replaces what is on this device (or in the account), so it is destructive either way.
            DestructiveTextButton(
                text = stringResource(if (state.account) R.string.backup_action_replace else R.string.action_restore),
                onClick = onConfirm,
            )
        },
        dismissButton = {
            DialogTextButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
        },
    )
}

@Composable
private fun AccountRestoreWarningDialog(actions: BackupDialogActions) {
    FinTrackAlertDialog(
        onDismissRequest = actions.onDismissImport,
        title = { Text(stringResource(R.string.backup_account_warning_title)) },
        text = { Text(stringResource(R.string.backup_account_warning_message)) },
        confirmButton = {
            Row {
                DialogTextButton(
                    text = stringResource(R.string.backup_action_export_first),
                    onClick = actions.onExportFirst,
                )
                DestructiveTextButton(
                    text = stringResource(R.string.backup_action_continue),
                    onClick = actions.onContinueFromWarning,
                )
            }
        },
        dismissButton = {
            DialogTextButton(text = stringResource(R.string.action_cancel), onClick = actions.onDismissImport)
        },
    )
}

@Composable
private fun RestoringDialog(state: BackupImportState.Restoring) {
    FinTrackAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.backup_restoring_title)) },
        text = {
            Column {
                if (state.total > 0) {
                    Text(stringResource(R.string.backup_restoring_progress, state.written, state.total))
                    ThinProgressBar(
                        progress = state.written.toFloat() / state.total,
                        drawIn = false,
                        modifier = Modifier.padding(top = Dimens.Space12),
                    )
                } else {
                    Text(stringResource(R.string.backup_restoring_preparing))
                    ThinProgressBar(progress = 0f, drawIn = false, modifier = Modifier.padding(top = Dimens.Space12))
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun PartialRestoreDialog(actions: BackupDialogActions) {
    FinTrackAlertDialog(
        onDismissRequest = actions.onDismissImport,
        title = { Text(stringResource(R.string.backup_restore_partial_title)) },
        text = { Text(stringResource(R.string.backup_restore_partial_message)) },
        confirmButton = {
            DialogTextButton(text = stringResource(R.string.action_retry), onClick = actions.onRetryRestore)
        },
        dismissButton = {
            DialogTextButton(text = stringResource(R.string.backup_action_close), onClick = actions.onDismissImport)
        },
    )
}
