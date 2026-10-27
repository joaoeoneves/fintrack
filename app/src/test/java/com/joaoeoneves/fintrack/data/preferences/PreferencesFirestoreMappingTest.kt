package com.joaoeoneves.fintrack.data.preferences

import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreferencesFirestoreMappingTest {
    private fun snap(
        data: Map<String, Any?>?,
        fromCache: Boolean = false,
        pending: Boolean = false,
    ) = RemoteSnapshot(data, fromCache, pending)

    @Test
    fun decide_pendingWrites_isNone() {
        assertEquals(SyncAction.None, decideSyncAction(snap(mapOf("theme" to "LIGHT"), pending = true)))
        assertEquals(SyncAction.None, decideSyncAction(snap(null, pending = true)))
    }

    @Test
    fun decide_missingFromCache_isNone() {
        assertEquals(SyncAction.None, decideSyncAction(snap(null, fromCache = true)))
    }

    @Test
    fun decide_missingFromServer_isUploadAll() {
        assertEquals(SyncAction.UploadAll, decideSyncAction(snap(null)))
    }

    @Test
    fun decide_present_isMergeEvenFromCache() {
        val data = mapOf<String, Any?>("theme" to "LIGHT")
        assertEquals(
            SyncAction.Merge(data.toRemotePreferences()),
            decideSyncAction(snap(data, fromCache = true)),
        )
        assertEquals(SyncAction.Merge(data.toRemotePreferences()), decideSyncAction(snap(data)))
    }

    @Test
    fun decide_emptyExistingDoc_mergesWithAllFieldsMissing() {
        val action = decideSyncAction(snap(emptyMap()))

        assertEquals(PreferenceField.entries.toSet(), (action as SyncAction.Merge).remote.missing)
    }

    @Test
    fun toRemote_allValid() {
        val r =
            mapOf<String, Any?>("theme" to "SYSTEM", "currency" to "JPY", "language" to "ENGLISH", "updatedAt" to 1L)
                .toRemotePreferences()

        assertEquals(RemotePreferences(ThemePreference.SYSTEM, CurrencyOption.JPY, AppLanguage.ENGLISH, emptySet()), r)
    }

    @Test
    fun toRemote_absentKeys_areListedAsMissing() {
        val r = mapOf<String, Any?>("currency" to "CAD").toRemotePreferences()

        assertEquals(CurrencyOption.CAD, r.currency)
        assertNull(r.theme)
        assertEquals(setOf(PreferenceField.THEME, PreferenceField.LANGUAGE), r.missing)
    }

    @Test
    fun toRemote_unknownOrNonStringValues_areNullButNotMissing() {
        val r =
            mapOf<String, Any?>("theme" to "NEON", "currency" to 42, "language" to null)
                .toRemotePreferences()

        assertNull(r.theme)
        assertNull(r.currency)
        assertNull(r.language)
        assertEquals(emptySet<PreferenceField>(), r.missing)
    }

    @Test
    fun toRemote_lowercaseName_isNotRecognized() {
        assertNull(mapOf<String, Any?>("theme" to "light").toRemotePreferences().theme)
    }

    @Test
    fun mergedWith_remoteOverrides_nullKeepsLocal() {
        val local = UserPreferences(ThemePreference.DARK, CurrencyOption.EUR, AppLanguage.PORTUGUESE)
        val remote = RemotePreferences(ThemePreference.LIGHT, null, AppLanguage.ENGLISH, emptySet())

        assertEquals(
            UserPreferences(ThemePreference.LIGHT, CurrencyOption.EUR, AppLanguage.ENGLISH),
            local.mergedWith(remote),
        )
    }

    @Test
    fun fieldValues_returnsOnlyRequestedFieldsWithEnumNames() {
        val prefs = UserPreferences(ThemePreference.LIGHT, CurrencyOption.GBP, AppLanguage.SYSTEM)

        assertEquals(
            mapOf("theme" to "LIGHT", "language" to "SYSTEM"),
            prefs.fieldValues(setOf(PreferenceField.THEME, PreferenceField.LANGUAGE)),
        )
        assertEquals(
            mapOf("theme" to "LIGHT", "currency" to "GBP", "language" to "SYSTEM"),
            prefs.fieldValues(PreferenceField.entries.toSet()),
        )
        assertEquals(emptyMap<String, String>(), prefs.fieldValues(emptySet()))
    }
}
