package com.joaoeoneves.fintrack.domain.repository

import android.content.Context
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.Session
import kotlinx.coroutines.flow.StateFlow

/** Single source of truth for the signed-in / guest / signed-out state. UI uses this, not [AuthRepository]. */
interface SessionRepository {
    val session: StateFlow<Session>

    /** Fails if the choice could not be persisted; the session is then unchanged. */
    suspend fun continueAsGuest(): Result<Unit>

    /** Leaves guest mode (the local data stays on the device). */
    suspend fun leaveGuestMode(): Result<Unit>

    /** Signs in with Google; on success the guest flag is cleared. */
    suspend fun signIn(context: Context): Result<AuthUser>

    /** Signs out of the account and clears the guest flag, so the sign-in screen shows. */
    suspend fun signOut(): Result<Unit>
}
