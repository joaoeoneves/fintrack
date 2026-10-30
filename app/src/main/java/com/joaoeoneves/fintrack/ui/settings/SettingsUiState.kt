package com.joaoeoneves.fintrack.ui.settings

import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.ui.common.UiText

data class SettingsUiState(
    val themePreference: ThemePreference = ThemePreference.DARK,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val currentUser: AuthUser? = null,
    val signOutError: UiText? = null,
    val isGuest: Boolean = false,
    val signInError: UiText? = null,
)
