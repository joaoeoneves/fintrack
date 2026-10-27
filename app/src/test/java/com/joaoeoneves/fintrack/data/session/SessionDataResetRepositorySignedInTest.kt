package com.joaoeoneves.fintrack.data.session

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.data.FirestoreDataResetRepository
import com.joaoeoneves.fintrack.data.local.LocalBudget
import com.joaoeoneves.fintrack.data.local.LocalDataResetRepository
import com.joaoeoneves.fintrack.data.local.LocalDataStore
import com.joaoeoneves.fintrack.data.preferences.UserPreferencesRepository
import com.joaoeoneves.fintrack.data.preferences.UserPreferencesStore
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.testutil.FakePreferencesSync
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * [SessionDataResetRepository] when signed in: the real (final) [FirestoreDataResetRepository] is used over a
 * real FirebaseAuth with no current user, so it fails with "Not signed in" before any network call. The
 * successful remote path cannot be faked without an interface seam (see the report).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SessionDataResetRepositorySignedInTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val sync = FakePreferencesSync()
    private val signedIn = Session.SignedIn(AuthUser("uid-1", "Ada", "ada@example.com", null))

    @Test
    fun signedIn_usesRemote_notLocal_andFailureIsReturnedWithPreferencesUntouched() =
        runBlocking {
            val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
            val remote = FirestoreDataResetRepository(FirebaseFirestore.getInstance(app), FirebaseAuth.getInstance(app))
            val lazyRemote = CountingLazy(remote)
            val store = LocalDataStore(File(tmp.root, "guest.json"), Dispatchers.IO, kotlinx.serialization.json.Json)
            store.update { it.copy(budgets = listOf(LocalBudget("FOOD", 100))) to Unit }
            val prefs =
                UserPreferencesRepository(
                    UserPreferencesStore(
                        PreferenceDataStoreFactory.create(
                            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
                            produceFile = { File(tmp.root, "p.preferences_pb") },
                        ),
                    ),
                    sync,
                )
            prefs.setThemePreference(ThemePreference.LIGHT)
            sync.pushes.clear()

            val result =
                SessionDataResetRepository(
                    FakeSessionRepository(initialSession = signedIn),
                    lazyRemote,
                    LocalDataResetRepository(store),
                    prefs,
                ).deleteAllData()

            assertEquals(1, lazyRemote.gets)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(
                1,
                store.data
                    .first()
                    .budgets.size,
            )
            assertEquals(ThemePreference.LIGHT, prefs.observeThemePreference().first())
            assertTrue(sync.pushes.isEmpty())
        }
}
