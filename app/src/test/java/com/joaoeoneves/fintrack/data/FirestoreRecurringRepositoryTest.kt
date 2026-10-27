package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/** [FirestoreRecurringRepository] while signed out (the signed-in paths need a Firestore backend). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirestoreRecurringRepositoryTest {
    private lateinit var repository: FirestoreRecurringRepository

    @Before
    fun setUp() {
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        val auth = FirebaseAuth.getInstance(app)
        auth.signOut()
        repository = FirestoreRecurringRepository(FirebaseFirestore.getInstance(app), auth)
    }

    private fun assertNotSignedIn(result: Result<*>) {
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("Not signed in", result.exceptionOrNull()?.message)
    }

    @Test
    fun signedOut_observeEmitsEmptyList() =
        runTest {
            assertEquals(emptyList<Any>(), repository.observeRecurring().first())
        }

    @Test
    fun signedOut_saveAndDeleteFail() =
        runTest {
            assertNotSignedIn(repository.saveRecurring(recurringItem()))
            assertNotSignedIn(repository.deleteRecurring("x"))
        }

    @Test
    fun signedOut_setHandledThroughAndConfirmFail() =
        runTest {
            assertNotSignedIn(repository.setHandledThrough("x", day(2026, 3, 15)))
            val tx = testExpense("rec_x_20260315", 1L, Instant.parse("2026-03-15T12:00:00Z"))
            assertNotSignedIn(repository.confirmOccurrence("x", day(2026, 3, 15), tx))
        }
}
