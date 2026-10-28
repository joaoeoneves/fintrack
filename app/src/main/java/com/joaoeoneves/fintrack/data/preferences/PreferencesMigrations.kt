package com.joaoeoneves.fintrack.data.preferences

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences

const val PREFERENCES_DATASTORE_NAME = "user_preferences"
const val KEY_THEME = "theme_preference"
const val KEY_CURRENCY = "currency"
const val KEY_LANGUAGE = "language"
const val KEY_REDUCED_MOTION = "reduced_motion"

private const val LEGACY_THEME_FILE = "theme_prefs"
private const val LEGACY_CURRENCY_FILE = "currency_prefs"
private const val LEGACY_LANGUAGE_FILE = "language_prefs"

/** Copies the values from the three legacy SharedPreferences files into the DataStore. */
internal fun preferencesMigrations(context: Context): List<DataMigration<Preferences>> =
    listOf(
        SharedPreferencesMigration(context, LEGACY_THEME_FILE, keysToMigrate = setOf(KEY_THEME)),
        SharedPreferencesMigration(context, LEGACY_CURRENCY_FILE, keysToMigrate = setOf(KEY_CURRENCY)),
        SharedPreferencesMigration(context, LEGACY_LANGUAGE_FILE, keysToMigrate = setOf(KEY_LANGUAGE)),
    )
