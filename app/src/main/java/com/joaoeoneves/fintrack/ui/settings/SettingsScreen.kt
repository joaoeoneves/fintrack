package com.joaoeoneves.fintrack.ui.settings

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.symbol
import com.joaoeoneves.fintrack.ui.importexport.ExportUiState
import com.joaoeoneves.fintrack.ui.importexport.ImportExportViewModel
import com.joaoeoneves.fintrack.ui.importexport.ImportPreviewDialog
import com.joaoeoneves.fintrack.ui.importexport.ImportUiState
import com.joaoeoneves.fintrack.ui.importexport.MessageDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    importExportViewModel: ImportExportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by importExportViewModel.importState.collectAsStateWithLifecycle()
    val exportState by importExportViewModel.exportState.collectAsStateWithLifecycle()

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(importExportViewModel::onImportFileSelected)
        }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let(importExportViewModel::onExportTargetSelected)
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
        ) {
            AppearanceSection(
                selected = uiState.themePreference,
                onSelected = viewModel::setThemePreference,
            )
            HorizontalDivider()
            CurrencySection(
                selected = uiState.currency,
                onSelected = viewModel::setCurrency,
            )
            HorizontalDivider()
            DataSection(
                onImportClick = {
                    importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
                },
                onExportClick = { exportLauncher.launch("fintrack-expenses.csv") },
            )
            HorizontalDivider()
            AccountSection(
                currentUser = uiState.currentUser,
                onSignOut = viewModel::signOut,
            )
            HorizontalDivider()
            AboutSection()
        }
    }

    when (val state = importState) {
        is ImportUiState.Preview -> {
            ImportPreviewDialog(
                state = state,
                onConfirm = importExportViewModel::onConfirmImport,
                onDismiss = importExportViewModel::onDismissImport,
            )
        }

        is ImportUiState.Done -> {
            MessageDialog(
                title = "Import complete",
                message = "Imported ${state.importedCount} expense(s).",
                onDismiss = importExportViewModel::onDismissImport,
            )
        }

        is ImportUiState.Error -> {
            MessageDialog(
                title = "Import failed",
                message = state.message,
                onDismiss = importExportViewModel::onDismissImport,
            )
        }

        ImportUiState.Idle, ImportUiState.Loading, ImportUiState.Importing -> Unit
    }

    when (val state = exportState) {
        is ExportUiState.Done -> {
            MessageDialog(
                title = "Export complete",
                message = "Exported ${state.exportedCount} expense(s).",
                onDismiss = importExportViewModel::onDismissExport,
            )
        }

        is ExportUiState.Error -> {
            MessageDialog(
                title = "Export failed",
                message = state.message,
                onDismiss = importExportViewModel::onDismissExport,
            )
        }

        ExportUiState.Idle, ExportUiState.Exporting -> Unit
    }

    uiState.signOutError?.let { message ->
        MessageDialog(
            title = "Sign-out failed",
            message = message,
            onDismiss = viewModel::dismissSignOutError,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private val ThemePreference.label: String
    get() =
        when (this) {
            ThemePreference.SYSTEM -> "System default"
            ThemePreference.LIGHT -> "Light"
            ThemePreference.DARK -> "Dark"
        }

@Composable
private fun AppearanceSection(
    selected: ThemePreference,
    onSelected: (ThemePreference) -> Unit,
) {
    Column {
        SectionHeader("Appearance")
        ThemePreference.entries.forEach { preference ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(preference) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected == preference,
                    onClick = { onSelected(preference) },
                )
                Text(text = preference.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencySection(
    selected: CurrencyOption,
    onSelected: (CurrencyOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        SectionHeader("Currency")
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            OutlinedTextField(
                readOnly = true,
                value = "${selected.displayName} (${selected.symbol})",
                onValueChange = {},
                label = { Text("Display currency") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CurrencyOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("${option.displayName} (${option.symbol})") },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DataSection(
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
) {
    Column {
        SectionHeader("Data")
        Text(
            text = "Import CSV",
            style = MaterialTheme.typography.bodyLarge,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onImportClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text = "Export CSV",
            style = MaterialTheme.typography.bodyLarge,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExportClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun AccountSection(
    currentUser: AuthUser?,
    onSignOut: () -> Unit,
) {
    Column {
        SectionHeader("Account")
        Text(
            text = currentUser?.displayName ?: currentUser?.email ?: "Signed in",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (currentUser?.email != null && currentUser.displayName != null) {
            Text(
                text = currentUser.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("Sign out")
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val versionName =
        remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            } catch (e: PackageManager.NameNotFoundException) {
                "unknown"
            }
        }

    Column {
        SectionHeader("About")
        Text(
            text = "FinTrack",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            text = "Version $versionName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
        )
    }
}
