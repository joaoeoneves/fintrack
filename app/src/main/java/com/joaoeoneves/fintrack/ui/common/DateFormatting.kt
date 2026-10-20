package com.joaoeoneves.fintrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.format.DateTimeFormatter

/**
 * A `"MMM d, yyyy"` date formatter that follows [currentLocale], so it is rebuilt whenever the
 * configuration changes, e.g. after `AppCompatDelegate.setApplicationLocales` recreates the
 * activity for a new in-app language. A module-level `val` formatter would instead be fixed at class-load time and
 * never pick up a later in-session language change.
 */
@Composable
fun rememberLocaleAwareDateFormatter(): DateTimeFormatter {
    val locale = currentLocale()
    return remember(locale) { DateTimeFormatter.ofPattern("MMM d, yyyy", locale) }
}
