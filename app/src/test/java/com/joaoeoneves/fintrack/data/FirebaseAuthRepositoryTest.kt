package com.joaoeoneves.fintrack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FirebaseAuthRepository.combineSignOutResults], the pure decision function that
 * drives `signOut()`'s overall [Result].
 *
 * Scope note: `FirebaseAuthRepository.signOut()` itself calls `credentialManager
 * .clearCredentialState()` and `firebaseAuth.signOut()` as two independent `runCatching` blocks --
 * i.e. each always runs regardless of whether the other throws. That structural guarantee (no
 * short-circuiting between the two) is verifiable by reading the production code directly and is
 * NOT itself exercised by a test here, since `FirebaseAuth`/`CredentialManager` cannot be faked
 * without a mocking library (MockK/Mockito), which this project's test dependencies do not include
 * and which is out of scope for this fix. What *is* fully covered below is the pure combination
 * logic: given any pair of outcomes for those two independent calls, does `signOut()` report the
 * correct overall result?
 */
class FirebaseAuthRepositoryTest {
    @Test
    fun bothSucceed_reportsSuccess() {
        val clear = Result.success(Unit)
        val signOut = Result.success(Unit)

        val combined = FirebaseAuthRepository.combineSignOutResults(clear, signOut)

        assertTrue(combined.isSuccess)
    }

    @Test
    fun clearCredentialFails_firebaseSignOutSucceeds_stillReportsSuccess_coreBugFixRegression() {
        // This is the exact scenario the fix targets: clearing the credential state failed, but
        // the user genuinely IS signed out of Firebase. Before the fix, this combination was
        // (incorrectly) reported as an overall failure because the clear-credential exception
        // prevented firebaseAuth.signOut() from ever being reached. Now both calls always run
        // independently, and success here must be driven solely by the Firebase sign-out outcome.
        val clear = Result.failure<Unit>(IllegalStateException("credential clear failed"))
        val signOut = Result.success(Unit)

        val combined = FirebaseAuthRepository.combineSignOutResults(clear, signOut)

        assertTrue(
            "clear-credential failure must not mask a successful Firebase sign-out",
            combined.isSuccess,
        )
    }

    @Test
    fun clearCredentialSucceeds_firebaseSignOutFails_reportsFailure_withExactSignOutException() {
        val clear = Result.success(Unit)
        val signOutException = IllegalStateException("firebase signOut failed")
        val signOut = Result.failure<Unit>(signOutException)

        val combined = FirebaseAuthRepository.combineSignOutResults(clear, signOut)

        assertTrue(combined.isFailure)
        // Must be the exact same exception instance, not a generic/wrapped one.
        assertSame(signOutException, combined.exceptionOrNull())
    }

    @Test
    fun bothFail_reportsFailure_withFirebaseSignOutExceptionSurfaced_notClearCredentialException() {
        val clearException = IllegalStateException("credential clear failed")
        val signOutException = IllegalStateException("firebase signOut failed")
        val clear = Result.failure<Unit>(clearException)
        val signOut = Result.failure<Unit>(signOutException)

        val combined = FirebaseAuthRepository.combineSignOutResults(clear, signOut)

        assertTrue(combined.isFailure)
        assertSame(signOutException, combined.exceptionOrNull())
        assertEquals("firebase signOut failed", combined.exceptionOrNull()?.message)
    }
}
