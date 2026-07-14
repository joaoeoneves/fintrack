package ptech.joaoe.agenticusage.ui.auth

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import ptech.joaoe.agenticusage.data.FakeAuthRepository
import ptech.joaoe.agenticusage.domain.model.AuthUser
import ptech.joaoe.agenticusage.domain.repository.AuthRepository

/**
 * Unit tests for [AuthViewModel]'s state transitions, backed by [FakeAuthRepository] for
 * everything except deterministically observing the transient `Loading` state during `signIn`
 * (where [GatedSignInAuthRepository], a small local fake implementing [AuthRepository] directly
 * and defined at the bottom of this file, is used instead -- see the comment above
 * `signIn_success_transitionsIdleThenLoadingThenSignedIn` for why).
 *
 * All tests share one [StandardTestDispatcher] used both as `Dispatchers.Main` (so
 * `viewModelScope` coroutines run on it) and as the dispatcher driving [runTest] itself, so that
 * a single `advanceUntilIdle()` call reliably flushes everything the ViewModel's `init` block and
 * its `signIn`/`signOut` coroutines have queued.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // AuthRepository.signIn(context: Context) requires a real Context reference even though none
    // of the fakes used here dereference it. ContextWrapper(null) constructs without invoking any
    // stubbed Android framework method body, so it's a safe, dependency-free stand-in on the JVM
    // unit test classpath (no Robolectric/Mockito needed) -- verified to construct cleanly under
    // this project's `./gradlew testDebugUnitTest` setup.
    private val fakeContext: Context = ContextWrapper(null)

    private val user = AuthUser(
        uid = "uid-1",
        displayName = "Ada Lovelace",
        email = "ada@example.com",
        photoUrl = null
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- initial state ----

    @Test
    fun initialState_isIdle_whenRepositoryStartsWithNoUser() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(initialUser = null)
        val viewModel = AuthViewModel(repo)

        advanceUntilIdle()

        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun initialState_isSignedIn_whenRepositoryStartsWithAlreadyAuthenticatedUser() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(initialUser = user)
        val viewModel = AuthViewModel(repo)

        advanceUntilIdle()

        assertEquals(AuthUiState.SignedIn(user), viewModel.uiState.value)
    }

    // ---- signIn: success ----
    //
    // FakeAuthRepository.signIn() never actually suspends (no real await point), so under
    // StandardTestDispatcher's cooperative, non-preemptive scheduling the ViewModel's
    // Loading -> SignedIn writes can both happen inside the *same* dispatched task, before our
    // test's collector task ever gets to run. Because StateFlow only keeps the latest value
    // (conflation), a collector that hasn't been resumed yet can miss the transient Loading value
    // entirely -- this was observed directly: an earlier version of this test using
    // FakeAuthRepository directly flaked/failed by only ever observing [Idle, SignedIn]. To
    // deterministically observe the intermediate Loading state we use GatedSignInAuthRepository,
    // a local fake whose signIn() suspends on a controllable gate until the test releases it.

    @Test
    fun signIn_success_transitionsIdleThenLoadingThenSignedIn() = runTest(testDispatcher) {
        val repo = GatedSignInAuthRepository(initialUser = null, signInResult = Result.success(user))
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        val snapshots = mutableListOf<AuthUiState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiState.collect { snapshots.add(it) }
        }

        viewModel.signIn(fakeContext)
        advanceUntilIdle() // runs the ViewModel coroutine up to (and no further than) the signIn gate

        assertEquals(listOf(AuthUiState.Idle, AuthUiState.Loading), snapshots)

        repo.signInGate.complete(Unit)
        advanceUntilIdle()
        job.cancel()

        assertEquals(
            listOf(AuthUiState.Idle, AuthUiState.Loading, AuthUiState.SignedIn(user)),
            snapshots
        )
    }

    // ---- signIn: failure ----

    @Test
    fun signIn_failure_transitionsLoadingThenError_withUnderlyingMessage() = runTest(testDispatcher) {
        val repo = GatedSignInAuthRepository(
            initialUser = null,
            signInResult = Result.failure(IllegalStateException("no credential available"))
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        val snapshots = mutableListOf<AuthUiState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiState.collect { snapshots.add(it) }
        }

        viewModel.signIn(fakeContext)
        advanceUntilIdle()
        assertEquals(listOf(AuthUiState.Idle, AuthUiState.Loading), snapshots)

        repo.signInGate.complete(Unit)
        advanceUntilIdle()
        job.cancel()

        assertEquals(
            listOf(AuthUiState.Idle, AuthUiState.Loading, AuthUiState.Error("no credential available")),
            snapshots
        )
    }

    @Test
    fun signIn_failure_withNullExceptionMessage_fallsBackToDefaultMessage() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            initialUser = null,
            nextSignInResult = Result.failure(RuntimeException())
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        viewModel.signIn(fakeContext)
        advanceUntilIdle()

        assertEquals(AuthUiState.Error("Sign-in failed"), viewModel.uiState.value)
    }

    // ---- signOut: success (relies on the fake's flow, not a ViewModel shortcut) ----

    @Test
    fun signOut_success_movesSignedInStateBackToIdle_viaObserveCurrentUserEmittingNull() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(initialUser = user)
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()
        assertEquals(AuthUiState.SignedIn(user), viewModel.uiState.value)

        viewModel.signOut()
        advanceUntilIdle()

        // Confirm the *repository's* observable flow actually flipped to null -- this is the
        // real mechanism the ViewModel depends on, not an assumption.
        assertNull(currentValueOf(repo.observeCurrentUser()))
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun signOut_success_whenAlreadySignedOut_staysIdle() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(initialUser = null)
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    // ---- signOut: failure ----
    //
    // FakeAuthRepository now takes a configurable `nextSignOutResult` (see
    // FakeExpenseRepository-style constructor param), which on failure returns that result
    // *without* touching userFlow, so we can exercise AuthViewModel's failure branch directly
    // against the real fake instead of a bespoke local subclass.

    @Test
    fun signOut_failure_setsErrorState_withUnderlyingMessage() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            initialUser = user,
            nextSignOutResult = Result.failure(IllegalStateException("network unavailable"))
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(AuthUiState.Error("network unavailable"), viewModel.uiState.value)
    }

    @Test
    fun signOut_failure_withNullExceptionMessage_fallsBackToDefaultMessage() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            initialUser = user,
            nextSignOutResult = Result.failure(RuntimeException())
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(AuthUiState.Error("Sign-out failed"), viewModel.uiState.value)
    }

    @Test
    fun signOut_failure_doesNotClearCurrentUser_repositoryFlowStillReflectsSignedInUser() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            initialUser = user,
            nextSignOutResult = Result.failure(IllegalStateException("network unavailable"))
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()
        assertEquals(AuthUiState.SignedIn(user), viewModel.uiState.value)

        viewModel.signOut()
        advanceUntilIdle()

        // AuthViewModel itself doesn't clear any state on signOut failure -- it relies entirely on
        // observeCurrentUser()/userFlow being updated by the repository. Confirm the repository's
        // own flow still reflects the signed-in user (i.e. FakeAuthRepository.signOut() did not
        // clear userFlow.value on failure), even though the ViewModel's uiState surfaces an Error.
        assertEquals(user, currentValueOf(repo.observeCurrentUser()))
        assertEquals(AuthUiState.Error("network unavailable"), viewModel.uiState.value)
    }

    @Test
    fun signOut_failureThenSuccess_eventuallyTransitionsToIdle_userClearedOnlyOnSuccess() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            initialUser = user,
            nextSignOutResult = Result.failure(IllegalStateException("network unavailable"))
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()
        assertEquals(AuthUiState.Error("network unavailable"), viewModel.uiState.value)
        assertEquals(user, currentValueOf(repo.observeCurrentUser()))

        // Reconfigure the fake to succeed on the next call and retry.
        repo.nextSignOutResult = Result.success(Unit)
        viewModel.signOut()
        advanceUntilIdle()

        assertNull(currentValueOf(repo.observeCurrentUser()))
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    // ---- other edge cases ----

    @Test
    fun signIn_calledWhileAlreadySignedIn_goesThroughLoadingAgain_thenSignedInWithNewUser() = runTest(testDispatcher) {
        val otherUser = user.copy(uid = "uid-2", displayName = "Grace Hopper")
        val repo = GatedSignInAuthRepository(initialUser = user, signInResult = Result.success(otherUser))
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()
        assertEquals(AuthUiState.SignedIn(user), viewModel.uiState.value)

        val snapshots = mutableListOf<AuthUiState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiState.collect { snapshots.add(it) }
        }

        viewModel.signIn(fakeContext)
        advanceUntilIdle()
        assertEquals(listOf(AuthUiState.SignedIn(user), AuthUiState.Loading), snapshots)

        repo.signInGate.complete(Unit)
        advanceUntilIdle()
        job.cancel()

        assertEquals(
            listOf(AuthUiState.SignedIn(user), AuthUiState.Loading, AuthUiState.SignedIn(otherUser)),
            snapshots
        )
    }

    @Test
    fun backToBackSignInThenSignOut_endsIdle() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            initialUser = null,
            nextSignInResult = Result.success(user)
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        viewModel.signIn(fakeContext)
        advanceUntilIdle()
        assertEquals(AuthUiState.SignedIn(user), viewModel.uiState.value)

        viewModel.signOut()
        advanceUntilIdle()
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    /**
     * Peek at the current value of a hot [Flow] known to be backed by a [StateFlow] (as
     * [FakeAuthRepository.observeCurrentUser] is), without needing a `first()` collection.
     */
    private fun currentValueOf(flow: Flow<AuthUser?>): AuthUser? = (flow as StateFlow<AuthUser?>).value

    /**
     * Local [AuthRepository] fake whose [signIn] suspends on a test-controlled
     * [CompletableDeferred] gate before resolving to [signInResult]. This lets a test deterministically
     * pause the ViewModel coroutine right after it writes [AuthUiState.Loading] and before it
     * writes the outcome, so the intermediate state can be asserted without racing StateFlow's
     * conflation against StandardTestDispatcher's scheduling.
     */
    private class GatedSignInAuthRepository(
        initialUser: AuthUser?,
        private val signInResult: Result<AuthUser>
    ) : AuthRepository {
        private val userFlow = MutableStateFlow(initialUser)
        val signInGate = CompletableDeferred<Unit>()

        override fun observeCurrentUser(): Flow<AuthUser?> = userFlow

        override suspend fun signIn(context: Context): Result<AuthUser> {
            signInGate.await()
            signInResult.onSuccess { userFlow.value = it }
            return signInResult
        }

        override suspend fun signOut(): Result<Unit> {
            userFlow.value = null
            return Result.success(Unit)
        }
    }
}
