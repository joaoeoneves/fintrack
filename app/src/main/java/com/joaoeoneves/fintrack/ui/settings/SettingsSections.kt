package com.joaoeoneves.fintrack.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.symbol
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** The preferences group (theme, language, currency) shown on [SettingsScreen]. */
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
            AppLanguage.SYSTEM -> stringResource(R.string.language_system)
            AppLanguage.ENGLISH -> stringResource(R.string.language_english)
            AppLanguage.PORTUGUESE -> stringResource(R.string.language_portuguese)
        }

/** Theme, language and display currency as three rows, each opening a menu of its options. */
@Composable
fun PreferencesSection(
    uiState: SettingsUiState,
    onThemeSelected: (ThemePreference) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onCurrencySelected: (CurrencyOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    SettingsGroup(label = stringResource(R.string.settings_section_preferences), modifier = modifier) {
        SettingsPickerRow(
            title = stringResource(R.string.settings_theme),
            selected = uiState.themePreference,
            options = ThemePreference.entries,
            optionLabel = { it.label },
            onSelected = onThemeSelected,
        )
        SettingsPickerRow(
            title = stringResource(R.string.settings_section_language),
            selected = uiState.language,
            options = AppLanguage.entries,
            optionLabel = { it.label },
            onSelected = onLanguageSelected,
        )
        SettingsPickerRow(
            title = stringResource(R.string.settings_section_currency),
            selected = uiState.currency,
            options = CurrencyOption.entries,
            optionLabel = { "${it.displayName} (${it.symbol(locale)})" },
            onSelected = onCurrencySelected,
            supportingText = stringResource(R.string.settings_currency_hint),
        )
    }
}

/** A row showing the [selected] option's label that opens an anchored menu of [options], the current one checked. */
@Suppress("LongParameterList") // title/current/options/label/callback/hint are independent
@Composable
private fun <T> SettingsPickerRow(
    title: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    supportingText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingsRow(
            title = title,
            value = optionLabel(selected),
            onClick = { expanded = true },
            showChevron = true,
            supportingText = supportingText,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = FinTrackTheme.colors.surface2,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    leadingIcon = if (option == selected) ({ CheckMark() }) else null,
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CheckMark() {
    Icon(Icons.Default.Check, contentDescription = null, tint = FinTrackTheme.colors.accentText)
}
