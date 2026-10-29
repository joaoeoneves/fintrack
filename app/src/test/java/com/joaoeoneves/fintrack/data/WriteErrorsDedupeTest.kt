package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.joaoeoneves.fintrack.ui.navigation.WriteErrorKind
import com.joaoeoneves.fintrack.ui.navigation.writeErrorKindOf
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

/** The 5 s window of [WriteErrors], per code, and the code-to-message-kind mapping. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WriteErrorsDedupeTest {
    // Far above any real elapsedRealtime so state left by other tests (a global object) never interferes.
    private val base = 1_000_000_000_000L

    private fun events(block: () -> Unit): List<WriteErrorCode> {
        val seen = mutableListOf<WriteErrorCode>()
        runTest(UnconfinedTestDispatcher()) {
            val job = backgroundScope.launch { WriteErrors.events.toList(seen) }
            block()
            job.cancel()
        }
        return seen
    }

    private val denied = FirebaseFirestoreException("no", Code.PERMISSION_DENIED)

    @Test
    fun sameCode_isEmittedAgainOnlyAfterFiveSeconds() {
        val seen =
            events {
                WriteErrors.emit(denied, base)
                WriteErrors.emit(denied, base + 4_999)
                WriteErrors.emit(denied, base + 5_000)
            }

        assertEquals(listOf(WriteErrorCode.PERMISSION_DENIED, WriteErrorCode.PERMISSION_DENIED), seen)
    }

    @Test
    fun differentCodes_doNotSuppressEachOther() {
        val seen =
            events {
                WriteErrors.emit(denied, base * 2)
                WriteErrors.emit(IllegalStateException("x"), base * 2 + 1)
            }

        assertEquals(listOf(WriteErrorCode.PERMISSION_DENIED, WriteErrorCode.OTHER), seen)
    }

    @Test
    fun notifyFalse_onlyLogs() {
        val seen = events { onFail("t", "auto write", notify = false).onFailure(denied) }

        assertTrue(seen.isEmpty())
    }

    @Test
    fun kindOf_mapsEachCode() {
        assertEquals(WriteErrorKind.PERMISSION, writeErrorKindOf(WriteErrorCode.PERMISSION_DENIED))
        assertEquals(WriteErrorKind.OTHER, writeErrorKindOf(WriteErrorCode.OTHER))
    }
}
