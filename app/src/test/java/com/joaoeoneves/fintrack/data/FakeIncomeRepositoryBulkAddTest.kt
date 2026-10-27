package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.BulkAddResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * [FakeIncomeRepository.addIncomeList] coverage, split out of [FakeIncomeRepositoryTest] to keep
 * that class bounded. Mirrors [FakeExpenseRepositoryTest]'s `addExpenses` coverage, including the
 * partial-failure knobs ([FakeIncomeRepository.nextAddIncomeListFailure] /
 * [FakeIncomeRepository.addIncomeListFailureAfterCount]).
 */
class FakeIncomeRepositoryBulkAddTest {
    private val day0 = Instant.parse("2026-01-01T00:00:00Z")
    private val day1 = day0.plus(1, ChronoUnit.DAYS)
    private val day2 = day0.plus(2, ChronoUnit.DAYS)
    private val day3 = day0.plus(3, ChronoUnit.DAYS)

    private fun income(
        id: String = "",
        source: String = "Paycheck",
        date: Instant = day1,
    ) = Income(id = id, source = source, amountCents = 100_000L, date = date, note = null)

    @Test
    fun addIncomeList_emptyList_returnsZeroCountAndNoFailure_addsNothing() =
        runBlocking {
            val repo = FakeIncomeRepository()

            val result = repo.addIncomeList(emptyList())

            assertEquals(BulkAddResult(0, null), result)
            assertTrue(result.isCompleteSuccess)
            assertTrue(repo.getAllIncome().getOrThrow().isEmpty())
        }

    @Test
    fun addIncomeList_bulkAdd_returnsSuccessCount_allRowsRetrievableWithFreshNonBlankIds() =
        runBlocking {
            val repo = FakeIncomeRepository()
            val toImport =
                listOf(
                    income(source = "Salary", date = day0),
                    income(source = "Freelance", date = day1),
                    income(source = "Interest", date = day2),
                )

            val result = repo.addIncomeList(toImport)

            assertTrue(result.isCompleteSuccess)
            assertEquals(3, result.succeededCount)
            assertNull(result.failure)
            val stored = repo.getAllIncome().getOrThrow()
            assertEquals(3, stored.size)
            assertTrue(stored.all { it.id.isNotBlank() })
            assertEquals(3, stored.map { it.id }.toSet().size)
            assertEquals(setOf("Salary", "Freelance", "Interest"), stored.map { it.source }.toSet())
        }

    @Test
    fun addIncomeList_ignoresAnyCallerSuppliedId_alwaysGeneratesFreshOne() =
        runBlocking {
            val repo = FakeIncomeRepository()

            repo.addIncomeList(listOf(income(id = "caller-supplied-id", date = day0)))

            val stored = repo.getAllIncome().getOrThrow()
            assertEquals(1, stored.size)
            assertFalse("caller-supplied-id" == stored.single().id)
            assertTrue(stored.single().id.isNotBlank())
        }

    @Test
    fun addIncomeList_appendsToExistingEntries_doesNotReplaceThem() =
        runBlocking {
            val existing = income(id = "existing", date = day0)
            val repo = FakeIncomeRepository(listOf(existing))

            val result = repo.addIncomeList(listOf(income(source = "New", date = day1)))

            assertTrue(result.isCompleteSuccess)
            assertEquals(1, result.succeededCount)
            val stored = repo.getAllIncome().getOrThrow()
            assertEquals(2, stored.size)
            assertTrue(stored.any { it.id == "existing" })
            assertTrue(stored.any { it.source == "New" })
        }

    @Test
    fun addIncomeList_failureKnobSet_onlyPrefixIsStored_resultReportsCommittedCountAndFailure() =
        runBlocking {
            val boom = IllegalStateException("Firestore batch commit failed")
            val repo = FakeIncomeRepository(nextAddIncomeListFailure = boom, addIncomeListFailureAfterCount = 2)
            val toImport =
                listOf(
                    income(source = "Salary", date = day0),
                    income(source = "Freelance", date = day1),
                    income(source = "Interest", date = day2),
                    income(source = "Gift", date = day3),
                )

            val result = repo.addIncomeList(toImport)

            assertFalse(result.isCompleteSuccess)
            assertEquals(2, result.succeededCount)
            assertSame(boom, result.failure)
            val stored = repo.getAllIncome().getOrThrow()
            assertEquals(setOf("Salary", "Freelance"), stored.map { it.source }.toSet())
            assertEquals(2, stored.size)
        }

    @Test
    fun addIncomeList_failureKnobSet_zeroAfterCount_reportsZeroSucceeded_storesNothing() =
        runBlocking {
            val boom = IllegalStateException("boom")
            val repo = FakeIncomeRepository(nextAddIncomeListFailure = boom, addIncomeListFailureAfterCount = 0)

            val result = repo.addIncomeList(listOf(income(date = day0)))

            assertEquals(BulkAddResult(0, boom), result)
            assertTrue(repo.getAllIncome().getOrThrow().isEmpty())
        }

    @Test
    fun addIncomeList_failureKnob_afterCountLargerThanList_commitsAllButStillReportsFailure() =
        runBlocking {
            val boom = IllegalStateException("boom")
            val repo = FakeIncomeRepository(nextAddIncomeListFailure = boom, addIncomeListFailureAfterCount = 10)

            val result = repo.addIncomeList(listOf(income(date = day0), income(date = day1)))

            assertEquals(2, result.succeededCount)
            assertSame(boom, result.failure)
            assertEquals(2, repo.getAllIncome().getOrThrow().size)
        }

    @Test
    fun addIncomeList_failureKnob_selfResetsAfterOneCall_soRetrySucceedsCompletely() =
        runBlocking {
            val repo =
                FakeIncomeRepository(
                    nextAddIncomeListFailure = IllegalStateException("transient"),
                    addIncomeListFailureAfterCount = 1,
                )

            val first = repo.addIncomeList(listOf(income(source = "Salary"), income(source = "Freelance")))
            assertFalse(first.isCompleteSuccess)
            assertEquals(1, first.succeededCount)
            assertNull("the fake should self-reset after being consumed once", repo.nextAddIncomeListFailure)

            val second = repo.addIncomeList(listOf(income(source = "Interest")))
            assertEquals(BulkAddResult(1, null), second)
            val stored = repo.getAllIncome().getOrThrow()
            assertEquals(setOf("Salary", "Interest"), stored.map { it.source }.toSet())
        }
}
