package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A rejected not-awaited write tells the user; one that only failed offline (UNAVAILABLE) stays silent. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WriteErrorsTest {
    private fun events(block: () -> Unit): List<WriteErrorCode> {
        val seen = mutableListOf<WriteErrorCode>()
        runTest(UnconfinedTestDispatcher()) {
            val job = backgroundScope.launch { WriteErrors.events.toList(seen) }
            block()
            job.cancel()
        }
        return seen
    }

    private fun failure(code: Code) = FirebaseFirestoreException("rejected: $code", code)

    @Test
    fun permissionDenied_emitsAnEvent() {
        val seen = events { onFail("t", "write").onFailure(failure(Code.PERMISSION_DENIED)) }

        assertEquals(listOf(WriteErrorCode.PERMISSION_DENIED), seen)
    }

    @Test
    fun unavailable_emitsNothing() {
        val seen = events { onFail("t", "write").onFailure(failure(Code.UNAVAILABLE)) }

        assertTrue(seen.isEmpty())
    }

    @Test
    fun nonFirestoreError_emitsOther_andRepeatsCollapse() {
        val seen =
            events {
                onFail("t", "write").onFailure(IllegalStateException("boom"))
                onFail("t", "write").onFailure(IllegalStateException())
            }

        assertEquals(listOf(WriteErrorCode.OTHER), seen)
    }
}
