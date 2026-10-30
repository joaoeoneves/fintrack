package com.joaoeoneves.fintrack.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackDropdownMenu
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackMenuItem
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow

/** The preferences group (theme, language) shown on [SettingsScreen]. */
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

/** Theme and language as rows, each opening a menu of its options. */
@Composable
fun PreferencesSection(
    uiState: SettingsUiState,
    onThemeSelected: (ThemePreference) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.settings_section_preferences), modifier = modifier) {
        SettingsPickerRow(
            title = stringResource(R.string.settings_theme),
            selected = uiState.themePreference,
            options = listOf(ThemePreference.DARK, ThemePreference.LIGHT, ThemePreference.SYSTEM),
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
        FinTrackDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                FinTrackMenuItem(
                    text = optionLabel(option),
                    selected = option == selected,
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
