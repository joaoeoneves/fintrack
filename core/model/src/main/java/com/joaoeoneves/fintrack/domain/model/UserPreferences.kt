package com.joaoeoneves.fintrack.domain.model

/** The user's synced app preferences, with the first-install defaults. */
data class UserPreferences(
    val theme: ThemePreference = ThemePreference.DARK,
    val currency: CurrencyOption = CurrencyOption.EUR,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val reducedMotion: MotionPreference = MotionPreference.SYSTEM,
    /** Device-local: whether the salary pays the next month's plan; `null` until chosen (then follows the profile). */
    val salaryPaysNextMonth: Boolean? = null,
)
