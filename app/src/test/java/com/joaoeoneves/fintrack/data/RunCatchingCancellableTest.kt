package com.joaoeoneves.fintrack.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** Unit tests for the internal [runCatchingCancellable] helper used by the Firestore repositories. */
class RunCatchingCancellableTest {
    @Test
    fun success_wrapsReturnedValue() {
        val result = runCatchingCancellable { 42 }

        assertEquals(Result.success(42), result)
    }

    @Test
    fun success_withNullValue_isStillSuccess() {
        val result = runCatchingCancellable<String?> { null }

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
    }

    @Test
    fun checkedException_isWrappedAsFailure_sameInstance() {
        val boom = IOException("disk on fire")

        val result = runCatchingCancellable<Int> { throw boom }

        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun runtimeException_isWrappedAsFailure_sameInstance() {
        val boom = IllegalStateException("bad state")

        val result = runCatchingCancellable<Int> { throw boom }

        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun cancellationException_isRethrown_notWrapped() {
        val cancel = CancellationException("cancelled")

        try {
            runCatchingCancellable<Int> { throw cancel }
            fail("expected CancellationException to propagate")
        } catch (e: CancellationException) {
            assertSame(cancel, e)
        }
    }

    @Test
    fun timeoutCancellationException_subclass_isRethrown() {
        var caught: Throwable? = null
        try {
            runBlocking {
                withTimeout(1) {
                    runCatchingCancellable { awaitCancellation() }
                }
            }
        } catch (e: TimeoutCancellationException) {
            caught = e
        }

        assertTrue("expected TimeoutCancellationException, was $caught", caught is TimeoutCancellationException)
    }

    @Test
    fun cancelledCoroutine_staysCancelled_andCodeAfterTheCallNeverRuns() =
        runBlocking {
            var reachedAfter = false
            var wrappedResult: Result<Unit>? = null
            val job =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    wrappedResult = runCatchingCancellable { awaitCancellation() }
                    reachedAfter = true
                }

            job.cancel()
            job.join()
            yield()

            assertTrue(job.isCancelled)
            assertFalse("cancellation must not be swallowed into a Result", reachedAfter)
            assertEquals(null, wrappedResult)
        }
}
