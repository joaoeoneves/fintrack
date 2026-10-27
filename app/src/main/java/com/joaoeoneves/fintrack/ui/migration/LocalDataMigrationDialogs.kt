package com.joaoeoneves.fintrack.ui.migration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.backup.BackupCounts
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.importexport.MessageDialog

/** Shows the migration flow's dialogs for the signed-in session; renders nothing when hidden. */
@Composable
fun LocalDataMigrationHost(viewModel: LocalDataMigrationViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions =
        MigrationActions(
            onAccept = viewModel::onAccept,
            onDecline = viewModel::onDecline,
            onRetry = viewModel::onRetry,
            onRemoveLocal = viewModel::onRemoveLocal,
            onKeepLocal = viewModel::onKeepLocal,
            onDismiss = viewModel::onDismiss,
        )
    LocalDataMigrationDialogs(state, actions)
}

@Composable
fun LocalDataMigrationDialogs(
    state: MigrationUiState,
    actions: MigrationActions,
) {
    when (state) {
        MigrationUiState.Hidden -> Unit
        is MigrationUiState.Offer -> OfferDialog(state.counts, actions)
        is MigrationUiState.Running -> RunningDialog(state)
        is MigrationUiState.PartialFailure -> PartialFailureDialog(state, actions)
        is MigrationUiState.Done -> DoneDialog(state, actions)
        is MigrationUiState.Error -> ErrorDialog(state, actions)
    }
}

@Composable
private fun ErrorDialog(
    state: MigrationUiState.Error,
    actions: MigrationActions,
) {
    if (!state.retryable) {
        MessageDialog(
            title = stringResource(R.string.migration_error_title),
            message = state.message.asString(),
            onDismiss = actions.onDismiss,
        )
        return
    }
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(stringResource(R.string.migration_error_title)) },
        text = { Text(state.message.asString()) },
        confirmButton = {
            TextButton(onClick = actions.onRetry) { Text(stringResource(R.string.action_retry)) }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.migration_action_not_now)) }
        },
    )
}

@Composable
private fun OfferDialog(
    counts: BackupCounts,
    actions: MigrationActions,
) {
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(stringResource(R.string.migration_offer_title)) },
        text = {
            Text(
                stringResource(
                    R.string.migration_offer_message,
                    stringResource(
                        R.string.backup_counts,
                        counts.expenses,
                        counts.income,
                        counts.budgets,
                        counts.categories,
                    ),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = actions.onAccept) { Text(stringResource(R.string.migration_action_copy)) }
        },
        dismissButton = {
            TextButton(onClick = actions.onDecline) { Text(stringResource(R.string.migration_action_not_now)) }
        },
    )
}

@Composable
private fun RunningDialog(state: MigrationUiState.Running) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.migration_running_title)) },
        text = {
            Column {
                if (state.total > 0) {
                    Text(stringResource(R.string.migration_progress, state.done, state.total))
                    LinearProgressIndicator(
                        progress = { state.done.toFloat() / state.total },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                } else {
                    Text(stringResource(R.string.migration_preparing))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun PartialFailureDialog(
    state: MigrationUiState.PartialFailure,
    actions: MigrationActions,
) {
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(stringResource(R.string.migration_error_title)) },
        text = {
            Column {
                Text(pluralStringResource(R.plurals.migration_partial_progress, state.total, state.done, state.total))
                Text(state.message.asString(), modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onRetry) { Text(stringResource(R.string.action_retry)) }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.migration_action_not_now)) }
        },
    )
}

@Composable
private fun DoneDialog(
    state: MigrationUiState.Done,
    actions: MigrationActions,
) {
    AlertDialog(
        onDismissRequest = actions.onKeepLocal,
        title = { Text(stringResource(R.string.migration_done_title)) },
        text = {
            Column {
                Text(pluralStringResource(R.plurals.migration_done_message, state.written, state.written))
                if (state.skipped > 0) {
                    Text(
                        pluralStringResource(R.plurals.migration_done_skipped, state.skipped, state.skipped),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onRemoveLocal) { Text(stringResource(R.string.migration_action_remove_local)) }
        },
        dismissButton = {
            TextButton(onClick = actions.onKeepLocal) { Text(stringResource(R.string.migration_action_keep)) }
        },
    )
}
