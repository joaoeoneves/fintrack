package com.joaoeoneves.fintrack.domain.model

/**
 * User-facing theme preference. [SYSTEM] follows the device's dark/light setting; [LIGHT] and
 * [DARK] are explicit manual overrides set via the dashboard's overflow menu toggle.
 */
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}
