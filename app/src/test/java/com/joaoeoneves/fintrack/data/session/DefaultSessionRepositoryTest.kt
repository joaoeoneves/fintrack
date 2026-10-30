package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FakeAuthRepository
import com.joaoeoneves.fintrack.data.preferences.PreferencesStoreTestBase
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.SignInCancelledException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class DefaultSessionRepositoryTest : PreferencesStoreTestBase() {
    private val user = AuthUser(uid = "u1", displayName = "Ada", email = "ada@example.com", photoUrl = null)
    private val scope: CoroutineScope = newScope()
    private val dataStore by lazy { newDataStore(scope) }
    private val flagStore by lazy { SessionFlagStore(dataStore) }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun repo(auth: FakeAuthRepository = FakeAuthRepository()) = DefaultSessionRepository(auth, flagStore, guestSeederIn(tmp.root), scope)

    private suspend fun DefaultSessionRepository.await(expected: Session): Session =
        withTimeout(TIMEOUT_MS) { session.first { it == expected } }

    @Test
    fun neitherUserNorFlag_isSignedOut() =
        runBlocking {
            assertEquals(Session.SignedOut, repo().await(Session.SignedOut))
        }

    @Test
    fun flagWithoutUser_isGuest() =
        runBlocking {
            flagStore.setGuest(true)
            assertEquals(Session.Guest, repo().await(Session.Guest))
        }

    @Test
    fun userWinsOverFlag() =
        runBlocking {
            flagStore.setGuest(true)
            val sessions = repo(FakeAuthRepository(initialUser = user))
            assertEquals(Session.SignedIn(user), sessions.await(Session.SignedIn(user)))
        }

    @Test
    fun continueAsGuest_persistsAcrossInstances() =
        runBlocking {
            val first = repo()
            first.await(Session.SignedOut)
            first.continueAsGuest()
            first.await(Session.Guest)

            val second = DefaultSessionRepository(FakeAuthRepository(), SessionFlagStore(dataStore), guestSeederIn(tmp.root), scope)
            assertEquals(Session.Guest, second.await(Session.Guest))
        }

    @Test
    fun leaveGuestMode_clearsFlag() =
        runBlocking {
            val sessions = repo()
            sessions.continueAsGuest()
            sessions.await(Session.Guest)
            sessions.leaveGuestMode()
            assertEquals(Session.SignedOut, sessions.await(Session.SignedOut))
            assertEquals(false, flagStore.isGuest.first())
        }

    @Test
    fun signIn_success_clearsGuestFlag_andSignsIn() =
        runBlocking {
            val auth = FakeAuthRepository(nextSignInResult = Result.success(user))
            val sessions = repo(auth)
            sessions.continueAsGuest()
            sessions.await(Session.Guest)

            assertTrue(sessions.signIn(contextStub()).isSuccess)

            assertEquals(Session.SignedIn(user), sessions.await(Session.SignedIn(user)))
            assertEquals(false, flagStore.isGuest.first())
        }

    @Test
    fun signIn_failureOrCancel_keepsGuest() =
        runBlocking {
            val auth = FakeAuthRepository(nextSignInResult = Result.failure(IllegalStateException("boom")))
            val sessions = repo(auth)
            sessions.continueAsGuest()
            sessions.await(Session.Guest)

            assertTrue(sessions.signIn(contextStub()).isFailure)
            assertEquals(Session.Guest, sessions.session.value)

            auth.nextSignInResult = Result.failure(SignInCancelledException())
            assertTrue(sessions.signIn(contextStub()).exceptionOrNull() is SignInCancelledException)
            assertEquals(Session.Guest, sessions.session.value)
            assertEquals(true, flagStore.isGuest.first())
        }

    @Test
    fun signOut_success_clearsFlag_andShowsSignedOut() =
        runBlocking {
            val sessions = repo(FakeAuthRepository(initialUser = user))
            sessions.await(Session.SignedIn(user))
            flagStore.setGuest(true)

            assertTrue(sessions.signOut().isSuccess)

            assertEquals(Session.SignedOut, sessions.await(Session.SignedOut))
            assertEquals(false, flagStore.isGuest.first())
        }

    @Test
    fun signOut_failure_keepsUserAndFlag() =
        runBlocking {
            val auth =
                FakeAuthRepository(
                    initialUser = user,
                    nextSignOutResult = Result.failure(IllegalStateException()),
                )
            val sessions = repo(auth)
            sessions.await(Session.SignedIn(user))
            flagStore.setGuest(true)

            assertTrue(sessions.signOut().isFailure)

            assertEquals(Session.SignedIn(user), sessions.session.value)
            assertEquals(true, flagStore.isGuest.first())
        }

    @Test
    fun initialValue_isLoading_untilFirstEmission() {
        val blocked = kotlinx.coroutines.flow.MutableSharedFlow<AuthUser?>()
        val auth =
            object : com.joaoeoneves.fintrack.domain.repository.AuthRepository {
                override fun observeCurrentUser() = blocked

                override suspend fun signIn(context: android.content.Context) = Result.failure<AuthUser>(Exception())

                override suspend fun signOut() = Result.success(Unit)
            }
        val sessions = DefaultSessionRepository(auth, flagStore, guestSeederIn(tmp.root), scope)
        assertEquals(Session.Loading, sessions.session.value)
    }

    private fun contextStub(): android.content.Context = android.content.ContextWrapper(null)

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
