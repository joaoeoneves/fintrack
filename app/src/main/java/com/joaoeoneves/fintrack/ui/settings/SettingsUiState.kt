package com.joaoeoneves.fintrack.ui.settings

import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.ThemePreference

data class SettingsUiState(
    val themePreference: ThemePreference = ThemePreference.DARK,
    val currency: CurrencyOption = CurrencyOption.EUR,
    val language: AppLanguage = AppLanguage.PORTUGUESE,
    val currentUser: AuthUser? = null,
    val signOutError: String? = null,
)
