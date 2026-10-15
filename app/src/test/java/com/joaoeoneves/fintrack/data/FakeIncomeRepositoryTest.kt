package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.Income
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Unit tests for [FakeIncomeRepository], which is also the de-facto contract test for
 * [com.joaoeoneves.fintrack.domain.repository.IncomeRepository] since it is the only
 * implementation that is exercisable without a live Firestore backend. Mirrors
 * [FakeExpenseRepositoryTest]'s coverage/conventions.
 */
class FakeIncomeRepositoryTest {
    private val day0 = Instant.parse("2026-01-01T00:00:00Z")
    private val day1 = day0.plus(1, ChronoUnit.DAYS)
    private val day2 = day0.plus(2, ChronoUnit.DAYS)
    private val day3 = day0.plus(3, ChronoUnit.DAYS)

    private fun income(
        id: String = "",
        source: String = "Paycheck",
        amountCents: Long = 100_000L,
        date: Instant = day1,
        note: String? = null,
    ) = Income(id = id, source = source, amountCents = amountCents, date = date, note = note)

    // ---- observeIncome: date-range filtering ----

    @Test
    fun observeIncome_includesItemAtStartInclusive() =
        runBlocking {
            val boundaryStart = income(id = "at-start", date = day0)
            val repo = FakeIncomeRepository(listOf(boundaryStart))

            val result = repo.observeIncome(day0, day2).first()

            assertEquals(listOf(boundaryStart), result)
        }

    @Test
    fun observeIncome_excludesItemAtEndExclusive() =
        runBlocking {
            val boundaryEnd = income(id = "at-end", date = day2)
            val repo = FakeIncomeRepository(listOf(boundaryEnd))

            val result = repo.observeIncome(day0, day2).first()

            assertTrue("item exactly at endExclusive should be excluded", result.isEmpty())
        }

    @Test
    fun observeIncome_excludesItemsOutsideRange() =
        runBlocking {
            val before = income(id = "before", date = day0.minus(1, ChronoUnit.DAYS))
            val after = income(id = "after", date = day3)
            val inside = income(id = "inside", date = day1)
            val repo = FakeIncomeRepository(listOf(before, after, inside))

            val result = repo.observeIncome(day0, day2).first()

            assertEquals(listOf(inside), result)
        }

    @Test
    fun observeIncome_emptyRepository_returnsEmptyList() =
        runBlocking {
            val repo = FakeIncomeRepository()

            val result = repo.observeIncome(day0, day2).first()

            assertTrue(result.isEmpty())
        }

    @Test
    fun observeIncome_subRangeAcrossDates_returnsOnlyMatchingSubset() =
        runBlocking {
            val i1 = income(id = "1", date = day0)
            val i2 = income(id = "2", date = day1)
            val i3 = income(id = "3", date = day2)
            val i4 = income(id = "4", date = day3)
            val repo = FakeIncomeRepository(listOf(i1, i2, i3, i4))

            // Sub-range [day1, day3) should pick up "i2" (day1, inclusive) and "i3" (day2) but
            // exclude "i1" (before range) and "i4" (== endExclusive).
            val result = repo.observeIncome(day1, day3).first()

            assertEquals(setOf(i2, i3), result.toSet())
            assertEquals(2, result.size)
        }

    // ---- observeIncome: nextObserveIncomeError ----

    @Test
    fun observeIncome_defaultNullError_behavesNormally() =
        runBlocking {
            val i1 = income(id = "i1", date = day1)
            val repo = FakeIncomeRepository(listOf(i1))

            assertNull(repo.nextObserveIncomeError)
            val result = repo.observeIncome(day0, day2).first()

            assertEquals(listOf(i1), result)
        }

    @Test
    fun observeIncome_withErrorSet_flowThrowsThatExactThrowable() =
        runBlocking {
            val repo = FakeIncomeRepository(listOf(income(id = "i1", date = day1)))
            val boom = IllegalStateException("Firestore unavailable")
            repo.nextObserveIncomeError = boom

            var caught: Throwable? = null
            repo
                .observeIncome(day0, day2)
                .catch { e -> caught = e }
                .collect { fail("expected the flow to throw, but got a value: $it") }

            assertSame(boom, caught)
        }

    @Test
    fun observeIncome_withErrorSet_throwsOnEveryCollection_untilClearedAgain() =
        runBlocking {
            val repo = FakeIncomeRepository(listOf(income(id = "i1", date = day1)))
            val boom = RuntimeException("boom")
            repo.nextObserveIncomeError = boom

            var firstCollectionThrew = false
            try {
                repo.observeIncome(day0, day2).first()
            } catch (e: RuntimeException) {
                firstCollectionThrew = true
                assertSame(boom, e)
            }
            assertTrue(firstCollectionThrew)

            // Clear the error: subsequent collections should go back to the normal filtered flow.
            repo.nextObserveIncomeError = null
            val result = repo.observeIncome(day0, day2).first()
            assertEquals(1, result.size)
        }

    @Test
    fun nextObserveIncomeError_doesNotAffectAddUpdateDeleteGetAll() =
        runBlocking {
            val i1 = income(id = "i1", date = day1)
            val repo = FakeIncomeRepository(listOf(i1))
            repo.nextObserveIncomeError = RuntimeException("observe is broken")

            // add
            val addResult = repo.addIncome(income(id = "i2", date = day1))
            assertTrue(addResult.isSuccess)

            // update
            val updated = i1.copy(source = "Renamed")
            assertTrue(repo.updateIncome(updated).isSuccess)

            // getIncome / getAllIncome unaffected
            assertEquals(updated, repo.getIncome("i1").getOrThrow())
            val all = repo.getAllIncome()
            assertTrue(all.isSuccess)
            assertEquals(2, all.getOrThrow().size)

            // delete
            assertTrue(repo.deleteIncome("i2").isSuccess)
            assertEquals(1, repo.getAllIncome().getOrThrow().size)

            // observeIncome itself is still broken throughout
            var caught: Throwable? = null
            try {
                repo.observeIncome(day0, day2).first()
            } catch (e: RuntimeException) {
                caught = e
            }
            assertSame(
                "observeIncome should still throw since nextObserveIncomeError was never cleared",
                repo.nextObserveIncomeError,
                caught,
            )
        }

    // ---- observeIncome: live updates over an active collector ----

    @Test
    fun observeIncome_activeCollector_seesAddIncomeUpdatesLive() =
        runBlocking {
            val repo = FakeIncomeRepository()
            val snapshots = mutableListOf<List<Income>>()

            val job =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    repo.observeIncome(day0, day3).collect { snapshots.add(it) }
                }

            val added = income(id = "", date = day1)
            val addResult = repo.addIncome(added)
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
    fun observeIncome_activeCollector_seesUpdateAndDeleteLive() =
        runBlocking {
            val original = income(id = "i1", source = "Paycheck", date = day1)
            val repo = FakeIncomeRepository(listOf(original))
            val snapshots = mutableListOf<List<Income>>()

            val job =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    repo.observeIncome(day0, day3).collect { snapshots.add(it) }
                }

            val updated = original.copy(source = "Paycheck (updated)", amountCents = 200_000L)
            assertTrue(repo.updateIncome(updated).isSuccess)
            yield()

            assertTrue(repo.deleteIncome("i1").isSuccess)
            yield()

            job.cancel()

            assertEquals(3, snapshots.size)
            assertEquals(listOf(original), snapshots[0])
            assertEquals(listOf(updated), snapshots[1])
            assertTrue(snapshots[2].isEmpty())
        }

    // ---- addIncome ----

    @Test
    fun addIncome_generatesId_whenInputIdBlank() =
        runBlocking {
            val repo = FakeIncomeRepository()

            val result = repo.addIncome(income(id = ""))

            assertTrue(result.isSuccess)
            val generatedId = result.getOrThrow()
            assertTrue(generatedId.isNotBlank())
            val stored = repo.observeIncome(day0, day3).first()
            assertEquals(1, stored.size)
            assertEquals(generatedId, stored[0].id)
        }

    @Test
    fun addIncome_preservesCallerSuppliedId_whenNonBlank() =
        runBlocking {
            val repo = FakeIncomeRepository()

            val result = repo.addIncome(income(id = "custom-id"))

            assertTrue(result.isSuccess)
            assertEquals("custom-id", result.getOrThrow())
            val stored = repo.observeIncome(day0, day3).first()
            assertEquals("custom-id", stored.single().id)
        }

    @Test
    fun addIncome_multipleCallsWithBlankId_generateDistinctIds() =
        runBlocking {
            val repo = FakeIncomeRepository()

            val id1 = repo.addIncome(income(id = "", date = day0)).getOrThrow()
            val id2 = repo.addIncome(income(id = "", date = day1)).getOrThrow()

            assertTrue(id1.isNotBlank())
            assertTrue(id2.isNotBlank())
            assertFalse("generated ids should be distinct", id1 == id2)
            assertEquals(2, repo.observeIncome(day0, day3).first().size)
        }

    @Test
    fun addIncome_acceptsZeroAndNegativeAmountCents_withoutValidation() =
        runBlocking {
            // FakeIncomeRepository performs no validation of amountCents; this test documents that
            // contract so a future validating implementation change is caught deliberately, not
            // accidentally.
            val repo = FakeIncomeRepository()

            val zeroResult = repo.addIncome(income(id = "zero", amountCents = 0L))
            val negativeResult = repo.addIncome(income(id = "negative", amountCents = -500L))

            assertTrue(zeroResult.isSuccess)
            assertTrue(negativeResult.isSuccess)
            val stored = repo.observeIncome(day0, day3).first().associateBy { it.id }
            assertEquals(0L, stored.getValue("zero").amountCents)
            assertEquals(-500L, stored.getValue("negative").amountCents)
        }

    // ---- updateIncome ----

    @Test
    fun updateIncome_updatesMatchingEntry_leavesOthersUntouched() =
        runBlocking {
            val i1 = income(id = "i1", source = "First", date = day0)
            val i2 = income(id = "i2", source = "Second", date = day1)
            val repo = FakeIncomeRepository(listOf(i1, i2))

            val updatedI1 = i1.copy(source = "First (renamed)", amountCents = 250_000L)
            val result = repo.updateIncome(updatedI1)

            assertTrue(result.isSuccess)
            val stored = repo.observeIncome(day0, day3).first().associateBy { it.id }
            assertEquals(updatedI1, stored.getValue("i1"))
            assertEquals(i2, stored.getValue("i2"))
        }

    @Test
    fun updateIncome_unknownId_returnsFailure_andDoesNotModifyList() =
        runBlocking {
            val i1 = income(id = "i1", date = day0)
            val repo = FakeIncomeRepository(listOf(i1))

            val result = repo.updateIncome(income(id = "does-not-exist", date = day1))

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is NoSuchElementException)
            val stored = repo.observeIncome(day0, day3).first()
            assertEquals(listOf(i1), stored)
        }

    // ---- deleteIncome ----

    @Test
    fun deleteIncome_removesMatchingEntry_leavesOthersUntouched() =
        runBlocking {
            val i1 = income(id = "i1", date = day0)
            val i2 = income(id = "i2", date = day1)
            val repo = FakeIncomeRepository(listOf(i1, i2))

            val result = repo.deleteIncome("i1")

            assertTrue(result.isSuccess)
            val stored = repo.observeIncome(day0, day3).first()
            assertEquals(listOf(i2), stored)
        }

    @Test
    fun deleteIncome_unknownId_returnsFailure_andDoesNotModifyList() =
        runBlocking {
            val i1 = income(id = "i1", date = day0)
            val repo = FakeIncomeRepository(listOf(i1))

            val result = repo.deleteIncome("does-not-exist")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is NoSuchElementException)
            val stored = repo.observeIncome(day0, day3).first()
            assertEquals(listOf(i1), stored)
        }

    @Test
    fun deleteIncome_thenReAddWithSameCallerSuppliedId_succeeds() =
        runBlocking {
            val repo = FakeIncomeRepository()
            repo.addIncome(income(id = "reused-id", date = day0))
            repo.deleteIncome("reused-id")

            val result = repo.addIncome(income(id = "reused-id", date = day1))

            assertTrue(result.isSuccess)
            val stored = repo.observeIncome(day0, day3).first()
            assertEquals(1, stored.size)
            assertEquals(day1, stored.single().date)
        }

    // ---- getIncome ----

    @Test
    fun getIncome_unknownId_returnsSuccessWithNull() =
        runBlocking {
            val repo = FakeIncomeRepository()

            val result = repo.getIncome("does-not-exist")

            // "Not found" must be a *successful* result carrying null, not a failure -- callers
            // (e.g. AddEditIncomeViewModel) rely on this to distinguish a confirmed-absent
            // document (blank editable form) from a failed read (error state, no silent upsert).
            assertTrue(result.isSuccess)
            assertNull(result.getOrThrow())
        }

    @Test
    fun getIncome_knownId_returnsSuccessWithMatchingEntry() =
        runBlocking {
            val i1 = income(id = "i1", date = day0)
            val repo = FakeIncomeRepository(listOf(i1))

            val result = repo.getIncome("i1")

            assertTrue(result.isSuccess)
            assertEquals(i1, result.getOrThrow())
        }

    // ---- getIncome: nextGetIncomeError ----

    @Test
    fun getIncome_defaultNullError_behavesNormally() =
        runBlocking {
            val repo = FakeIncomeRepository()

            assertNull(repo.nextGetIncomeError)
            assertTrue(repo.getIncome("anything").isSuccess)
        }

    @Test
    fun getIncome_withErrorSet_returnsFailureWithThatExactThrowable_distinctFromNotFound() =
        runBlocking {
            val i1 = income(id = "i1", date = day0)
            val repo = FakeIncomeRepository(listOf(i1))
            val boom = IllegalStateException("Firestore unavailable")
            repo.nextGetIncomeError = boom

            // Even for a *known* id, a load failure must surface as Result.failure, never as a
            // (successful) null -- otherwise it would be indistinguishable from "not found".
            val result = repo.getIncome("i1")

            assertTrue(result.isFailure)
            assertSame(boom, result.exceptionOrNull())
        }

    @Test
    fun getIncome_withErrorSet_selfResetsAfterOneCall_soRetrySucceeds() =
        runBlocking {
            val i1 = income(id = "i1", date = day0)
            val repo = FakeIncomeRepository(listOf(i1))
            repo.nextGetIncomeError = RuntimeException("transient")

            val first = repo.getIncome("i1")
            assertTrue(first.isFailure)
            assertNull(
                "the fake should self-reset after being consumed once",
                repo.nextGetIncomeError,
            )

            val second = repo.getIncome("i1")
            assertTrue(second.isSuccess)
            assertEquals(i1, second.getOrThrow())
        }

    // ---- getAllIncome ----

    @Test
    fun getAllIncome_emptyRepository_returnsEmptySuccess() =
        runBlocking {
            val repo = FakeIncomeRepository()

            val result = repo.getAllIncome()

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isEmpty())
        }

    @Test
    fun getAllIncome_returnsEntriesRegardlessOfDate_unlikeObserveIncome() =
        runBlocking {
            // Deliberately spans a much wider range than any single observeIncome(start, end) call
            // in these tests would use, to confirm getAllIncome performs no date filtering at all.
            val farPast = income(id = "past", date = day0.minus(10_000, ChronoUnit.DAYS))
            val farFuture = income(id = "future", date = day0.plus(10_000, ChronoUnit.DAYS))
            val repo = FakeIncomeRepository(listOf(farPast, farFuture))

            val result = repo.getAllIncome()

            assertTrue(result.isSuccess)
            assertEquals(setOf(farPast, farFuture), result.getOrThrow().toSet())
            // Sanity-check: the same two entries would NOT both show up in a narrow observeIncome window.
            val narrowRange = repo.observeIncome(day0, day2).first()
            assertTrue(narrowRange.isEmpty())
        }

    @Test
    fun getAllIncome_reflectsLaterMutations() =
        runBlocking {
            val repo = FakeIncomeRepository(listOf(income(id = "i1", date = day0)))
            repo.addIncome(income(id = "i2", date = day1))
            repo.deleteIncome("i1")

            val result = repo.getAllIncome()

            assertTrue(result.isSuccess)
            assertEquals(listOf("i2"), result.getOrThrow().map { it.id })
        }

    // ---- addIncomeList (bulk) ----

    @Test
    fun addIncomeList_emptyList_succeedsWithZeroCount_addsNothing() =
        runBlocking {
            val repo = FakeIncomeRepository()

            val result = repo.addIncomeList(emptyList())

            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrThrow())
            assertTrue(repo.getAllIncome().getOrThrow().isEmpty())
        }

    @Test
    fun addIncomeList_bulkAdd_returnsSuccessCount_allRowsRetrievableWithFreshNonBlankIds() =
        runBlocking {
            val repo = FakeIncomeRepository()
            val toImport =
                listOf(
                    income(id = "", source = "Salary", date = day0),
                    income(id = "", source = "Freelance", date = day1),
                    income(id = "", source = "Interest", date = day2),
                )

            val result = repo.addIncomeList(toImport)

            assertTrue(result.isSuccess)
            assertEquals(3, result.getOrThrow())
            val stored = repo.getAllIncome().getOrThrow()
            assertEquals(3, stored.size)
            assertTrue(stored.all { it.id.isNotBlank() })
            // ids are distinct
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

            val result = repo.addIncomeList(listOf(income(id = "", source = "New", date = day1)))

            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrThrow())
            val stored = repo.getAllIncome().getOrThrow()
            assertEquals(2, stored.size)
            assertTrue(stored.any { it.id == "existing" })
            assertTrue(stored.any { it.source == "New" })
        }
}
