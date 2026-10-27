package com.joaoeoneves.fintrack.ui.settings.categories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog

/** What a delete dialog's confirm button says and does; `null` confirm means no such button. */
private data class ConfirmAction(
    val label: Int,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

/**
 * The delete flow's single dialog, switching on [state]: checking usage, confirming, or (when the
 * category is in use / usage can't be verified) offering to archive it instead.
 */
@Composable
internal fun CategoryDeleteDialog(
    state: DeleteDialogState,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = state.category.displayName
    val archived = state.category.archived
    when (state) {
        is DeleteDialogState.Checking -> {
            DeleteAlert(name, null, onDismiss, R.string.action_cancel) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }

        is DeleteDialogState.ConfirmDelete -> {
            DeleteAlert(
                title = stringResource(R.string.category_delete_confirm, name),
                confirm = ConfirmAction(R.string.category_action_delete, onDelete, destructive = true),
                onDismiss = onDismiss,
                dismissLabel = R.string.action_cancel,
            ) {}
        }

        is DeleteDialogState.InUse -> {
            val message = if (archived) R.string.category_in_use_archived_message else R.string.category_in_use_message
            BlockedAlert(name, stringResource(message, name), archived, onArchive, onDismiss)
        }

        is DeleteDialogState.CannotVerify -> {
            val message =
                if (archived) {
                    R.string.category_cannot_verify_archived_message
                } else {
                    R.string.category_cannot_verify_message
                }
            BlockedAlert(name, stringResource(message), archived, onArchive, onDismiss)
        }
    }
}

/** Deletion isn't possible: an active category offers "Archive", an archived one just "OK". */
@Composable
private fun BlockedAlert(
    name: String,
    message: String,
    archived: Boolean,
    onArchive: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (archived) {
        DeleteAlert(name, ConfirmAction(R.string.action_ok, onDismiss), onDismiss, null) { Text(message) }
    } else {
        DeleteAlert(
            title = name,
            confirm = ConfirmAction(R.string.category_action_archive, onArchive),
            onDismiss = onDismiss,
            dismissLabel = R.string.action_cancel,
        ) { Text(message) }
    }
}

@Composable
private fun DeleteAlert(
    title: String,
    confirm: ConfirmAction?,
    onDismiss: () -> Unit,
    dismissLabel: Int?,
    body: @Composable () -> Unit,
) {
    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = body,
        confirmButton = {
            confirm?.let {
                if (it.destructive) {
                    DestructiveTextButton(text = stringResource(it.label), onClick = it.onClick)
                } else {
                    TextButton(onClick = it.onClick) { Text(stringResource(it.label)) }
                }
            }
        },
        dismissButton = {
            dismissLabel?.let { TextButton(onClick = onDismiss) { Text(stringResource(it)) } }
        },
    )
}
