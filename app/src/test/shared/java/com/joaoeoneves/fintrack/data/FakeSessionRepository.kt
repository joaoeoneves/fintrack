package com.joaoeoneves.fintrack.data

import android.content.Context
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [SessionRepository] for tests. Starts as [initialSession] (or signed in as [initialUser]
 * when given); successful sign-in / sign-out / guest calls move [session] like the real one does, and
 * every call is recorded.
 */
class FakeSessionRepository(
    initialSession: Session = Session.SignedOut,
    initialUser: AuthUser? = null,
    var nextSignInResult: Result<AuthUser> =
        Result.success(
            AuthUser(uid = "fake-uid", displayName = "Fake User", email = "fake@example.com", photoUrl = null),
        ),
    var nextSignOutResult: Result<Unit> = Result.success(Unit),
    var nextContinueAsGuestResult: Result<Unit> = Result.success(Unit),
    var nextLeaveGuestModeResult: Result<Unit> = Result.success(Unit),
) : SessionRepository {
    private val state = MutableStateFlow(initialUser?.let { Session.SignedIn(it) } ?: initialSession)
    override val session: StateFlow<Session> = state.asStateFlow()

    var continueAsGuestCalls = 0
        private set
    var leaveGuestModeCalls = 0
        private set
    var signInCalls = 0
        private set
    var signOutCalls = 0
        private set

    /** Pushes a session as if the account/flag changed externally. */
    fun emit(session: Session) {
        state.value = session
    }

    override suspend fun continueAsGuest(): Result<Unit> {
        continueAsGuestCalls++
        nextContinueAsGuestResult.onSuccess { state.value = Session.Guest }
        return nextContinueAsGuestResult
    }

    override suspend fun leaveGuestMode(): Result<Unit> {
        leaveGuestModeCalls++
        nextLeaveGuestModeResult.onSuccess { state.value = Session.SignedOut }
        return nextLeaveGuestModeResult
    }

    override suspend fun signIn(context: Context): Result<AuthUser> {
        signInCalls++
        nextSignInResult.onSuccess { state.value = Session.SignedIn(it) }
        return nextSignInResult
    }

    override suspend fun signOut(): Result<Unit> {
        signOutCalls++
        nextSignOutResult.onSuccess { state.value = Session.SignedOut }
        return nextSignOutResult
    }
}
