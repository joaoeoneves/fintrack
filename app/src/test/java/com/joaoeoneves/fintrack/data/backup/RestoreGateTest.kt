package com.joaoeoneves.fintrack.data.backup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreGateTest {
    private val gate = RestoreGate()

    @Test
    fun startsIdleAtGenerationZero() {
        assertFalse(gate.restoring.value)
        assertEquals(0, gate.generation.value)
    }

    @Test
    fun hold_raisesRestoringDuringTheBlock_andReleasesAfter() =
        runTest {
            var during = false

            val result =
                gate.hold {
                    during = gate.restoring.value
                    "done"
                }

            assertTrue(during)
            assertEquals("done", result)
            assertFalse(gate.restoring.value)
            assertEquals(1, gate.generation.value)
        }

    @Test
    fun hold_releasesAndBumpsGeneration_whenTheBlockThrows() =
        runTest {
            val error = runCatching { gate.hold<Unit> { throw IllegalStateException("boom") } }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertEquals("boom", error?.message)
            assertFalse(gate.restoring.value)
            assertEquals(1, gate.generation.value)
        }

    @Test
    fun hold_releasesAndBumpsGeneration_whenCancelled() =
        runTest(UnconfinedTestDispatcher()) {
            val job = launch { gate.hold<Unit> { awaitCancellation() } }
            runCurrent()
            assertTrue(gate.restoring.value)

            job.cancelAndJoin()

            assertFalse(gate.restoring.value)
            assertEquals(1, gate.generation.value)
        }

    @Test
    fun generation_bumpsOncePerHold() =
        runTest {
            repeat(3) { gate.hold { } }

            assertEquals(3, gate.generation.value)
        }

    @Test
    fun nestedHolds_stayRestoringUntilTheOuterOneEnds() =
        runTest {
            val states = mutableListOf<Boolean>()

            gate.hold {
                gate.hold { states += gate.restoring.value }
                states += gate.restoring.value
            }
            states += gate.restoring.value

            assertEquals(listOf(true, true, false), states)
            assertEquals(2, gate.generation.value)
        }

    @Test
    fun overlappingHolds_stayRestoringUntilTheLastOneEnds() =
        runTest(UnconfinedTestDispatcher()) {
            val first = CompletableDeferred<Unit>()
            val second = CompletableDeferred<Unit>()
            val a = launch { gate.hold { first.await() } }
            val b = launch { gate.hold { second.await() } }
            runCurrent()

            first.complete(Unit)
            a.join()
            assertTrue(gate.restoring.value)

            second.complete(Unit)
            b.join()
            assertFalse(gate.restoring.value)
        }

    @Test
    fun generation_changesBeforeRestoringGoesBackToFalse() =
        runTest(UnconfinedTestDispatcher()) {
            val seen = mutableListOf<Pair<Boolean, Int>>()
            val watcher =
                launch {
                    gate.restoring.collect { seen += it to gate.generation.value }
                }
            runCurrent()

            gate.hold { }

            watcher.cancelAndJoin()
            assertEquals(listOf(false to 0, true to 0, false to 1), seen)
        }

    @Test
    fun gatedBy_carriesTheValueAndTheGateState() =
        runTest(UnconfinedTestDispatcher()) {
            val first = flowOf("x").gatedBy(gate).first()

            assertEquals("x", first.value)
            assertFalse(first.restoring)
            assertEquals(0, first.generation)

            val release = CompletableDeferred<Unit>()
            val job = launch { gate.hold { release.await() } }
            runCurrent()
            val during = flowOf("y").gatedBy(gate).first()
            release.complete(Unit)
            job.join()

            assertTrue(during.restoring)
            val after = flowOf("z").gatedBy(gate).first()
            assertFalse(after.restoring)
            assertEquals(1, after.generation)
        }
}
