package com.joaoeoneves.fintrack.domain.model

/** Who the app is currently running as; decides whether data lives in Firestore or on the device. */
sealed interface Session {
    /** The persisted state has not been read yet. */
    data object Loading : Session

    data object SignedOut : Session

    /** Device-only mode: no account, data kept in the local guest store. */
    data object Guest : Session

    data class SignedIn(
        val user: AuthUser,
    ) : Session
}
