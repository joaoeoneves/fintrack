package ptech.joaoe.agenticusage.data

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ptech.joaoe.agenticusage.domain.model.Expense
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory

/**
 * Unit tests for [FakeExpenseRepository], which is also the de-facto contract test for
 * [ptech.joaoe.agenticusage.domain.repository.ExpenseRepository] since it is the only
 * implementation that is exercisable without a live Firestore backend.
 */
class FakeExpenseRepositoryTest {

    private val day0 = Instant.parse("2026-01-01T00:00:00Z")
    private val day1 = day0.plus(1, ChronoUnit.DAYS)
    private val day2 = day0.plus(2, ChronoUnit.DAYS)
    private val day3 = day0.plus(3, ChronoUnit.DAYS)

    private fun expense(
        id: String = "",
        name: String = "expense",
        amountCents: Long = 1_000L,
        category: ExpenseCategory = ExpenseCategory.SHOPPING,
        date: Instant = day1,
        note: String? = null
    ) = Expense(id = id, name = name, amountCents = amountCents, category = category, date = date, note = note)

    // ---- observeExpenses: date-range filtering ----

    @Test
    fun observeExpenses_includesItemAtStartInclusive() = runBlocking {
        val boundaryStart = expense(id = "at-start", date = day0)
        val repo = FakeExpenseRepository(listOf(boundaryStart))

        val result = repo.observeExpenses(day0, day2).first()

        assertEquals(listOf(boundaryStart), result)
    }

    @Test
    fun observeExpenses_excludesItemAtEndExclusive() = runBlocking {
        val boundaryEnd = expense(id = "at-end", date = day2)
        val repo = FakeExpenseRepository(listOf(boundaryEnd))

        val result = repo.observeExpenses(day0, day2).first()

        assertTrue("item exactly at endExclusive should be excluded", result.isEmpty())
    }

    @Test
    fun observeExpenses_excludesItemsOutsideRange() = runBlocking {
        val before = expense(id = "before", date = day0.minus(1, ChronoUnit.DAYS))
        val after = expense(id = "after", date = day3)
        val inside = expense(id = "inside", date = day1)
        val repo = FakeExpenseRepository(listOf(before, after, inside))

        val result = repo.observeExpenses(day0, day2).first()

        assertEquals(listOf(inside), result)
    }

    @Test
    fun observeExpenses_emptyRepository_returnsEmptyList() = runBlocking {
        val repo = FakeExpenseRepository()

        val result = repo.observeExpenses(day0, day2).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun observeExpenses_subRangeAcrossCategoriesAndDates_returnsOnlyMatchingSubset() = runBlocking {
        val transfer = expense(id = "1", category = ExpenseCategory.TRANSFER, date = day0)
        val investment = expense(id = "2", category = ExpenseCategory.INVESTMENTS, date = day1)
        val shopping = expense(id = "3", category = ExpenseCategory.SHOPPING, date = day2)
        val recurring = expense(id = "4", category = ExpenseCategory.RECURRING, date = day3)
        val repo = FakeExpenseRepository(listOf(transfer, investment, shopping, recurring))

        // Sub-range [day1, day3) should pick up "investment" (day1, inclusive) and "shopping"
        // (day2) but exclude "transfer" (before range) and "recurring" (== endExclusive).
        val result = repo.observeExpenses(day1, day3).first()

        assertEquals(setOf(investment, shopping), result.toSet())
        assertEquals(2, result.size)
    }

    // ---- observeExpenses: live updates over an active collector ----

    @Test
    fun observeExpenses_activeCollector_seesAddExpenseUpdatesLive() = runBlocking {
        val repo = FakeExpenseRepository()
        val snapshots = mutableListOf<List<Expense>>()

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.observeExpenses(day0, day3).collect { snapshots.add(it) }
        }

        val added = expense(id = "", date = day1)
        val addResult = repo.addExpense(added)
        yield() // let the collector's suspended continuation process the new emission

        job.cancel()

        assertTrue(addResult.isSuccess)
        // initial empty emission + emission after add
        assertEquals(2, snapshots.size)
        assertTrue(snapshots[0].isEmpty())
        assertEquals(1, snapshots[1].size)
        assertEquals(added.copy(id = addResult.getOrThrow()), snapshots[1][0])
    }

    @Test
    fun observeExpenses_activeCollector_seesUpdateAndDeleteLive() = runBlocking {
        val original = expense(id = "e1", name = "Groceries", date = day1)
        val repo = FakeExpenseRepository(listOf(original))
        val snapshots = mutableListOf<List<Expense>>()

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.observeExpenses(day0, day3).collect { snapshots.add(it) }
        }

        val updated = original.copy(name = "Groceries (updated)", amountCents = 2_000L)
        assertTrue(repo.updateExpense(updated).isSuccess)
        yield()

        assertTrue(repo.deleteExpense("e1").isSuccess)
        yield()

        job.cancel()

        assertEquals(3, snapshots.size)
        assertEquals(listOf(original), snapshots[0])
        assertEquals(listOf(updated), snapshots[1])
        assertTrue(snapshots[2].isEmpty())
    }

    // ---- addExpense ----

    @Test
    fun addExpense_generatesId_whenInputIdBlank() = runBlocking {
        val repo = FakeExpenseRepository()

        val result = repo.addExpense(expense(id = ""))

        assertTrue(result.isSuccess)
        val generatedId = result.getOrThrow()
        assertTrue(generatedId.isNotBlank())
        val stored = repo.observeExpenses(day0, day3).first()
        assertEquals(1, stored.size)
        assertEquals(generatedId, stored[0].id)
    }

    @Test
    fun addExpense_preservesCallerSuppliedId_whenNonBlank() = runBlocking {
        val repo = FakeExpenseRepository()

        val result = repo.addExpense(expense(id = "custom-id"))

        assertTrue(result.isSuccess)
        assertEquals("custom-id", result.getOrThrow())
        val stored = repo.observeExpenses(day0, day3).first()
        assertEquals("custom-id", stored.single().id)
    }

    @Test
    fun addExpense_multipleCallsWithBlankId_generateDistinctIds() = runBlocking {
        val repo = FakeExpenseRepository()

        val id1 = repo.addExpense(expense(id = "", date = day0)).getOrThrow()
        val id2 = repo.addExpense(expense(id = "", date = day1)).getOrThrow()

        assertTrue(id1.isNotBlank())
        assertTrue(id2.isNotBlank())
        assertFalse("generated ids should be distinct", id1 == id2)
        assertEquals(2, repo.observeExpenses(day0, day3).first().size)
    }

    @Test
    fun addExpense_acceptsZeroAndNegativeAmountCents_withoutValidation() = runBlocking {
        // FakeExpenseRepository performs no validation of amountCents; this test documents that
        // contract so a future validating implementation change is caught deliberately, not
        // accidentally.
        val repo = FakeExpenseRepository()

        val zeroResult = repo.addExpense(expense(id = "zero", amountCents = 0L))
        val negativeResult = repo.addExpense(expense(id = "negative", amountCents = -500L))

        assertTrue(zeroResult.isSuccess)
        assertTrue(negativeResult.isSuccess)
        val stored = repo.observeExpenses(day0, day3).first().associateBy { it.id }
        assertEquals(0L, stored.getValue("zero").amountCents)
        assertEquals(-500L, stored.getValue("negative").amountCents)
    }

    // ---- updateExpense ----

    @Test
    fun updateExpense_updatesMatchingEntry_leavesOthersUntouched() = runBlocking {
        val e1 = expense(id = "e1", name = "First", date = day0)
        val e2 = expense(id = "e2", name = "Second", date = day1)
        val repo = FakeExpenseRepository(listOf(e1, e2))

        val updatedE1 = e1.copy(name = "First (renamed)", category = ExpenseCategory.RECURRING)
        val result = repo.updateExpense(updatedE1)

        assertTrue(result.isSuccess)
        val stored = repo.observeExpenses(day0, day3).first().associateBy { it.id }
        assertEquals(updatedE1, stored.getValue("e1"))
        assertEquals(e2, stored.getValue("e2"))
    }

    @Test
    fun updateExpense_unknownId_returnsFailure_andDoesNotModifyList() = runBlocking {
        val e1 = expense(id = "e1", date = day0)
        val repo = FakeExpenseRepository(listOf(e1))

        val result = repo.updateExpense(expense(id = "does-not-exist", date = day1))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
        val stored = repo.observeExpenses(day0, day3).first()
        assertEquals(listOf(e1), stored)
    }

    // ---- deleteExpense ----

    @Test
    fun deleteExpense_removesMatchingEntry_leavesOthersUntouched() = runBlocking {
        val e1 = expense(id = "e1", date = day0)
        val e2 = expense(id = "e2", date = day1)
        val repo = FakeExpenseRepository(listOf(e1, e2))

        val result = repo.deleteExpense("e1")

        assertTrue(result.isSuccess)
        val stored = repo.observeExpenses(day0, day3).first()
        assertEquals(listOf(e2), stored)
    }

    @Test
    fun deleteExpense_unknownId_returnsFailure_andDoesNotModifyList() = runBlocking {
        val e1 = expense(id = "e1", date = day0)
        val repo = FakeExpenseRepository(listOf(e1))

        val result = repo.deleteExpense("does-not-exist")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
        val stored = repo.observeExpenses(day0, day3).first()
        assertEquals(listOf(e1), stored)
    }

    @Test
    fun deleteExpense_thenReAddWithSameCallerSuppliedId_succeeds() = runBlocking {
        val repo = FakeExpenseRepository()
        repo.addExpense(expense(id = "reused-id", date = day0))
        repo.deleteExpense("reused-id")

        val result = repo.addExpense(expense(id = "reused-id", date = day1))

        assertTrue(result.isSuccess)
        val stored = repo.observeExpenses(day0, day3).first()
        assertEquals(1, stored.size)
        assertEquals(day1, stored.single().date)
    }

    // ---- getAllExpenses ----

    @Test
    fun getAllExpenses_emptyRepository_returnsEmptySuccess() = runBlocking {
        val repo = FakeExpenseRepository()

        val result = repo.getAllExpenses()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun getAllExpenses_returnsEntriesRegardlessOfDate_unlikeObserveExpenses() = runBlocking {
        // Deliberately spans a much wider range than any single observeExpenses(start, end) call
        // in these tests would use, to confirm getAllExpenses performs no date filtering at all.
        val farPast = expense(id = "past", date = day0.minus(10_000, ChronoUnit.DAYS))
        val farFuture = expense(id = "future", date = day0.plus(10_000, ChronoUnit.DAYS))
        val repo = FakeExpenseRepository(listOf(farPast, farFuture))

        val result = repo.getAllExpenses()

        assertTrue(result.isSuccess)
        assertEquals(setOf(farPast, farFuture), result.getOrThrow().toSet())
        // Sanity-check: the same two entries would NOT both show up in a narrow observeExpenses window.
        val narrowRange = repo.observeExpenses(day0, day2).first()
        assertTrue(narrowRange.isEmpty())
    }

    @Test
    fun getAllExpenses_reflectsLaterMutations() = runBlocking {
        val repo = FakeExpenseRepository(listOf(expense(id = "e1", date = day0)))
        repo.addExpense(expense(id = "e2", date = day1))
        repo.deleteExpense("e1")

        val result = repo.getAllExpenses()

        assertTrue(result.isSuccess)
        assertEquals(listOf("e2"), result.getOrThrow().map { it.id })
    }

    // ---- addExpenses (bulk) ----

    @Test
    fun addExpenses_emptyList_succeedsWithZeroCount_addsNothing() = runBlocking {
        val repo = FakeExpenseRepository()

        val result = repo.addExpenses(emptyList())

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow())
        assertTrue(repo.getAllExpenses().getOrThrow().isEmpty())
    }

    @Test
    fun addExpenses_bulkAdd_returnsSuccessCount_allRowsRetrievableWithFreshNonBlankIds() = runBlocking {
        val repo = FakeExpenseRepository()
        val toImport = listOf(
            expense(id = "", name = "Coffee", date = day0),
            expense(id = "", name = "Rent", date = day1),
            expense(id = "", name = "Netflix", date = day2)
        )

        val result = repo.addExpenses(toImport)

        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrThrow())
        val stored = repo.getAllExpenses().getOrThrow()
        assertEquals(3, stored.size)
        assertTrue(stored.all { it.id.isNotBlank() })
        // ids are distinct
        assertEquals(3, stored.map { it.id }.toSet().size)
        assertEquals(setOf("Coffee", "Rent", "Netflix"), stored.map { it.name }.toSet())
    }

    @Test
    fun addExpenses_ignoresAnyCallerSuppliedId_alwaysGeneratesFreshOne() = runBlocking {
        val repo = FakeExpenseRepository()

        repo.addExpenses(listOf(expense(id = "caller-supplied-id", date = day0)))

        val stored = repo.getAllExpenses().getOrThrow()
        assertEquals(1, stored.size)
        assertFalse("caller-supplied-id" == stored.single().id)
        assertTrue(stored.single().id.isNotBlank())
    }

    @Test
    fun addExpenses_appendsToExistingEntries_doesNotReplaceThem() = runBlocking {
        val existing = expense(id = "existing", date = day0)
        val repo = FakeExpenseRepository(listOf(existing))

        val result = repo.addExpenses(listOf(expense(id = "", name = "New", date = day1)))

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow())
        val stored = repo.getAllExpenses().getOrThrow()
        assertEquals(2, stored.size)
        assertTrue(stored.any { it.id == "existing" })
        assertTrue(stored.any { it.name == "New" })
    }
}
