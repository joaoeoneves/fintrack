package com.joaoeoneves.fintrack.domain.model

/**
 * User-facing in-app language preference. [SYSTEM] follows the device's locale; [ENGLISH] and
 * [PORTUGUESE] are explicit manual overrides set via the Settings screen's language section.
 */
enum class AppLanguage {
    SYSTEM,
    ENGLISH,
    PORTUGUESE,
}
