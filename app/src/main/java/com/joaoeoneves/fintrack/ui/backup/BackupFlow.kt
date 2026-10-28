package com.joaoeoneves.fintrack.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Owns the backup SAF launchers and result dialogs. Renders [content] with the two click handlers
 * (export, import) so the caller only places the buttons. [signedIn] makes the restore warn before the picker opens.
 */
@Composable
fun BackupFlow(
    signedIn: Boolean,
    viewModel: BackupViewModel = hiltViewModel(),
    content: @Composable (onExportClick: () -> Unit, onImportClick: () -> Unit) -> Unit,
) {
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) viewModel.onExportTargetSelected(uri) else viewModel.onExportCancelled()
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::onImportFileSelected)
        }

    LaunchedEffect(viewModel) {
        viewModel.pickFile.collect {
            importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
        }
    }

    content(
        { exportLauncher.launch(viewModel.suggestedFileName()) },
        { viewModel.onRestoreRequested(signedIn) },
    )

    BackupDialogs(
        exportState = exportState,
        importState = importState,
        actions =
            BackupDialogActions(
                onConfirmImport = viewModel::onConfirmImport,
                onDismissImport = viewModel::onDismissImport,
                onDismissExport = viewModel::onDismissExport,
                onExportFirst = {
                    viewModel.onExportFirst()
                    exportLauncher.launch(viewModel.suggestedFileName())
                },
                onContinueFromWarning = viewModel::onContinueFromWarning,
                onRetryRestore = viewModel::onRetryRestore,
            ),
    )
}
