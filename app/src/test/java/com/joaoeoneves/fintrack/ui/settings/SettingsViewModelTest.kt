package com.joaoeoneves.fintrack.ui.settings

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SettingsViewModel], backed by a local [FakeThemeRepository] fake
 * (no existing fake under `app/src/main` for the repository, mirroring the pattern used by
 * [com.joaoeoneves.fintrack.ui.theme.ThemeViewModelTest]) and the existing [FakeSessionRepository], which
 * already supports configuring the next `signOut()` result.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val user =
        AuthUser(
            uid = "uid-1",
            displayName = "Ada Lovelace",
            email = "ada@example.com",
            photoUrl = null,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- initial / combined state ----

    @Test
    fun uiState_beforeCollection_isDefaultValue() =
        runTest(testDispatcher) {
            // Before advanceUntilIdle/collection, the StateFlow built via stateIn(WhileSubscribed)
            // has not yet started collecting upstream, so it should still report its initialValue
            // regardless of what the repositories are actually seeded with.
            val themeRepo = FakeThemeRepository(ThemePreference.DARK)
            val authRepo = FakeSessionRepository(initialUser = user)
            val viewModel = SettingsViewModel(themeRepo, authRepo)

            assertEquals(SettingsUiState(), viewModel.uiState.value)
        }

    @Test
    fun uiState_afterCollection_reflectsCombinedRepositoryValues() =
        runTest(testDispatcher) {
            val themeRepo = FakeThemeRepository(ThemePreference.DARK)
            val authRepo = FakeSessionRepository(initialUser = user)
            val viewModel = SettingsViewModel(themeRepo, authRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(
                SettingsUiState(
                    themePreference = ThemePreference.DARK,
                    currentUser = user,
                    signOutError = null,
                ),
                viewModel.uiState.value,
            )
            job.cancel()
        }

    @Test
    fun uiState_withNoSignedInUser_currentUserIsNull() =
        runTest(testDispatcher) {
            val themeRepo = FakeThemeRepository(ThemePreference.SYSTEM)
            val authRepo = FakeSessionRepository(initialUser = null)
            val viewModel = SettingsViewModel(themeRepo, authRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.currentUser)
            job.cancel()
        }

    // ---- setThemePreference ----

    @Test
    fun setThemePreference_writesThroughToRepository_andUiStateReflectsIt() =
        runTest(testDispatcher) {
            val themeRepo = FakeThemeRepository(ThemePreference.SYSTEM)
            val authRepo = FakeSessionRepository(initialUser = null)
            val viewModel = SettingsViewModel(themeRepo, authRepo)
            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.setThemePreference(ThemePreference.LIGHT)
            advanceUntilIdle()

            assertEquals(ThemePreference.LIGHT, themeRepo.currentPreference())
            assertEquals(ThemePreference.LIGHT, viewModel.uiState.value.themePreference)
            job.cancel()
        }

    @Test
    fun setThemePreference_toDark_writesThroughToRepository() =
        runTest(testDispatcher) {
            val themeRepo = FakeThemeRepository(ThemePreference.SYSTEM)
            val authRepo = FakeSessionRepository(initialUser = null)
            val viewModel = SettingsViewModel(themeRepo, authRepo)
            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.setThemePreference(ThemePreference.DARK)
            advanceUntilIdle()

            assertEquals(ThemePreference.DARK, themeRepo.currentPreference())
            job.cancel()
        }

    // ---- signOut: success ----

    @Test
    fun signOut_success_clearsCurrentUser_viaRepositoryFlow_signOutErrorStaysNull() =
        runTest(testDispatcher) {
            val themeRepo = FakeThemeRepository(ThemePreference.SYSTEM)
            val authRepo = FakeSessionRepository(initialUser = user)
            val viewModel = SettingsViewModel(themeRepo, authRepo)
            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(user, viewModel.uiState.value.currentUser)

            viewModel.signOut()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.currentUser)
            assertNull(viewModel.uiState.value.signOutError)
            job.cancel()
        }

    // ---- signOut: failure ----

    @Test
    fun signOut_failure_setsSignOutError_andLeavesCurrentUserUnchanged() =
        runTest(testDispatcher) {
            val themeRepo = FakeThemeRepository(ThemePreference.SYSTEM)
            val authRepo =
                FakeSessionRepository(
                    initialUser = user,
                    nextSignOutResult = Result.failure(IllegalStateException("network unavailable")),
                )
            val viewModel = SettingsViewModel(themeRepo, authRepo)
            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.signOut()
            advanceUntilIdle()

            assertEquals(UiText.StringResource(R.string.error_sign_out_failed), viewModel.uiState.value.signOutError)
            assertEquals(user, viewModel.uiState.value.currentUser)
            job.cancel()
        }

    @Test
    fun signOut_failure_withNullExceptionMessage_stillSetsANonNullSignOutError() =
        runTest(testDispatcher) {
            val themeRepo = FakeThemeRepository(ThemePreference.SYSTEM)
            val authRepo =
                FakeSessionRepository(
                    initialUser = user,
                    nextSignOutResult = Result.failure(RuntimeException()),
                )
            val viewModel = SettingsViewModel(themeRepo, authRepo)
            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.signOut()
            advanceUntilIdle()

            assertEquals(UiText.StringResource(R.string.error_sign_out_failed), viewModel.uiState.value.signOutError)
            assertEquals(user, viewModel.uiState.value.currentUser)
            job.cancel()
        }

    // ---- dismissSignOutError ----

    @Test
    fun dismissSignOutError_clearsTheError() =
        runTest(testDispatcher) {
            val themeRepo = FakeThemeRepository(ThemePreference.SYSTEM)
            val authRepo =
                FakeSessionRepository(
                    initialUser = user,
                    nextSignOutResult = Result.failure(IllegalStateException("network unavailable")),
                )
            val viewModel = SettingsViewModel(themeRepo, authRepo)
            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()
            viewModel.signOut()
            advanceUntilIdle()
            assertEquals(UiText.StringResource(R.string.error_sign_out_failed), viewModel.uiState.value.signOutError)

            viewModel.dismissSignOutError()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.signOutError)
            // Dismissing the error must not resurrect the still-signed-in user's absence/presence --
            // it only touches the local error state.
            assertEquals(user, viewModel.uiState.value.currentUser)
            job.cancel()
        }

    @Test
    fun dismissSignOutError_whenNoErrorPresent_isANoOp() =
        runTest(testDispatcher) {
            val themeRepo = FakeThemeRepository(ThemePreference.SYSTEM)
            val authRepo = FakeSessionRepository(initialUser = user)
            val viewModel = SettingsViewModel(themeRepo, authRepo)
            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.dismissSignOutError()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.signOutError)
            job.cancel()
        }

    @Test
    fun signOut_failureThenSuccess_eventuallyClearsUser_andErrorStaysNull() =
        runTest(testDispatcher) {
            val themeRepo = FakeThemeRepository(ThemePreference.SYSTEM)
            val authRepo =
                FakeSessionRepository(
                    initialUser = user,
                    nextSignOutResult = Result.failure(IllegalStateException("network unavailable")),
                )
            val viewModel = SettingsViewModel(themeRepo, authRepo)
            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.signOut()
            advanceUntilIdle()
            assertEquals(UiText.StringResource(R.string.error_sign_out_failed), viewModel.uiState.value.signOutError)
            assertEquals(user, viewModel.uiState.value.currentUser)

            authRepo.nextSignOutResult = Result.success(Unit)
            viewModel.signOut()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.currentUser)
            // signOutError is never cleared automatically on a later success -- only
            // dismissSignOutError() clears it. Confirm that's still the (documented) behavior so a
            // future change to auto-clear it is a deliberate one, not an accident.
            assertEquals(UiText.StringResource(R.string.error_sign_out_failed), viewModel.uiState.value.signOutError)
            job.cancel()
        }

    /**
     * Minimal in-memory [ThemeRepository] fake, local to this test file since no fake exists under
     * `app/src/main` for this repository (same pattern as `ThemeViewModelTest.FakeThemeRepository`).
     */
    private class FakeThemeRepository(
        initial: ThemePreference = ThemePreference.SYSTEM,
    ) : ThemeRepository {
        private val preferenceFlow = MutableStateFlow(initial)

        override fun observeThemePreference(): Flow<ThemePreference> = preferenceFlow

        override suspend fun setThemePreference(preference: ThemePreference) {
            preferenceFlow.value = preference
        }

        fun currentPreference(): ThemePreference = preferenceFlow.value
    }
}
