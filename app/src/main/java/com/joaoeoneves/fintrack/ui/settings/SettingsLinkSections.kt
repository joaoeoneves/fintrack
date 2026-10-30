package com.joaoeoneves.fintrack.ui.settings

import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackBottomSheet
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Backup, restore, CSV export and the wipe in one group. */
@Composable
fun DataSection(
    actions: DataSectionActions,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.settings_section_data), modifier = modifier) {
        SettingsRow(title = stringResource(R.string.settings_export_backup), onClick = actions.onExportBackup)
        Hairline()
        SettingsRow(title = stringResource(R.string.settings_restore_backup), onClick = actions.onRestoreBackup)
        Hairline()
        SettingsRow(title = stringResource(R.string.settings_export_csv), onClick = actions.onExportCsv)
        Hairline()
        SettingsRow(
            title = stringResource(R.string.settings_delete_all_data),
            onClick = actions.onDeleteAll,
            titleColor = FinTrackTheme.colors.negative,
        )
    }
}

@Composable
fun AboutSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val unknownVersion = stringResource(R.string.about_version_unknown)
    val versionName =
        remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: unknownVersion
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w("SettingsScreen", "Could not resolve app version name", e)
                unknownVersion
            }
        }
    var showLicense by remember { mutableStateOf(false) }

    SettingsGroup(label = stringResource(R.string.settings_section_about), modifier = modifier) {
        SettingsRow(
            title = stringResource(R.string.app_name),
            value = stringResource(R.string.about_version, versionName),
        )
        Hairline()
        SettingsRow(
            title = stringResource(R.string.about_font_license),
            onClick = { showLicense = true },
            showChevron = true,
        )
    }

    if (showLicense) {
        FontLicenseSheet(onDismiss = { showLicense = false })
    }
}

@Composable
private fun FontLicenseSheet(onDismiss: () -> Unit) {
    val resources = LocalResources.current
    // Read off the main thread; only runs while the sheet is shown. Empty until loaded.
    val licenseText by produceState(initialValue = "", resources) {
        value =
            withContext(Dispatchers.IO) {
                resources.openRawResource(R.raw.inter_ofl).use { it.readBytes().decodeToString() }
            }
    }
    FinTrackBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.about_font_license),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Dimens.Space12),
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = licenseText,
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.textSecondary,
            )
        }
    }
}
