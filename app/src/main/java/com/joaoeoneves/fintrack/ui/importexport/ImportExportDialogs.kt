package com.joaoeoneves.fintrack.ui.importexport

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.designsystem.DialogTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog

@Composable
fun ImportPreviewDialog(
    state: ImportUiState.Preview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_preview_title)) },
        text = {
            Column {
                Text(
                    pluralStringResource(
                        R.plurals.import_preview_summary,
                        state.validExpenses.size,
                        state.validExpenses.size,
                        state.failures.size,
                    ),
                )
                if (state.failures.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(state.failures) { failure ->
                            Text(
                                stringResource(
                                    R.string.import_preview_row_failure,
                                    failure.rowNumber,
                                    failure.reason.toUiText().asString(),
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogTextButton(
                text = stringResource(R.string.action_import),
                onClick = onConfirm,
                enabled = state.validExpenses.isNotEmpty(),
            )
        },
        dismissButton = {
            DialogTextButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
        },
    )
}

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
