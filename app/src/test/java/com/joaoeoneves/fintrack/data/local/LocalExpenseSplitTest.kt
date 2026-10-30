package com.joaoeoneves.fintrack.data.local

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 5.9 split reads/writes and the awaiting-refund flag in the guest store. */
class LocalExpenseSplitTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalExpenseRepository(store, SequenceIds()) }

    private fun part(
        id: String,
        cents: Long = 1000,
        split: String? = "grp",
    ) = expense(id, amountCents = cents).copy(splitId = split)

    @Test
    fun getSplit_returnsOnlyTheSiblings_andEmptyForUnknownId() =
        runStoreTest {
            repo.addExpensesKeepingIds(listOf(part("a"), part("b"), part("c", split = "other"), part("d", split = null)))

            assertEquals(listOf("a", "b"), repo.getSplit("grp").getOrThrow().map { it.id }.sorted())
            assertEquals(emptyList<String>(), repo.getSplit("nope").getOrThrow().map { it.id })
        }

    @Test
    fun replaceSplit_updatesAddsAndDeletes_inOneRevision() =
        runStoreTest {
            repo.addExpensesKeepingIds(listOf(part("a"), part("b"), part("c")))
            assertEquals(1L, diskDoc().revision)

            val result =
                repo.replaceSplit(
                    update = listOf(part("a", cents = 700)),
                    add = listOf(part("", cents = 300), part("n", cents = 100)),
                    deleteIds = listOf("b"),
                )

            assertTrue(result.isSuccess)
            assertEquals(2L, diskDoc().revision)
            val byId = diskDoc().expenses.associateBy { it.id }
            assertEquals(setOf("a", "c", "id1", "n"), byId.keys)
            assertEquals(700L, byId.getValue("a").amountCents)
            assertEquals(300L, byId.getValue("id1").amountCents)
        }

    @Test
    fun replaceSplit_unknownUpdateOrInvalidPart_writesNothing() =
        runStoreTest {
            repo.addExpensesKeepingIds(listOf(part("a"), part("b")))
            val before = store.data.first()

            assertTrue(repo.replaceSplit(listOf(part("ghost")), emptyList(), listOf("a")).isFailure)
            assertTrue(repo.replaceSplit(emptyList(), listOf(part("x", cents = 0)), listOf("a")).isFailure)

            assertEquals(before, store.data.first())
        }

    @Test
    fun replaceSplit_keepingOnePartAsPlain_clearsItsSplitId() =
        runStoreTest {
            repo.addExpensesKeepingIds(listOf(part("a"), part("b")))

            repo.replaceSplit(listOf(part("a", split = null)), emptyList(), listOf("b"))

            assertEquals(null, repo.getExpense("a").getOrThrow()?.splitId)
            assertEquals(emptyList<String>(), repo.getSplit("grp").getOrThrow().map { it.id })
        }

    @Test
    fun observeAwaitingRefund_listsFlaggedExpenses_andPersistsTheFlag() =
        runStoreTest {
            repo.addExpensesKeepingIds(listOf(expense("a").copy(awaitingRefund = true), expense("b")))

            assertEquals(listOf("a"), repo.observeAwaitingRefund().first().map { it.id })
            assertEquals(listOf(true, false), diskDoc().expenses.sortedBy { it.id }.map { it.awaitingRefund })

            repo.updateExpense(expense("a").copy(awaitingRefund = false))
            assertEquals(emptyList<String>(), repo.observeAwaitingRefund().first().map { it.id })
        }
}
