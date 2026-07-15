package com.joaoeoneves.fintrack.ui.settings

import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.ThemePreference

data class SettingsUiState(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val currency: CurrencyOption = CurrencyOption.USD,
    val currentUser: AuthUser? = null,
    val signOutError: String? = null,
)
