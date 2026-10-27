package com.joaoeoneves.fintrack.domain.model

/**
 * Signals that the user dismissed the sign-in flow (e.g. closed the account picker). Not an error
 * to surface: the UI simply returns to its idle state.
 */
class SignInCancelledException(
    cause: Throwable? = null,
) : Exception("Sign-in cancelled", cause)
