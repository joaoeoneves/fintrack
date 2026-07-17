package com.joaoeoneves.fintrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A `"MMM d, yyyy"` date formatter that re-reads [Locale.getDefault] whenever the configuration
 * changes, e.g. after `AppCompatDelegate.setApplicationLocales` recreates the activity for a new
 * in-app language. A module-level `val` formatter would instead be fixed at class-load time and
 * never pick up a later in-session language change.
 */
@Composable
fun rememberLocaleAwareDateFormatter(): DateTimeFormatter {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault(Locale.Category.FORMAT))
    }
}
