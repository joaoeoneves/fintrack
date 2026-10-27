package com.joaoeoneves.fintrack.data.session

import android.content.Context
import android.util.Log
import com.joaoeoneves.fintrack.di.ApplicationScope
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.repository.AuthRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SessionRepository"

/** Combines the Firebase user with the persisted guest flag; an account always wins over the flag. */
@Singleton
class DefaultSessionRepository
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val flagStore: SessionFlagStore,
        @ApplicationScope scope: CoroutineScope,
    ) : SessionRepository {
        override val session: StateFlow<Session> =
            combine(authRepository.observeCurrentUser(), flagStore.isGuest) { user, guest ->
                when {
                    user != null -> Session.SignedIn(user)
                    guest -> Session.Guest
                    else -> Session.SignedOut
                }
            }.stateIn(scope, SharingStarted.Eagerly, Session.Loading)

        override suspend fun continueAsGuest(): Result<Unit> = flagStore.setGuest(true)

        override suspend fun leaveGuestMode(): Result<Unit> = flagStore.setGuest(false)

        // The account wins over the flag: a failed flag clear must not fail a successful sign-in/out.
        override suspend fun signIn(context: Context): Result<AuthUser> {
            val result = authRepository.signIn(context)
            if (result.isSuccess) clearFlagBestEffort()
            return result
        }

        override suspend fun signOut(): Result<Unit> = authRepository.signOut().onSuccess { clearFlagBestEffort() }

        private suspend fun clearFlagBestEffort() {
            flagStore.setGuest(false).onFailure { Log.w(TAG, "Could not clear the guest flag", it) }
        }
    }
