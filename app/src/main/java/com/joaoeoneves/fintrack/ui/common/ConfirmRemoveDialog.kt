package com.joaoeoneves.fintrack.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.DialogTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog

/** A confirmation before removing something: [confirmLabel] is the destructive action, Cancel dismisses. */
@Composable
fun ConfirmRemoveDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { DestructiveTextButton(text = confirmLabel, onClick = onConfirm) },
        dismissButton = { DialogTextButton(text = stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}
