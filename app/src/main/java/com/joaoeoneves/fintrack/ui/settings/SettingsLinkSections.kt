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

/** The navigation/action groups (categories, recurring, data, about) shown on [SettingsScreen]. */
@Composable
fun CategoriesSection(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.settings_section_categories), modifier = modifier) {
        SettingsRow(
            title = stringResource(R.string.settings_manage_categories),
            onClick = onOpen,
            showChevron = true,
        )
    }
}

@Composable
fun RecurringSection(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.settings_section_recurring), modifier = modifier) {
        SettingsRow(
            title = stringResource(R.string.settings_manage_recurring),
            onClick = onOpen,
            showChevron = true,
        )
    }
}

@Composable
fun DataSection(
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.settings_section_data), modifier = modifier) {
        SettingsRow(title = stringResource(R.string.settings_import_csv), onClick = onImportClick)
        Hairline()
        SettingsRow(title = stringResource(R.string.settings_export_csv), onClick = onExportClick)
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
    val licenseText =
        remember(resources) {
            resources.openRawResource(R.raw.inter_ofl).use { stream ->
                stream.readBytes().decodeToString()
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
