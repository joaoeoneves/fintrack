package com.joaoeoneves.fintrack.ui.importexport

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.designsystem.DialogTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog

@Composable
fun MessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            DialogTextButton(text = stringResource(R.string.action_ok), onClick = onDismiss)
        },
    )
}
