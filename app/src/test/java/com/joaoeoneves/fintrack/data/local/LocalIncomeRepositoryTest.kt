package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.repository.BulkAddResult
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LocalIncomeRepositoryTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalIncomeRepository(store, SequenceIds()) }
    private val start = Instant.parse("2026-03-01T00:00:00Z")
    private val end = Instant.parse("2026-04-01T00:00:00Z")

    @Test
    fun add_blankIdGenerates_givenIdPreserved_andUpserts() =
        runStoreTest {
            assertEquals("id1", repo.addIncome(income(id = "")).getOrThrow())
            assertEquals("g", repo.addIncome(income(id = "g", source = "A")).getOrThrow())
            repo.addIncome(income(id = "g", source = "B"))
            assertEquals(listOf("Salary", "B"), repo.getAllIncome().getOrThrow().map { it.source })
        }

    @Test
    fun observe_halfOpenRange_sortedAscending() =
        runStoreTest {
            repo.addIncome(income("atEnd", date = end))
            repo.addIncome(income("last", date = end.minusNanos(1)))
            repo.addIncome(income("first", date = start))
            repo.addIncome(income("before", date = start.minusNanos(1)))
            assertEquals(listOf("first", "last"), repo.observeIncome(start, end).first().map { it.id })
        }

    @Test
    fun observe_empty() =
        runStoreTest {
            assertEquals(emptyList<Any>(), repo.observeIncome(start, end).first())
        }

    @Test
    fun update_missingFails_existingReplaces_invalidKeepsOld() =
        runStoreTest {
            assertTrue(repo.updateIncome(income("ghost")).exceptionOrNull() is IllegalArgumentException)
            repo.addIncome(income("a", source = "Old"))
            assertTrue(repo.updateIncome(income("a", source = "New")).isSuccess)
            assertEquals("New", repo.getIncome("a").getOrThrow()?.source)
            assertTrue(repo.updateIncome(income("a", amountCents = -1)).isFailure)
            assertEquals("New", repo.getIncome("a").getOrThrow()?.source)
        }

    @Test
    fun delete_missingSucceedsNoRevisionBump_getMissingIsNull() =
        runStoreTest {
            repo.addIncome(income("a"))
            repo.deleteIncome("a")
            val revision = store.data.first().revision
            assertTrue(repo.deleteIncome("a").isSuccess)
            assertEquals(revision, store.data.first().revision)
            assertNull(repo.getIncome("a").getOrThrow())
        }

    @Test
    fun validation_boundaries() =
        runStoreTest {
            assertTrue(repo.addIncome(income("a", source = "s".repeat(200), note = "n".repeat(2000))).isSuccess)
            assertTrue(repo.addIncome(income("b", source = "")).isFailure)
            assertTrue(repo.addIncome(income("c", source = "s".repeat(201))).isFailure)
            assertTrue(repo.addIncome(income("d", amountCents = 0)).isFailure)
            assertTrue(repo.addIncome(income("e", amountCents = -5)).isFailure)
            assertTrue(repo.addIncome(income("f", note = "n".repeat(2001))).isFailure)
            assertEquals(listOf("a"), diskDoc().income.map { it.id })
        }

    @Test
    fun bulkAdd_newIdsSingleWrite() =
        runStoreTest {
            assertEquals(BulkAddResult(2, null), repo.addIncomeList(listOf(income("x"), income("y"))))
            assertEquals(listOf("id1", "id2"), diskDoc().income.map { it.id })
            assertEquals(1L, diskDoc().revision)
        }

    @Test
    fun bulkAdd_oneInvalid_nothingWritten() =
        runStoreTest {
            val result = repo.addIncomeList(listOf(income("x"), income("y", amountCents = 0)))
            assertEquals(0, result.succeededCount)
            assertTrue(result.failure is IllegalArgumentException)
            assertFalse(dataFile.exists())
        }
}
