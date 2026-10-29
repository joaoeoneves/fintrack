package com.joaoeoneves.fintrack.ui.migration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.backup.BackupCounts
import com.joaoeoneves.fintrack.ui.backup.countsText
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.DialogTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.ThinProgressBar
import com.joaoeoneves.fintrack.ui.importexport.MessageDialog
import com.joaoeoneves.fintrack.ui.theme.Dimens

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
    FinTrackAlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(stringResource(R.string.migration_error_title)) },
        text = { Text(state.message.asString()) },
        confirmButton = {
            DialogTextButton(text = stringResource(R.string.action_retry), onClick = actions.onRetry)
        },
        dismissButton = {
            DialogTextButton(text = stringResource(R.string.migration_action_not_now), onClick = actions.onDismiss)
        },
    )
}

@Composable
private fun OfferDialog(
    counts: BackupCounts,
    actions: MigrationActions,
) {
    FinTrackAlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(stringResource(R.string.migration_offer_title)) },
        text = {
            Text(
                stringResource(
                    R.string.migration_offer_message,
                    countsText(counts),
                ),
            )
        },
        confirmButton = {
            DialogTextButton(text = stringResource(R.string.migration_action_copy), onClick = actions.onAccept)
        },
        dismissButton = {
            DialogTextButton(text = stringResource(R.string.migration_action_not_now), onClick = actions.onDecline)
        },
    )
}

@Composable
private fun RunningDialog(state: MigrationUiState.Running) {
    FinTrackAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.migration_running_title)) },
        text = {
            Column {
                if (state.total > 0) {
                    Text(stringResource(R.string.migration_progress, state.done, state.total))
                    ThinProgressBar(
                        progress = state.done.toFloat() / state.total,
                        drawIn = false,
                        modifier = Modifier.padding(top = Dimens.Space12),
                    )
                } else {
                    Text(stringResource(R.string.migration_preparing))
                    ThinProgressBar(progress = 0f, drawIn = false, modifier = Modifier.padding(top = Dimens.Space12))
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
    FinTrackAlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(stringResource(R.string.migration_error_title)) },
        text = {
            Column {
                Text(pluralStringResource(R.plurals.migration_partial_progress, state.total, state.done, state.total))
                Text(state.message.asString(), modifier = Modifier.padding(top = Dimens.Space8))
            }
        },
        confirmButton = {
            DialogTextButton(text = stringResource(R.string.action_retry), onClick = actions.onRetry)
        },
        dismissButton = {
            DialogTextButton(text = stringResource(R.string.migration_action_not_now), onClick = actions.onDismiss)
        },
    )
}

@Composable
private fun DoneDialog(
    state: MigrationUiState.Done,
    actions: MigrationActions,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove) {
        FinTrackAlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.migration_remove_confirm_title)) },
            text = { Text(stringResource(R.string.migration_remove_confirm_message)) },
            confirmButton = {
                DestructiveTextButton(text = stringResource(R.string.action_delete), onClick = actions.onRemoveLocal)
            },
            dismissButton = {
                DialogTextButton(text = stringResource(R.string.action_cancel), onClick = { confirmRemove = false })
            },
        )
        return
    }
    FinTrackAlertDialog(
        onDismissRequest = actions.onKeepLocal,
        title = { Text(stringResource(R.string.migration_done_title)) },
        text = {
            Column {
                Text(pluralStringResource(R.plurals.migration_done_message, state.written, state.written))
                if (state.skipped > 0) {
                    Text(
                        pluralStringResource(R.plurals.migration_done_skipped, state.skipped, state.skipped),
                        modifier = Modifier.padding(top = Dimens.Space8),
                    )
                }
            }
        },
        confirmButton = {
            DialogTextButton(
                text = stringResource(R.string.migration_action_remove_local),
                onClick = { confirmRemove = true },
            )
        },
        dismissButton = {
            DialogTextButton(text = stringResource(R.string.migration_action_keep), onClick = actions.onKeepLocal)
        },
    )
}
