package com.joaoeoneves.fintrack.data.preferences

import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.model.UserPreferences

/**
 * A parsed remote preferences document. A null value means the field was absent or unrecognized;
 * only absent fields are listed in [missing] (and uploaded from local).
 */
data class RemotePreferences(
    val theme: ThemePreference?,
    val language: AppLanguage?,
    val missing: Set<PreferenceField>,
)

/** What a Firestore snapshot said: [data] is null when the document does not exist. */
data class RemoteSnapshot(
    val data: Map<String, Any?>?,
    val fromCache: Boolean,
    val hasPendingWrites: Boolean,
)

sealed interface SyncAction {
    data object None : SyncAction

    data object UploadAll : SyncAction

    data class Merge(
        val remote: RemotePreferences,
    ) : SyncAction
}

fun decideSyncAction(s: RemoteSnapshot): SyncAction =
    when {
        s.hasPendingWrites -> SyncAction.None
        s.data == null && s.fromCache -> SyncAction.None
        s.data == null -> SyncAction.UploadAll
        else -> SyncAction.Merge(s.data.toRemotePreferences())
    }

fun Map<String, Any?>.toRemotePreferences(): RemotePreferences =
    RemotePreferences(
        theme = parseEnum<ThemePreference>(PreferenceField.THEME),
        language = parseEnum<AppLanguage>(PreferenceField.LANGUAGE),
        missing = PreferenceField.entries.filterNot { containsKey(it.key) }.toSet(),
    )

fun UserPreferences.mergedWith(r: RemotePreferences): UserPreferences =
    copy(
        theme = r.theme ?: theme,
        language = r.language ?: language,
    )

fun UserPreferences.fieldValues(fields: Set<PreferenceField>): Map<String, String> =
    fields.associate { field ->
        field.key to
            when (field) {
                PreferenceField.THEME -> theme.name
                PreferenceField.LANGUAGE -> language.name
            }
    }

private inline fun <reified E : Enum<E>> Map<String, Any?>.parseEnum(field: PreferenceField): E? {
    val raw = this[field.key] as? String
    return enumValues<E>().find { it.name == raw }
}
