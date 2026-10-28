package com.joaoeoneves.fintrack.data.automation

import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.usecase.AutoPostRecurring
import com.joaoeoneves.fintrack.domain.usecase.HandleRecurringOccurrence
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** [AutomationSync]: when a pass runs (session, foreground, data) and when it is not repeated. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutomationSyncTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-20T10:00:00Z"), ZoneId.of("UTC"))

    private fun due() = recurringItem(id = "r1", handledThrough = day(2026, 2, 15), autoPost = true)

    private fun TestScope.sync(
        session: FakeSessionRepository,
        repo: FakeRecurringRepository,
    ) = AutomationSync(
        session,
        repo,
        AutoPostRecurring(
            repo,
            HandleRecurringOccurrence(repo, FakeCategoryRepository()),
            FakeExpenseRepository(),
            FakeIncomeRepository(),
        ),
        clock,
        backgroundScope,
    )

    private fun guest() = FakeSessionRepository(initialSession = Session.Guest)

    @Test
    fun guest_runsOnStart() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeRecurringRepository(listOf(due()))

            sync(guest(), repo).start()
            advanceUntilIdle()

            assertEquals(listOf("rec_r1_20260315"), repo.confirmedTransactions.map { it.id })
        }

    @Test
    fun signedOut_doesNothing_untilSessionChanges() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeRecurringRepository(listOf(due()))
            val session = FakeSessionRepository(initialSession = Session.SignedOut)

            sync(session, repo).start()
            advanceUntilIdle()
            assertTrue(repo.confirmedTransactions.isEmpty())

            session.emit(Session.Guest)
            advanceUntilIdle()
            assertEquals(1, repo.confirmedTransactions.size)
        }

    @Test
    fun onForeground_rerunsForNewlyDueWork() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeRecurringRepository(listOf(due()))
            val sync = sync(guest(), repo)
            sync.start()
            advanceUntilIdle()
            repo.nextConfirmError = null

            sync.onForeground()
            advanceUntilIdle()

            // Same attempt: nothing new, and no duplicates either.
            assertEquals(1, repo.confirmedTransactions.size)
        }

    @Test
    fun startTwice_keepsOneCollector() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeRecurringRepository(listOf(due()))
            val sync = sync(guest(), repo)

            sync.start()
            sync.start()
            advanceUntilIdle()

            assertEquals(1, repo.confirmedTransactions.size)
        }

    @Test
    fun identicalFailingAttempt_isNotRetried_onForeground() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeRecurringRepository(listOf(due()), nextConfirmError = IllegalStateException("down"))
            val sync = sync(guest(), repo)
            sync.start()
            advanceUntilIdle()
            assertTrue(repo.confirmedTransactions.isEmpty())

            sync.onForeground()
            advanceUntilIdle()

            // The failure was one-shot; a retry would have posted. Same inputs: not attempted again.
            assertTrue(repo.confirmedTransactions.isEmpty())
        }

    @Test
    fun changedRecurringData_triggersANewAttempt() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeRecurringRepository(listOf(due()), nextConfirmError = IllegalStateException("down"))
            sync(guest(), repo).start()
            advanceUntilIdle()
            assertTrue(repo.confirmedTransactions.isEmpty())

            repo.setItems(listOf(due().copy(handledThrough = day(2026, 1, 15))))
            advanceUntilIdle()

            assertEquals(2, repo.confirmedTransactions.size)
        }
}
