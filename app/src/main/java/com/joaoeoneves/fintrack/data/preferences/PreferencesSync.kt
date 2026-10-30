package com.joaoeoneves.fintrack.data.preferences

/** The synced preference fields; [key] is the Firestore field name. */
enum class PreferenceField(
    val key: String,
) {
    THEME("theme"),
    LANGUAGE("language"),
}

/** Keeps the local preferences and the signed-in user's Firestore preferences document in sync. */
interface PreferencesSync {
    /** Starts observing auth and remote changes; safe to call more than once. */
    fun start()

    /** Fire-and-forget upload of one locally changed field (enum name); no-op when signed out. */
    fun push(
        field: PreferenceField,
        value: String,
    )
}
