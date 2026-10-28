package com.joaoeoneves.fintrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.util.Locale

/**
 * The primary locale of the current configuration, so formatting follows the in-app language
 * (which recreates the activity with a new configuration) rather than a process-wide default
 * captured once. Falls back to [Locale.ROOT] (never the process default, which is not observable
 * from composition) in the practically impossible case of an empty locale list.
 */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
