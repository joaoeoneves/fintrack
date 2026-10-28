package com.joaoeoneves.fintrack.data.session

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.data.FirestoreExpenseRepository
import com.joaoeoneves.fintrack.data.local.LocalExpenseRepository
import com.joaoeoneves.fintrack.data.local.LocalStoreTestBase
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import dagger.Lazy
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** `observeHasAnyExpense` on the session-switched repository and on the Firestore one without a signed-in user. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SessionExpenseRepositoryHasAnyTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }

    private fun firestoreExpenses(): FirestoreExpenseRepository {
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        return FirestoreExpenseRepository(FirebaseFirestore.getInstance(app), FirebaseAuth.getInstance(app))
    }

    private fun sessionRepo(
        session: Session,
        remote: Lazy<FirestoreExpenseRepository>,
    ) = SessionExpenseRepository(
        FakeSessionRepository(initialSession = session),
        remote,
        LocalExpenseRepository(store, SequenceIds()),
    )

    @Test
    fun guest_readsTheLocalStore_andNeverResolvesFirestore() =
        runStoreTest {
            val remote = CountingLazy<FirestoreExpenseRepository>(null)
            val repo = sessionRepo(Session.Guest, remote)
            assertFalse(repo.observeHasAnyExpense().first())

            repo.addExpense(expense(id = ""))

            assertTrue(repo.observeHasAnyExpense().first())
            assertEquals(0, remote.gets)
        }

    @Test
    fun signedOut_goesToFirestore_whichReportsFalse() =
        runStoreTest {
            val remote = CountingLazy(firestoreExpenses())
            val repo = sessionRepo(Session.SignedOut, remote)

            assertFalse(repo.observeHasAnyExpense().first())
            assertEquals(1, remote.gets)
        }

    @Test
    fun firestore_withoutAUser_isFalse() =
        runStoreTest {
            assertFalse(firestoreExpenses().observeHasAnyExpense().first())
        }
}
