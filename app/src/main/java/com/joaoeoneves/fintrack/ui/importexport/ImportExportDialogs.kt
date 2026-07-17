package com.joaoeoneves.fintrack.ui.importexport

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R

@Composable
fun ImportPreviewDialog(
    state: ImportUiState.Preview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_preview_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.import_preview_summary,
                        state.validExpenses.size,
                        state.failures.size,
                    ),
                )
                if (state.failures.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(state.failures) { failure ->
                            Text(stringResource(R.string.import_preview_row_failure, failure.rowNumber, failure.reason))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = state.validExpenses.isNotEmpty(),
            ) {
                Text(stringResource(R.string.action_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun MessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        },
    )
}
