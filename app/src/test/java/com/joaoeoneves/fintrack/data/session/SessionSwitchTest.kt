package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.domain.model.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSwitchTest : SessionSwitchTestBase() {
    @Test
    fun guest_flowReadsLocal_andNeverResolvesRemote() =
        runTest {
            val (switch, _) = switchFor(Session.Guest)
            assertEquals(listOf("local"), switch.observe().first().map { it.name })
            assertEquals(0, remote.gets)
        }

    @Test
    fun guest_callGoesToLocal_andNeverResolvesRemote() =
        runTest {
            val (switch, _) = switchFor(Session.Guest)
            val id = switch.call { it.addExpense(expense("new")) }
            assertTrue(id.isSuccess)
            assertEquals(2, localRepo.getAllExpenses().getOrThrow().size)
            assertEquals(1, remoteRepo.getAllExpenses().getOrThrow().size)
            assertEquals(0, remote.gets)
        }

    @Test
    fun signedIn_flowAndCallsGoToRemote() =
        runTest {
            val (switch, _) = switchFor(Session.SignedIn(user("u1")))
            assertEquals(listOf("remote"), switch.observe().first().map { it.name })
            assertEquals(listOf("remote"), switch.call { it.getAllExpenses() }.getOrThrow().map { it.name })
        }

    @Test
    fun signedOut_usesRemote() =
        runTest {
            val (switch, _) = switchFor(Session.SignedOut)
            assertEquals(listOf("remote"), switch.observe().first().map { it.name })
        }

    @Test
    fun call_returnsResultOfBlock() =
        runTest {
            val (switch, _) = switchFor(Session.Guest)
            assertEquals(
                true,
                switch
                    .call {
                        it.hasExpenseWithCategory(
                            localRepo
                                .getAllExpenses()
                                .getOrThrow()[0]
                                .category.id,
                        )
                    }.getOrThrow(),
            )
        }

    @Test
    fun call_propagatesExceptionsFromBlock() =
        runTest {
            val (switch, _) = switchFor(Session.Guest)
            val result = runCatching { switch.call<Unit> { error("boom") } }
            assertEquals("boom", result.exceptionOrNull()?.message)
        }
}
