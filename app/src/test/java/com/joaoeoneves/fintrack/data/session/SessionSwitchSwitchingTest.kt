package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SessionSwitchSwitchingTest : SessionSwitchTestBase() {
    private fun names(list: List<Expense>) = list.map { it.name }

    @Test
    fun modeChangeMidCollection_resubscribesToNewSource() =
        runTest(UnconfinedTestDispatcher()) {
            val (switch, sessions) = switchFor(Session.Guest)
            val seen = mutableListOf<List<String>>()
            backgroundScope.launch { switch.observe().collect { seen += names(it) } }

            sessions.emit(Session.SignedIn(user("u1")))
            sessions.emit(Session.Guest)

            assertEquals(listOf(listOf("local"), listOf("remote"), listOf("local")), seen)
        }

    @Test
    fun uidChange_resubscribes_butSameUidDoesNot() =
        runTest(UnconfinedTestDispatcher()) {
            val (switch, sessions) = switchFor(Session.SignedIn(user("u1")))
            var subscriptions = 0
            backgroundScope.launch {
                switch
                    .flow(emptyList<Expense>()) { repo ->
                        subscriptions++
                        repo.observeExpenses(from, to)
                    }.collect { }
            }
            assertEquals(1, subscriptions)

            sessions.emit(Session.SignedIn(user("u1").copy(displayName = "Renamed")))
            assertEquals(1, subscriptions)

            sessions.emit(Session.SignedIn(user("u2")))
            assertEquals(2, subscriptions)
        }

    @Test
    fun loading_blocksFlowUntilResolved() =
        runTest(UnconfinedTestDispatcher()) {
            val (switch, sessions) = switchFor(Session.Loading)
            val seen = mutableListOf<List<String>>()
            backgroundScope.launch { switch.observe().collect { seen += names(it) } }
            assertTrue(seen.isEmpty())

            sessions.emit(Session.Guest)
            assertEquals(listOf(listOf("local")), seen)
        }

    @Test
    fun loading_blocksCallUntilResolved_andUsesResolvedSource() =
        runTest(UnconfinedTestDispatcher()) {
            val (switch, sessions) = switchFor(Session.Loading)
            val pending = async { switch.call { it.getAllExpenses() } }
            advanceUntilIdle()
            assertFalse(pending.isCompleted)

            sessions.emit(Session.Guest)
            assertEquals(listOf("local"), names(pending.await().getOrThrow()))
            assertEquals(0, remote.gets)
        }

    @Test
    fun localFlowError_emitsFallback() =
        runTest(UnconfinedTestDispatcher()) {
            val (switch, _) = switchFor(Session.Guest)
            val failing =
                object : ExpenseRepository by localRepo {
                    override fun observeExpenses(
                        startInclusive: Instant,
                        endExclusive: Instant,
                    ): Flow<List<Expense>> = flow { throw java.io.IOException("unreadable") }
                }
            val sessions =
                com.joaoeoneves.fintrack.data
                    .FakeSessionRepository(initialSession = Session.Guest)
            val failingSwitch = SessionSwitch<ExpenseRepository>(sessions, remote, failing)
            val fallback = listOf(expense("fallback"))

            val result = failingSwitch.flow(fallback) { it.observeExpenses(from, to) }.first()

            assertEquals(fallback, result)
            assertEquals(0, remote.gets)
            assertEquals(listOf("local"), names(switch.observe().first()))
        }

    @Test
    fun remoteFlowError_isNotCaught() =
        runTest(UnconfinedTestDispatcher()) {
            val failingRemote =
                object : ExpenseRepository by remoteRepo {
                    override fun observeExpenses(
                        startInclusive: Instant,
                        endExclusive: Instant,
                    ): Flow<List<Expense>> = flow { throw java.io.IOException("server") }
                }
            val sessions =
                com.joaoeoneves.fintrack.data
                    .FakeSessionRepository(initialSession = Session.SignedOut)
            val switch = SessionSwitch<ExpenseRepository>(sessions, CountingLazy(failingRemote), localRepo)

            val result = runCatching { switch.observe().first() }

            assertTrue(result.exceptionOrNull() is java.io.IOException)
        }

    @Test
    fun cancellingCollector_ofLocalFlow_doesNotEmitFallback() =
        runTest(UnconfinedTestDispatcher()) {
            val (switch, _) = switchFor(Session.Guest)
            val seen = mutableListOf<List<Expense>>()
            val flow = switch.flow(listOf(expense("fallback"))) { it.observeExpenses(from, to) }
            val job = launch { flow.collect { seen += it } }

            job.cancel()
            job.join()

            assertEquals(listOf("local"), names(seen.single()))
        }
}
