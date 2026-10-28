package com.joaoeoneves.fintrack.testutil

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.joaoeoneves.fintrack.data.FakeLegacyNames
import com.joaoeoneves.fintrack.data.backup.AccountBackupRestorer
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A real [AccountBackupRestorer] over a network-inert Firebase app with nobody signed in (see [FirebaseTestApp]), so
 * `restore` ends in `AccountRestoreResult.Failed("Not signed in")` without reading or writing anything. The class is
 * final and the project has no mocking library, so the signed-in outcomes (Success, Offline, Partial) cannot be
 * produced from JVM tests.
 */
fun unsignedAccountRestorer(context: Context): AccountBackupRestorer {
    val app = FirebaseTestApp.ensureInitialized(context)
    return AccountBackupRestorer(
        FirebaseFirestore.getInstance(app),
        FirebaseAuth.getInstance(app),
        FakeLegacyNames(),
        object : ThemeRepository {
            private val theme = MutableStateFlow(ThemePreference.DARK)

            override fun observeThemePreference(): Flow<ThemePreference> = theme

            override suspend fun setThemePreference(preference: ThemePreference) {
                theme.value = preference
            }
        },
        FakeCurrencyRepository(),
        FakeLanguageRepository(),
    )
}
