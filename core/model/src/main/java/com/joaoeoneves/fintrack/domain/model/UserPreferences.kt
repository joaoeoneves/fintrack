package com.joaoeoneves.fintrack.domain.model

/** The user's synced app preferences, with the first-install defaults. */
data class UserPreferences(
    val theme: ThemePreference = ThemePreference.DARK,
    val language: AppLanguage = AppLanguage.SYSTEM,
)
