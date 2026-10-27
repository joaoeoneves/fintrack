package com.joaoeoneves.fintrack.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.joaoeoneves.fintrack.data.FakeAuthRepository
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** Disk failures in the guest flag's DataStore must never block or fail the session. */
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class DefaultSessionRepositoryFailureTest {
    private val user = AuthUser(uid = "u1", displayName = null, email = null, photoUrl = null)
    private val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())

    /** [DataStore] whose reads and/or writes throw [IOException]. */
    private class ThrowingDataStore(
        private val failRead: Boolean,
        private val failWrite: Boolean,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> =
            flow {
                if (failRead) throw IOException("read failed")
                emit(emptyPreferences())
            }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (failWrite) throw IOException("write failed")
            return transform(emptyPreferences())
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun repo(
        store: DataStore<Preferences>,
        auth: FakeAuthRepository = FakeAuthRepository(),
    ) = DefaultSessionRepository(auth, SessionFlagStore(store), scope)

    private suspend fun DefaultSessionRepository.resolved() = withTimeout(TIMEOUT_MS) { session.first(::isResolved) }

    @Test
    fun unreadableFlag_resolvesToSignedOut_notStuckLoading() =
        runBlocking(Dispatchers.Default) {
            val sessions = repo(ThrowingDataStore(failRead = true, failWrite = false))
            assertEquals(Session.SignedOut, sessions.resolved())
        }

    @Test
    fun unreadableFlag_withUser_resolvesToSignedIn() =
        runBlocking(Dispatchers.Default) {
            val sessions = repo(ThrowingDataStore(true, false), FakeAuthRepository(initialUser = user))
            assertEquals(Session.SignedIn(user), sessions.resolved())
        }

    @Test
    fun flagStore_unreadable_readsAsFalse() =
        runBlocking(Dispatchers.Default) {
            assertFalse(SessionFlagStore(ThrowingDataStore(true, false)).isGuest.first())
        }

    @Test
    fun flagStore_setGuest_ioFailure_isFailureResult() =
        runBlocking(Dispatchers.Default) {
            val result = SessionFlagStore(ThrowingDataStore(false, true)).setGuest(true)
            assertTrue(result.exceptionOrNull() is IOException)
        }

    @Test
    fun continueAsGuestAndLeaveGuestMode_surfaceWriteFailure_withoutThrowing() =
        runBlocking(Dispatchers.Default) {
            val sessions = repo(ThrowingDataStore(failRead = false, failWrite = true))
            assertTrue(sessions.continueAsGuest().isFailure)
            assertTrue(sessions.leaveGuestMode().isFailure)
        }

    @Test
    fun signIn_success_staysSuccess_whenFlagWriteFails() =
        runBlocking(Dispatchers.Default) {
            val auth = FakeAuthRepository(nextSignInResult = Result.success(user))
            val sessions = repo(ThrowingDataStore(false, true), auth)

            val result = sessions.signIn(android.content.ContextWrapper(null))

            assertEquals(user, result.getOrThrow())
            assertEquals(Session.SignedIn(user), sessions.resolved())
        }

    @Test
    fun signOut_success_staysSuccess_whenFlagWriteFails() =
        runBlocking(Dispatchers.Default) {
            val sessions = repo(ThrowingDataStore(false, true), FakeAuthRepository(initialUser = user))

            assertTrue(sessions.signOut().isSuccess)
        }

    private fun isResolved(session: Session) = session != Session.Loading

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
