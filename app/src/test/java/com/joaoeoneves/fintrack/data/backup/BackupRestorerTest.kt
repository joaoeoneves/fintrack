package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [BackupRestorer] picks the guest or the account restorer by session and wraps either in the [RestoreGate]. */
class BackupRestorerTest : BackupRepositoryTestBase() {
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
            val partial = AccountRestoreResult.Partial(3, 10, IllegalStateException("x"))
            account.answer(partial)

            val result = restorer.restore(doc) { _, _ -> }

            assertEquals(partial, result)
            assertEquals(listOf(doc), account.calls)
            val stored = localStore.data.first()
            assertEquals(listOf("keep"), stored.categories.map { it.id })
            assertTrue(stored.expenses.isEmpty())
        }

    @Test
    fun guest_neverCallsTheAccountSide() =
        runStoreTest {
            restorer.restore(doc) { _, _ -> }

            assertTrue(account.calls.isEmpty())
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
            assertTrue(account.calls.isEmpty())
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
