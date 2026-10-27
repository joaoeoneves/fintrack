package com.joaoeoneves.fintrack.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.joaoeoneves.fintrack.domain.model.ThemePreference

private const val PREFS_NAME = "night_mode_mirror"
private const val KEY_THEME = "theme"

/**
 * Keeps AppCompat's night mode (which drives the window background and status bar of the
 * DayNight window theme before Compose draws anything) in line with the stored [ThemePreference].
 * DataStore is asynchronous, so the last resolved value is mirrored into a tiny SharedPreferences
 * file that [restore] reads synchronously in `Application.onCreate` (a few bytes, already cached
 * by the platform; DataStore stays the source of truth).
 */
object NightModeSync {
    /** Applies the mirrored preference as early as possible (before the first activity). */
    fun restore(context: Context) {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_THEME, null)
        val preference = name?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
        if (preference != null) AppCompatDelegate.setDefaultNightMode(preference.toNightMode())
    }

    /** Mirrors [preference] and applies it; a no-op when AppCompat is already in that mode. */
    fun apply(
        context: Context,
        preference: ThemePreference,
    ) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_THEME, preference.name) }
        val mode = preference.toNightMode()
        if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun ThemePreference.toNightMode(): Int =
        when (this) {
            ThemePreference.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemePreference.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemePreference.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
}
