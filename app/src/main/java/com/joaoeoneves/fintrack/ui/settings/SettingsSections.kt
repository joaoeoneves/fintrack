package com.joaoeoneves.fintrack.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.symbol
import com.joaoeoneves.fintrack.ui.designsystem.SettingsChoiceRow
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** The preference groups (appearance, language, currency) shown on [SettingsScreen]. */
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
fun AppearanceSection(
    selected: ThemePreference,
    onSelected: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.settings_section_appearance), modifier = modifier) {
        ThemePreference.entries.forEach { preference ->
            SettingsChoiceRow(
                title = preference.label,
                selected = selected == preference,
                onClick = { onSelected(preference) },
            )
        }
    }
}

@Composable
fun LanguageSection(
    selected: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.settings_section_language), modifier = modifier) {
        AppLanguage.entries.forEach { language ->
            SettingsChoiceRow(
                title = language.label,
                selected = selected == language,
                onClick = { onSelected(language) },
            )
        }
    }
}

@Composable
fun CurrencySection(
    selected: CurrencyOption,
    onSelected: (CurrencyOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    SettingsGroup(label = stringResource(R.string.settings_section_currency), modifier = modifier) {
        Box {
            SettingsRow(
                title = stringResource(R.string.settings_currency_label),
                value = "${selected.displayName} (${selected.symbol})",
                onClick = { expanded = true },
                showChevron = true,
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = MaterialTheme.shapes.medium,
                containerColor = FinTrackTheme.colors.surface2,
            ) {
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
