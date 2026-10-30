package com.joaoeoneves.fintrack.ui.settings

import android.app.Application
import android.content.ContextWrapper
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.SignInCancelledException
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Guest-mode behaviour of [SettingsViewModel]: isGuest, signing in to sync, leaving guest mode. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsViewModelGuestTest {
    private val testDispatcher = StandardTestDispatcher()
    private val user = AuthUser(uid = "u1", displayName = "Ada", email = "ada@example.com", photoUrl = null)

    private class StaticThemeRepository : ThemeRepository {
        private val flow = MutableStateFlow(ThemePreference.SYSTEM)

        override fun observeThemePreference(): Flow<ThemePreference> = flow

        override suspend fun setThemePreference(preference: ThemePreference) {
            flow.value = preference
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(sessions: FakeSessionRepository): SettingsViewModel {
        val vm = SettingsViewModel(StaticThemeRepository(), sessions)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    @Test
    fun guestSession_setsIsGuest_withoutUser() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeSessionRepository(initialSession = Session.Guest))
            assertTrue(vm.uiState.value.isGuest)
            assertNull(vm.uiState.value.currentUser)
        }

    @Test
    fun signedInSession_isNotGuest_withUser() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeSessionRepository(initialUser = user))
            assertFalse(vm.uiState.value.isGuest)
            assertEquals(user, vm.uiState.value.currentUser)
        }

    @Test
    fun leaveGuestMode_callsRepository_andClearsIsGuest() =
        runTest(testDispatcher) {
            val sessions = FakeSessionRepository(initialSession = Session.Guest)
            val vm = viewModel(sessions)

            vm.leaveGuestMode()
            advanceUntilIdle()

            assertEquals(1, sessions.leaveGuestModeCalls)
            assertFalse(vm.uiState.value.isGuest)
        }

    @Test
    fun signInToSync_success_signsInAndLeavesNoError() =
        runTest(testDispatcher) {
            val sessions =
                FakeSessionRepository(initialSession = Session.Guest, nextSignInResult = Result.success(user))
            val vm = viewModel(sessions)

            vm.signInToSync(ContextWrapper(null))
            advanceUntilIdle()

            assertEquals(1, sessions.signInCalls)
            assertEquals(user, vm.uiState.value.currentUser)
            assertFalse(vm.uiState.value.isGuest)
            assertNull(vm.uiState.value.signInError)
        }

    @Test
    fun signInToSync_failure_setsGenericError_neverRawMessage_andStaysGuest() =
        runTest(testDispatcher) {
            val sessions =
                FakeSessionRepository(
                    initialSession = Session.Guest,
                    nextSignInResult = Result.failure(IllegalStateException("raw sdk message")),
                )
            val vm = viewModel(sessions)

            vm.signInToSync(ContextWrapper(null))
            advanceUntilIdle()

            assertEquals(UiText.StringResource(R.string.error_sign_in_failed), vm.uiState.value.signInError)
            assertTrue(vm.uiState.value.isGuest)
        }

    @Test
    fun signInToSync_cancelled_isIgnored() =
        runTest(testDispatcher) {
            val sessions =
                FakeSessionRepository(
                    initialSession = Session.Guest,
                    nextSignInResult = Result.failure(SignInCancelledException()),
                )
            val vm = viewModel(sessions)

            vm.signInToSync(ContextWrapper(null))
            advanceUntilIdle()

            assertNull(vm.uiState.value.signInError)
            assertTrue(vm.uiState.value.isGuest)
        }

    @Test
    fun dismissSignInError_clearsOnlySignInError() =
        runTest(testDispatcher) {
            val sessions =
                FakeSessionRepository(initialUser = user, nextSignInResult = Result.failure(IllegalStateException()))
            sessions.nextSignOutResult = Result.failure(IllegalStateException())
            val vm = viewModel(sessions)
            vm.signInToSync(ContextWrapper(null))
            vm.signOut()
            advanceUntilIdle()
            assertEquals(UiText.StringResource(R.string.error_sign_in_failed), vm.uiState.value.signInError)
            assertEquals(UiText.StringResource(R.string.error_sign_out_failed), vm.uiState.value.signOutError)

            vm.dismissSignInError()
            advanceUntilIdle()

            assertNull(vm.uiState.value.signInError)
            assertEquals(UiText.StringResource(R.string.error_sign_out_failed), vm.uiState.value.signOutError)
        }

    @Test
    fun signOut_usesSessionRepository() =
        runTest(testDispatcher) {
            val sessions = FakeSessionRepository(initialUser = user)
            val vm = viewModel(sessions)

            vm.signOut()
            advanceUntilIdle()

            assertEquals(1, sessions.signOutCalls)
            assertNull(vm.uiState.value.currentUser)
            assertFalse(vm.uiState.value.isGuest)
        }

    @Test
    fun leaveGuestMode_failure_setsSignOutError_andStaysGuest() =
        runTest(testDispatcher) {
            val sessions =
                FakeSessionRepository(
                    initialSession = Session.Guest,
                    nextLeaveGuestModeResult = Result.failure(java.io.IOException("disk")),
                )
            val vm = viewModel(sessions)

            vm.leaveGuestMode()
            advanceUntilIdle()

            assertEquals(UiText.StringResource(R.string.error_sign_out_failed), vm.uiState.value.signOutError)
            assertTrue(vm.uiState.value.isGuest)
        }
}
