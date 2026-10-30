package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.domain.model.Session
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionServerSettledTest {
    private fun settled(
        session: Session,
        remote: CountingLazy<FirestoreServerSettled>,
    ) = SessionServerSettled(FakeSessionRepository(initialSession = session), remote, LocalServerSettled())

    @Test
    fun guest_returnsAtOnce_andNeverResolvesFirestore() =
        runTest {
            val remote = CountingLazy<FirestoreServerSettled>(null)

            settled(Session.Guest, remote).awaitPendingWrites()

            assertEquals(0, remote.gets)
        }

    @Test
    fun signedOut_goesToTheRemote() {
        run {
            val remote = CountingLazy<FirestoreServerSettled>(null)

            // The remote placeholder is null, so resolving it fails: proof that the switch picked the server side.
            assertThrows(IllegalStateException::class.java) {
                runBlocking { settled(Session.SignedOut, remote).awaitPendingWrites() }
            }
            assertEquals(1, remote.gets)
        }
    }

    @Test
    fun localServerSettled_isANoOp() =
        runTest {
            LocalServerSettled().awaitPendingWrites()
        }
}
