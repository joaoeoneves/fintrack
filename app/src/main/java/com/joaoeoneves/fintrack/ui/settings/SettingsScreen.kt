package com.joaoeoneves.fintrack.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.ui.backup.BackupFlow
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.importexport.ExportUiState
import com.joaoeoneves.fintrack.ui.importexport.ImportExportViewModel
import com.joaoeoneves.fintrack.ui.importexport.ImportPreviewDialog
import com.joaoeoneves.fintrack.ui.importexport.ImportUiState
import com.joaoeoneves.fintrack.ui.importexport.MessageDialog
import com.joaoeoneves.fintrack.ui.theme.Dimens

@Suppress("LongParameterList") // one callback per navigation target plus the injected ViewModel
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDataDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val importExportViewModel: ImportExportViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by importExportViewModel.importState.collectAsStateWithLifecycle()
    val exportState by importExportViewModel.exportState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(importExportViewModel::onImportFileSelected)
        }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let(importExportViewModel::onExportTargetSelected)
        }
    SettingsScaffold(
        onBack = onBack,
        uiState = uiState,
        actions =
            SettingsActions(
                onThemeSelected = viewModel::setThemePreference,
                onLanguageSelected = viewModel::setLanguage,
                onCurrencySelected = viewModel::setCurrency,
                onImportClick = {
                    importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
                },
                onExportClick = { exportLauncher.launch("fintrack-expenses.csv") },
                onSignOut = viewModel::signOut,
                onSignInToSync = { viewModel.signInToSync(context) },
                onLeaveGuestMode = viewModel::leaveGuestMode,
                onDataDeleted = onDataDeleted,
            ),
        modifier = modifier,
    )

    ImportExportDialogs(
        importState = importState,
        exportState = exportState,
        onConfirmImport = importExportViewModel::onConfirmImport,
        onDismissImport = importExportViewModel::onDismissImport,
        onDismissExport = importExportViewModel::onDismissExport,
    )

    uiState.signOutError?.let { message ->
        MessageDialog(
            title = stringResource(R.string.label_sign_out_failed),
            message = message.asString(),
            onDismiss = viewModel::dismissSignOutError,
        )
    }

    uiState.signInError?.let { message ->
        MessageDialog(
            title = stringResource(R.string.label_sign_in_failed),
            message = message.asString(),
            onDismiss = viewModel::dismissSignInError,
        )
    }
}

/** Bundles [SettingsScaffold]'s section callbacks to keep its own parameter list short. */
private data class SettingsActions(
    val onThemeSelected: (ThemePreference) -> Unit,
    val onLanguageSelected: (AppLanguage) -> Unit,
    val onCurrencySelected: (CurrencyOption) -> Unit,
    val onImportClick: () -> Unit,
    val onExportClick: () -> Unit,
    val onSignOut: () -> Unit,
    val onSignInToSync: () -> Unit,
    val onLeaveGuestMode: () -> Unit,
    val onDataDeleted: () -> Unit,
)

@Composable
private fun SettingsScaffold(
    onBack: () -> Unit,
    uiState: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    LargeTitleScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.ScreenPadding)
                    .padding(bottom = Dimens.Space32),
        ) {
            PreferencesSection(
                uiState = uiState,
                onThemeSelected = actions.onThemeSelected,
                onLanguageSelected = actions.onLanguageSelected,
                onCurrencySelected = actions.onCurrencySelected,
            )
            AutomationSection()
            DataSection(onImportClick = actions.onImportClick, onExportClick = actions.onExportClick)
            BackupFlow { onExport, onRestore ->
                DeleteAllDataFlow(
                    isGuest = uiState.isGuest,
                    onExportBackup = onExport,
                    onDeleted = actions.onDataDeleted,
                ) { onDeleteAll ->
                    BackupSection(
                        isGuest = uiState.isGuest,
                        onExportClick = onExport,
                        onRestoreClick = onRestore,
                        onDeleteAllClick = onDeleteAll,
                    )
                }
            }
            AccountCard(uiState = uiState, actions = actions)
            AboutSection()
        }
    }
}

@Composable
private fun AccountCard(
    uiState: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    if (uiState.isGuest) {
        GuestAccountSection(
            onSignInToSync = actions.onSignInToSync,
            onLeaveGuestMode = actions.onLeaveGuestMode,
            modifier = modifier,
        )
    } else {
        AccountSection(currentUser = uiState.currentUser, onSignOut = actions.onSignOut, modifier = modifier)
    }
}

@Composable
private fun ImportExportDialogs(
    importState: ImportUiState,
    exportState: ExportUiState,
    onConfirmImport: () -> Unit,
    onDismissImport: () -> Unit,
    onDismissExport: () -> Unit,
) {
    ImportStateDialog(state = importState, onConfirm = onConfirmImport, onDismiss = onDismissImport)
    ExportStateDialog(state = exportState, onDismiss = onDismissExport)
}

@Composable
private fun ImportStateDialog(
    state: ImportUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is ImportUiState.Preview -> {
            ImportPreviewDialog(state = state, onConfirm = onConfirm, onDismiss = onDismiss)
        }

        is ImportUiState.Done -> {
            MessageDialog(
                title = stringResource(R.string.label_import_complete),
                message =
                    pluralStringResource(
                        R.plurals.import_complete_message,
                        state.importedCount,
                        state.importedCount,
                    ),
                onDismiss = onDismiss,
            )
        }

        is ImportUiState.PartialFailure -> {
            MessageDialog(
                title = stringResource(R.string.label_import_partial_failure),
                message =
                    pluralStringResource(
                        R.plurals.import_partial_failure_message,
                        state.succeededCount,
                        state.succeededCount,
                        state.message.asString(),
                    ),
                onDismiss = onDismiss,
            )
        }

        is ImportUiState.Error -> {
            MessageDialog(
                title = stringResource(R.string.label_import_failed),
                message = state.message.asString(),
                onDismiss = onDismiss,
            )
        }

        ImportUiState.Idle, ImportUiState.Loading, ImportUiState.Importing -> Unit
    }
}

@Composable
private fun ExportStateDialog(
    state: ExportUiState,
    onDismiss: () -> Unit,
) {
    when (state) {
        is ExportUiState.Done -> {
            MessageDialog(
                title = stringResource(R.string.label_export_complete),
                message =
                    pluralStringResource(
                        R.plurals.export_complete_message,
                        state.exportedCount,
                        state.exportedCount,
                    ),
                onDismiss = onDismiss,
            )
        }

        is ExportUiState.Error -> {
            MessageDialog(
                title = stringResource(R.string.label_export_failed),
                message = state.message.asString(),
                onDismiss = onDismiss,
            )
        }

        ExportUiState.Idle, ExportUiState.Exporting -> Unit
    }
}
