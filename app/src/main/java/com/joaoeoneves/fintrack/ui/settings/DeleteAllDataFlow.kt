package com.joaoeoneves.fintrack.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.LocalAppSnackbar
import com.joaoeoneves.fintrack.ui.theme.Dimens

private val SpinnerSize = 24.dp

/**
 * Owns the "Delete all data" dialogs and the success hand-off (snackbar, then [onDeleted]). Renders [content]
 * with the click handler for the settings row; [onExportBackup] is the "Export backup" shortcut in the confirmation.
 */
@Composable
fun DeleteAllDataFlow(
    isGuest: Boolean,
    onExportBackup: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: DeleteAllDataViewModel = hiltViewModel(),
    content: @Composable (onDeleteClick: () -> Unit) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showMessage = LocalAppSnackbar.current
    val doneMessage = stringResource(R.string.delete_all_data_done)
    LaunchedEffect(viewModel) {
        viewModel.deleted.collect {
            showMessage(doneMessage)
            onDeleted()
        }
    }

    content(viewModel::onRequestDelete)

    when (val current = state) {
        DeleteAllDataState.Idle -> Unit

        DeleteAllDataState.Confirming ->
            ConfirmDeleteDialog(
                isGuest = isGuest,
                onExportBackup = onExportBackup,
                onConfirm = viewModel::onConfirmDelete,
                onDismiss = viewModel::onDismiss,
            )

        DeleteAllDataState.Deleting -> DeletingDialog()

        is DeleteAllDataState.Failed ->
            FinTrackAlertDialog(
                onDismissRequest = viewModel::onDismiss,
                title = { Text(stringResource(R.string.delete_all_data_error_title)) },
                text = { Text(current.message.asString()) },
                confirmButton = {
                    DestructiveTextButton(
                        text = stringResource(R.string.action_retry),
                        onClick = viewModel::onConfirmDelete,
                    )
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onDismiss) { Text(stringResource(R.string.action_cancel)) }
                },
            )
    }
}

@Composable
private fun ConfirmDeleteDialog(
    isGuest: Boolean,
    onExportBackup: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_all_data_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        if (isGuest) {
                            R.string.delete_all_data_message_device
                        } else {
                            R.string.delete_all_data_message_account
                        },
                    ),
                )
                TextButton(onClick = onExportBackup) { Text(stringResource(R.string.delete_all_data_export_backup)) }
            }
        },
        confirmButton = {
            DestructiveTextButton(text = stringResource(R.string.delete_all_data_confirm), onClick = onConfirm)
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DeletingDialog() {
    FinTrackAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(SpinnerSize), strokeWidth = 2.dp)
                Text(stringResource(R.string.delete_all_data_progress))
            }
        },
        confirmButton = {},
    )
}
