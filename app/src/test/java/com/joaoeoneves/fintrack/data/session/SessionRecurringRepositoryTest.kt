package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.data.FirestoreRecurringRepository
import com.joaoeoneves.fintrack.data.local.LocalRecurringRepository
import com.joaoeoneves.fintrack.data.local.LocalStoreTestBase
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SessionRecurringRepository] as a guest: local only, the Firestore Lazy must never be resolved. */
class SessionRecurringRepositoryTest : LocalStoreTestBase() {
    private val sessions = FakeSessionRepository(initialSession = Session.Guest)
    private val remote = CountingLazy<FirestoreRecurringRepository>(null)
    private val repo by lazy {
        SessionRecurringRepository(
            sessions,
            CoroutineScope(Dispatchers.Unconfined),
            remote,
            LocalRecurringRepository(newStore(), SequenceIds("g")),
        )
    }

    @Test
    fun guest_observeEmitsLocalItems_withoutResolvingRemote() =
        runStoreTest {
            assertEquals(emptyList<Any>(), repo.observeRecurring().first())
            assertEquals(0, remote.gets)
        }

    @Test
    fun guest_saveAndDelete_roundTripLocally() =
        runStoreTest {
            val id = repo.saveRecurring(recurringItem(id = "")).getOrThrow()

            assertEquals(listOf(recurringItem(id = id)), repo.observeRecurring().first())
            assertTrue(repo.deleteRecurring(id).isSuccess)
            assertTrue(repo.observeRecurring().first().isEmpty())
            assertEquals(0, remote.gets)
        }

    @Test
    fun guest_setHandledThroughAndConfirm_routeLocally() =
        runStoreTest {
            val id = repo.saveRecurring(recurringItem(id = "")).getOrThrow()

            assertTrue(repo.setHandledThrough(id, day(2026, 2, 15)).isSuccess)
            assertTrue(repo.confirmOccurrence(id, day(2026, 3, 15), expense(id = "rec_${id}_20260315")).isSuccess)

            val items = repo.observeRecurring().first()
            assertEquals(day(2026, 3, 15), items.single().handledThrough)
            assertEquals(0, remote.gets)
        }

    @Test
    fun guest_invalidItem_failsThroughLocalValidation() =
        runStoreTest {
            assertTrue(repo.saveRecurring(recurringItem(amountCents = 0)).isFailure)
        }
}
