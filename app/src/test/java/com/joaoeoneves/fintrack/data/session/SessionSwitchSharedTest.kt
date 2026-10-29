package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.domain.model.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [SessionSwitch.shared]: one shared upstream, replay, source tagging and the 5s stop timeout. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SessionSwitchSharedTest {
    private class Source(
        val name: String,
        val values: MutableStateFlow<Int>,
    ) {
        var subscriptions = 0
        var cancelled = 0

        fun stream(): Flow<Int> =
            flow {
                subscriptions++
                values.collect { emit(it) }
            }.onCompletion { cancelled++ }
    }

    private val local = Source("local", MutableStateFlow(1))
    private val remote = Source("remote", MutableStateFlow(100))
    private val sessions = FakeSessionRepository(initialSession = Session.Guest)

    private val switch = SessionSwitch(sessions, CountingLazy(remote), local)

    private fun user() =
        Session.SignedIn(
            com.joaoeoneves.fintrack.domain.model
                .AuthUser("u1", null, null, null),
        )

    private fun TestScope.shared(): Flow<Int> =
        switch.shared(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()), -1) { it.stream() }

    @Test
    fun twoCollectors_shareOneUpstream() =
        runTest(UnconfinedTestDispatcher()) {
            val flow = shared()
            val a = mutableListOf<Int>()
            val b = mutableListOf<Int>()
            backgroundScope.launch { flow.collect { a += it } }
            backgroundScope.launch { flow.collect { b += it } }
            assertEquals(1, local.subscriptions)
            assertEquals(listOf(1), a)
            assertEquals(listOf(1), b)
            local.values.value = 2
            assertEquals(listOf(1, 2), a)
            assertEquals(listOf(1, 2), b)
        }

    @Test
    fun lateCollector_getsTheReplay_withoutANewUpstream() =
        runTest(UnconfinedTestDispatcher()) {
            val flow = shared()
            backgroundScope.launch { flow.collect {} }
            local.values.value = 5
            assertEquals(5, flow.first())
            assertEquals(1, local.subscriptions)
        }

    @Test
    fun afterSourceSwitch_noPreviousSessionValueIsDelivered() =
        runTest(UnconfinedTestDispatcher()) {
            val flow = shared()
            val seen = mutableListOf<Int>()
            backgroundScope.launch { flow.collect { seen += it } }
            sessions.emit(user())
            assertEquals(listOf(1, 100), seen)
            sessions.emit(Session.Guest)
            assertEquals(listOf(1, 100, 1), seen)
        }

    @Test
    fun upstream_isCancelledAfterFiveSecondsWithoutCollectors_andNoStaleReplay() =
        runTest(UnconfinedTestDispatcher()) {
            val flow = shared()
            val job = launch { flow.collect {} }
            job.cancel()
            advanceTimeBy(4_000)
            runCurrent()
            assertEquals(0, local.cancelled)
            advanceTimeBy(1_500)
            runCurrent()
            assertEquals(1, local.cancelled)
            local.values.value = 7
            // Re-collecting restarts the upstream and sees the fresh value first, not the old replay.
            assertEquals(7, flow.first())
            assertEquals(2, local.subscriptions)
        }

    @Test
    fun collectorReturningWithinTimeout_keepsTheUpstream() =
        runTest(UnconfinedTestDispatcher()) {
            val flow = shared()
            val job = launch { flow.collect {} }
            job.cancel()
            advanceTimeBy(2_000)
            assertEquals(1, flow.first())
            assertEquals(1, local.subscriptions)
        }

    @Test
    fun localFailure_emitsFallback() =
        runTest(UnconfinedTestDispatcher()) {
            val failing = SessionSwitch(sessions, CountingLazy(remote), local)
            val flow =
                failing.shared(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()), -1) {
                    flow<Int> { error("unreadable guest file") }
                }
            assertEquals(-1, flow.first())
            assertTrue(remote.subscriptions == 0)
        }
}
