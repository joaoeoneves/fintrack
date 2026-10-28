package com.joaoeoneves.fintrack.data.backup

import android.app.Application
import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.testutil.unsignedAccountRestorer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** [BackupRestorer] picks the guest or the account restorer by session and wraps either in the [RestoreGate]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupRestorerTest : BackupRepositoryTestBase() {
    override val accountRestorer by lazy { unsignedAccountRestorer(RuntimeEnvironment.getApplication()) }

    private val doc = backupDoc(expenses = listOf(backupExpense("e1")))
    private val signedIn = Session.SignedIn(AuthUser("u", null, null, null))

    @Test
    fun guest_restoresIntoTheLocalStore_andReportsSuccess() =
        runStoreTest {
            val result = restorer.restore(doc) { _, _ -> }

            assertEquals(AccountRestoreResult.Success, result)
            assertEquals(
                listOf("e1"),
                localStore.data
                    .first()
                    .expenses
                    .map { it.id },
            )
        }

    @Test
    fun signedIn_goesToTheAccountSide_notTheGuestStore() =
        runStoreTest {
            localStore.seedCategory("keep")
            sessions.emit(signedIn)

            val result = restorer.restore(doc) { _, _ -> }

            // The account restorer ran (nobody is signed in to Firebase here) and the guest file is untouched.
            val failed = result as AccountRestoreResult.Failed
            assertEquals("Not signed in", failed.cause.message)
            val stored = localStore.data.first()
            assertEquals(listOf("keep"), stored.categories.map { it.id })
            assertTrue(stored.expenses.isEmpty())
        }

    @Test
    fun signedOut_orLoading_failsWithoutTouchingAnything() =
        runStoreTest {
            localStore.seedCategory("keep")
            for (session in listOf(Session.SignedOut, Session.Loading)) {
                sessions.emit(session)

                val result = restorer.restore(doc) { _, _ -> }

                assertTrue(result is AccountRestoreResult.Failed)
            }
            assertEquals(
                listOf("keep"),
                localStore.data
                    .first()
                    .categories
                    .map { it.id },
            )
        }

    @Test
    fun hasData_account_isAlwaysTrue_guest_followsTheStore() =
        runStoreTest {
            assertFalse(restorer.hasData())

            localStore.seedCategory("x")
            assertTrue(restorer.hasData())

            localStore.replaceAll(LocalDataDocument())
            sessions.emit(signedIn)
            assertTrue(restorer.hasData())
        }

    @Test
    fun theGateIsHeldForTheWholeRestore_andOpenedAfter() =
        runStoreTest {
            val seen = mutableListOf<Boolean>()
            val watcher =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    gate.restoring.toList(seen)
                }

            restorer.restore(doc) { _, _ -> }
            watcher.cancel()

            assertEquals(listOf(false, true, false), seen)
            assertEquals(1, gate.generation.value)
        }

    @Test
    fun theGateIsReleased_evenWhenTheRestoreFails() =
        runStoreTest {
            sessions.emit(Session.SignedOut)

            restorer.restore(doc) { _, _ -> }

            assertFalse(gate.restoring.value)
            assertEquals(1, gate.generation.value)
        }
}
