package com.joaoeoneves.fintrack.ui.auth

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Session-driven states of [AuthViewModel]: Starting, Guest and continueAsGuest. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AuthViewModelGuestTest {
    private val testDispatcher = StandardTestDispatcher()
    private val user = AuthUser(uid = "u1", displayName = "Ada", email = "ada@example.com", photoUrl = null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isStarting_beforeSessionIsCollected() =
        runTest(testDispatcher) {
            val vm = AuthViewModel(FakeSessionRepository(initialSession = Session.Guest))
            assertEquals(AuthUiState.Starting, vm.uiState.value)
        }

    @Test
    fun loadingSession_staysStarting_thenResolves() =
        runTest(testDispatcher) {
            val sessions = FakeSessionRepository(initialSession = Session.Loading)
            val vm = AuthViewModel(sessions)
            advanceUntilIdle()
            assertEquals(AuthUiState.Starting, vm.uiState.value)

            sessions.emit(Session.SignedOut)
            advanceUntilIdle()
            assertEquals(AuthUiState.Idle, vm.uiState.value)
        }

    @Test
    fun guestSession_mapsToGuestState() =
        runTest(testDispatcher) {
            val vm = AuthViewModel(FakeSessionRepository(initialSession = Session.Guest))
            advanceUntilIdle()
            assertEquals(AuthUiState.Guest, vm.uiState.value)
        }

    @Test
    fun continueAsGuest_callsRepository_andMovesToGuest() =
        runTest(testDispatcher) {
            val sessions = FakeSessionRepository(initialSession = Session.SignedOut)
            val vm = AuthViewModel(sessions)
            advanceUntilIdle()

            vm.continueAsGuest()
            advanceUntilIdle()

            assertEquals(1, sessions.continueAsGuestCalls)
            assertEquals(AuthUiState.Guest, vm.uiState.value)
        }

    @Test
    fun signedInSession_thenLeavingGuest_followsSessionChanges() =
        runTest(testDispatcher) {
            val sessions = FakeSessionRepository(initialSession = Session.Guest)
            val vm = AuthViewModel(sessions)
            advanceUntilIdle()

            sessions.emit(Session.SignedIn(user))
            advanceUntilIdle()
            assertEquals(AuthUiState.SignedIn(user), vm.uiState.value)

            sessions.emit(Session.SignedOut)
            advanceUntilIdle()
            assertEquals(AuthUiState.Idle, vm.uiState.value)
        }

    @Test
    fun signIn_fromGuest_endsSignedIn() =
        runTest(testDispatcher) {
            val sessions =
                FakeSessionRepository(initialSession = Session.Guest, nextSignInResult = Result.success(user))
            val vm = AuthViewModel(sessions)
            advanceUntilIdle()

            vm.signIn(android.content.ContextWrapper(null))
            advanceUntilIdle()

            assertEquals(1, sessions.signInCalls)
            assertEquals(AuthUiState.SignedIn(user), vm.uiState.value)
        }

    @Test
    fun continueAsGuest_failure_showsError_andStaysOffGuest() =
        runTest(testDispatcher) {
            val sessions =
                FakeSessionRepository(
                    initialSession = Session.SignedOut,
                    nextContinueAsGuestResult = Result.failure(java.io.IOException("disk")),
                )
            val vm = AuthViewModel(sessions)
            advanceUntilIdle()

            vm.continueAsGuest()
            advanceUntilIdle()

            assertEquals(
                AuthUiState.Error(UiText.StringResource(R.string.error_guest_start_failed)),
                vm.uiState.value,
            )
        }
}
