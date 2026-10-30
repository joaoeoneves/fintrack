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
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.ui.backup.BackupFlow
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.importexport.ExportUiState
import com.joaoeoneves.fintrack.ui.importexport.ImportExportViewModel
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
    val exportState by importExportViewModel.exportState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                onExportClick = { exportLauncher.launch("fintrack-expenses.csv") },
                onSignOut = viewModel::signOut,
                onSignInToSync = { viewModel.signInToSync(context) },
                onLeaveGuestMode = viewModel::leaveGuestMode,
                onDataDeleted = onDataDeleted,
            ),
        modifier = modifier,
    )

    ImportExportDialogs(
        exportState = exportState,
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
            AccountCard(uiState = uiState, actions = actions)
            PreferencesSection(
                uiState = uiState,
                onThemeSelected = actions.onThemeSelected,
                onLanguageSelected = actions.onLanguageSelected,
            )
            StartChecklistSection()
            BackupFlow(signedIn = !uiState.isGuest) { onExport, onRestore ->
                DeleteAllDataFlow(
                    isGuest = uiState.isGuest,
                    onExportBackup = onExport,
                    onDeleted = actions.onDataDeleted,
                ) { onDeleteAll ->
                    DataSection(
                        actions =
                            DataSectionActions(
                                onExportBackup = onExport,
                                onRestoreBackup = onRestore,
                                onExportCsv = actions.onExportClick,
                                onDeleteAll = onDeleteAll,
                            ),
                    )
                }
            }
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
    exportState: ExportUiState,
    onDismissExport: () -> Unit,
) {
    ExportStateDialog(state = exportState, onDismiss = onDismissExport)
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
