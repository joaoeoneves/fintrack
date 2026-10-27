package com.joaoeoneves.fintrack.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Owns the backup SAF launchers and result dialogs. Renders [content] with the two click handlers
 * (export, import) so the caller only places the buttons.
 */
@Composable
fun BackupFlow(
    viewModel: BackupViewModel = hiltViewModel(),
    content: @Composable (onExportClick: () -> Unit, onImportClick: () -> Unit) -> Unit,
) {
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let(viewModel::onExportTargetSelected)
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::onImportFileSelected)
        }

    content(
        { exportLauncher.launch(viewModel.suggestedFileName()) },
        { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
    )

    BackupDialogs(
        exportState = exportState,
        importState = importState,
        onConfirmImport = viewModel::onConfirmImport,
        onDismissImport = viewModel::onDismissImport,
        onDismissExport = viewModel::onDismissExport,
    )
}
