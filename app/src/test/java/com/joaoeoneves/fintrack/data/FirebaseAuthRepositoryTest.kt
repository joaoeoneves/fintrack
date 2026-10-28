package com.joaoeoneves.fintrack.data

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FirebaseAuthRepository.combineSignOutResults], the pure function that drives
 * `signOut()`'s overall [Result]. Only the Firebase sign-out outcome decides it: a failure to clear
 * the credential state is logged by the caller and never masks a successful sign-out.
 */
class FirebaseAuthRepositoryTest {
    @Test
    fun firebaseSignOutSucceeds_reportsSuccess() {
        val combined = FirebaseAuthRepository.combineSignOutResults(Result.success(Unit))

        assertTrue(combined.isSuccess)
    }

    @Test
    fun firebaseSignOutFails_reportsFailure_withExactSignOutException() {
        val signOutException = IllegalStateException("firebase signOut failed")

        val combined = FirebaseAuthRepository.combineSignOutResults(Result.failure(signOutException))

        assertTrue(combined.isFailure)
        assertSame(signOutException, combined.exceptionOrNull())
    }
}
