package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LocalExpenseRepositoryTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalExpenseRepository(store, SequenceIds()) }
    private val start = Instant.parse("2026-03-01T00:00:00Z")
    private val end = Instant.parse("2026-04-01T00:00:00Z")

    @Test
    fun add_blankId_generatesId_andGivenIdIsPreserved() =
        runStoreTest {
            assertEquals("id1", repo.addExpense(expense(id = "")).getOrThrow())
            assertEquals("given", repo.addExpense(expense(id = "given")).getOrThrow())
            assertEquals(listOf("id1", "given"), repo.getAllExpenses().getOrThrow().map { it.id })
        }

    @Test
    fun add_existingId_upserts() =
        runStoreTest {
            repo.addExpense(expense(id = "x", name = "A"))
            repo.addExpense(expense(id = "x", name = "B"))
            assertEquals(listOf("B"), repo.getAllExpenses().getOrThrow().map { it.name })
        }

    @Test
    fun add_withRealRandomIds_produces20CharIds() =
        runStoreTest {
            val id = LocalExpenseRepository(store, RandomIdGenerator()).addExpense(expense(id = "")).getOrThrow()
            assertTrue(id.matches(Regex("[A-Za-z0-9]{20}")))
        }

    @Test
    fun observe_includesStart_excludesEnd_sortedAscending() =
        runStoreTest {
            repo.addExpense(expense("atEnd", date = end))
            repo.addExpense(expense("late", date = end.minusNanos(1)))
            repo.addExpense(expense("atStart", date = start))
            repo.addExpense(expense("before", date = start.minusNanos(1)))
            repo.addExpense(expense("mid", date = Instant.parse("2026-03-15T00:00:00Z")))
            assertEquals(listOf("atStart", "mid", "late"), repo.observeExpenses(start, end).first().map { it.id })
        }

    @Test
    fun observe_emptyAndEmptyRange() =
        runStoreTest {
            assertEquals(emptyList<Any>(), repo.observeExpenses(start, end).first())
            repo.addExpense(expense("a", date = start))
            assertEquals(emptyList<Any>(), repo.observeExpenses(start, start).first())
        }

    @Test
    fun update_missing_failsWithIllegalArgument() =
        runStoreTest {
            val result = repo.updateExpense(expense("ghost"))
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertFalse(dataFile.exists())
        }

    @Test
    fun update_existing_replaces_andInvalidUpdateKeepsOld() =
        runStoreTest {
            repo.addExpense(expense("a", name = "Old"))
            assertTrue(repo.updateExpense(expense("a", name = "New")).isSuccess)
            assertEquals("New", repo.getExpense("a").getOrThrow()?.name)
            assertTrue(repo.updateExpense(expense("a", amountCents = 0)).isFailure)
            assertEquals("New", repo.getExpense("a").getOrThrow()?.name)
        }

    @Test
    fun delete_removes_andMissingSucceedsWithoutRevisionBump() =
        runStoreTest {
            repo.addExpense(expense("a"))
            assertTrue(repo.deleteExpense("a").isSuccess)
            val revision = store.data.first().revision
            assertTrue(repo.deleteExpense("a").isSuccess)
            assertEquals(revision, store.data.first().revision)
            assertEquals(emptyList<Any>(), repo.getAllExpenses().getOrThrow())
        }

    @Test
    fun get_missing_isSuccessNull() =
        runStoreTest {
            assertNull(repo.getExpense("nope").getOrThrow())
        }

    @Test
    fun hasExpenseWithCategory() =
        runStoreTest {
            repo.addExpense(expense("a", categoryId = "FOOD"))
            assertTrue(repo.hasExpenseWithCategory(CategoryId("FOOD")).getOrThrow())
            assertFalse(repo.hasExpenseWithCategory(CategoryId("SHOPPING")).getOrThrow())
        }

    @Test
    fun storedExpense_roundTripsNanosAndNote() =
        runStoreTest {
            val date = Instant.parse("2026-03-10T10:15:30.123456789Z")
            repo.addExpense(expense("a", date = date).copy(note = "hi"))
            val loaded = LocalExpenseRepository(newStore(), SequenceIds()).getExpense("a").getOrThrow()!!
            assertEquals(date, loaded.date)
            assertEquals("hi", loaded.note)
            assertEquals(BuiltInCategories.FOOD, loaded.category)
        }

    @Test
    fun unknownStoredCategory_resolvesToUnknown() =
        runStoreTest {
            store.update {
                it.copy(expenses = listOf(LocalExpense("a", "n", 5, "deleted_cat", "2026-03-10T00:00:00Z"))) to Unit
            }
            val category = repo.getExpense("a").getOrThrow()!!.category
            assertEquals(Category.unknown(CategoryId("deleted_cat")), category)
        }
}
