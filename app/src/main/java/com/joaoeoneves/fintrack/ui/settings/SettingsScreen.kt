package com.joaoeoneves.fintrack.ui.settings

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.AppLanguage
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

    val sectionModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
                modifier = sectionModifier,
            )
            LanguageSection(
                selected = uiState.language,
                onSelected = viewModel::setLanguage,
                modifier = sectionModifier,
            )
            CurrencySection(
                selected = uiState.currency,
                onSelected = viewModel::setCurrency,
                modifier = sectionModifier,
            )
            DataSection(
                onImportClick = {
                    importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
                },
                onExportClick = { exportLauncher.launch("fintrack-expenses.csv") },
                modifier = sectionModifier,
            )
            AccountSection(
                currentUser = uiState.currentUser,
                onSignOut = viewModel::signOut,
                modifier = sectionModifier,
            )
            AboutSection(modifier = sectionModifier)
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
                title = stringResource(R.string.label_import_complete),
                message =
                    pluralStringResource(
                        R.plurals.import_complete_message,
                        state.importedCount,
                        state.importedCount,
                    ),
                onDismiss = importExportViewModel::onDismissImport,
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
                        state.message,
                    ),
                onDismiss = importExportViewModel::onDismissImport,
            )
        }

        is ImportUiState.Error -> {
            MessageDialog(
                title = stringResource(R.string.label_import_failed),
                message = state.message,
                onDismiss = importExportViewModel::onDismissImport,
            )
        }

        ImportUiState.Idle, ImportUiState.Loading, ImportUiState.Importing -> Unit
    }

    when (val state = exportState) {
        is ExportUiState.Done -> {
            MessageDialog(
                title = stringResource(R.string.label_export_complete),
                message =
                    pluralStringResource(
                        R.plurals.export_complete_message,
                        state.exportedCount,
                        state.exportedCount,
                    ),
                onDismiss = importExportViewModel::onDismissExport,
            )
        }

        is ExportUiState.Error -> {
            MessageDialog(
                title = stringResource(R.string.label_export_failed),
                message = state.message,
                onDismiss = importExportViewModel::onDismissExport,
            )
        }

        ExportUiState.Idle, ExportUiState.Exporting -> Unit
    }

    uiState.signOutError?.let { message ->
        MessageDialog(
            title = stringResource(R.string.label_sign_out_failed),
            message = message,
            onDismiss = viewModel::dismissSignOutError,
        )
    }
}

@Composable
private fun SectionHeader(
    text: String,
    icon: ImageVector,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private val ThemePreference.label: String
    @Composable
    get() =
        when (this) {
            ThemePreference.SYSTEM -> stringResource(R.string.theme_system)
            ThemePreference.LIGHT -> stringResource(R.string.theme_light)
            ThemePreference.DARK -> stringResource(R.string.theme_dark)
        }

/**
 * Human-readable label for an [AppLanguage]. [AppLanguage.PORTUGUESE]'s own label is always
 * rendered in Portuguese ("Português"), regardless of the currently active app language — the
 * same convention every language picker follows for its non-default entries.
 */
private val AppLanguage.label: String
    @Composable
    get() =
        when (this) {
            AppLanguage.SYSTEM -> stringResource(R.string.theme_system)
            AppLanguage.ENGLISH -> stringResource(R.string.language_english)
            AppLanguage.PORTUGUESE -> stringResource(R.string.language_portuguese)
        }

@Composable
private fun AppearanceSection(
    selected: ThemePreference,
    onSelected: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            SectionHeader(stringResource(R.string.settings_section_appearance), Icons.Filled.Palette)
            ThemePreference.entries.forEach { preference ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(preference) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
}

@Composable
private fun LanguageSection(
    selected: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            SectionHeader(stringResource(R.string.settings_section_language), Icons.Filled.Language)
            AppLanguage.entries.forEach { language ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(language) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected == language,
                        onClick = { onSelected(language) },
                    )
                    Text(text = language.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencySection(
    selected: CurrencyOption,
    onSelected: (CurrencyOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            SectionHeader(stringResource(R.string.settings_section_currency), Icons.Filled.AttachMoney)
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = "${selected.displayName} (${selected.symbol})",
                    onValueChange = {},
                    label = { Text(stringResource(R.string.settings_currency_label)) },
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
}

@Composable
private fun DataSection(
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            SectionHeader(stringResource(R.string.settings_section_data), Icons.Filled.ImportExport)
            Text(
                text = stringResource(R.string.settings_import_csv),
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onImportClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Text(
                text = stringResource(R.string.settings_export_csv),
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onExportClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun AccountSection(
    currentUser: AuthUser?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            SectionHeader(stringResource(R.string.settings_section_account), Icons.Filled.Person)
            val signedInFallback = stringResource(R.string.settings_signed_in_fallback)
            Text(
                text = currentUser?.displayName ?: currentUser?.email ?: signedInFallback,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
                Text(stringResource(R.string.action_sign_out))
            }
        }
    }
}

@Composable
private fun AboutSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val unknownVersion = stringResource(R.string.about_version_unknown)
    val versionName =
        remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: unknownVersion
            } catch (e: PackageManager.NameNotFoundException) {
                unknownVersion
            }
        }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            SectionHeader(stringResource(R.string.settings_section_about), Icons.Filled.Info)
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text = stringResource(R.string.about_version, versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp),
            )
        }
    }
}
