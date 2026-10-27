package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [FakeBudgetRepository], which is also the de-facto contract test for
 * [com.joaoeoneves.fintrack.domain.repository.BudgetRepository] since it is the only
 * implementation that is exercisable without a live Firestore backend.
 */
class FakeBudgetRepositoryTest {
    // ---- observeBudgets: reflects constructor-seeded and mutated state ----

    @Test
    fun observeBudgets_emptyByDefault_returnsEmptyList() =
        runBlocking {
            val repo = FakeBudgetRepository()

            val result = repo.observeBudgets().first()

            assertTrue(result.isEmpty())
        }

    @Test
    fun observeBudgets_reflectsConstructorSeededBudgets() =
        runBlocking {
            val seeded =
                listOf(
                    Budget(BuiltInCategories.SHOPPING.id, 10_000L),
                    Budget(BuiltInCategories.TRANSFER.id, 5_000L),
                )
            val repo = FakeBudgetRepository(seeded)

            val result = repo.observeBudgets().first()

            assertEquals(seeded.toSet(), result.toSet())
        }

    @Test
    fun observeBudgets_activeCollector_seesSetBudgetUpdatesLive() =
        runBlocking {
            val repo = FakeBudgetRepository()
            val snapshots = mutableListOf<List<Budget>>()

            val job =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    repo.observeBudgets().collect { snapshots.add(it) }
                }

            repo.setBudget(BuiltInCategories.SHOPPING.id, 12_345L)
            yield() // let the collector's suspended continuation process the new emission

            job.cancel()

            assertEquals(2, snapshots.size)
            assertTrue("initial emission should be empty", snapshots[0].isEmpty())
            assertEquals(listOf(Budget(BuiltInCategories.SHOPPING.id, 12_345L)), snapshots[1])
        }

    // ---- setBudget: upserts (replaces existing entry rather than duplicating) ----

    @Test
    fun setBudget_newCategory_addsEntry() =
        runBlocking {
            val repo = FakeBudgetRepository()

            val result = repo.setBudget(BuiltInCategories.SHOPPING.id, 10_000L)

            assertTrue(result.isSuccess)
            val stored = repo.observeBudgets().first()
            assertEquals(listOf(Budget(BuiltInCategories.SHOPPING.id, 10_000L)), stored)
        }

    @Test
    fun setBudget_existingCategory_replacesLimitRatherThanDuplicating() =
        runBlocking {
            val repo = FakeBudgetRepository(listOf(Budget(BuiltInCategories.SHOPPING.id, 10_000L)))

            val result = repo.setBudget(BuiltInCategories.SHOPPING.id, 20_000L)

            assertTrue(result.isSuccess)
            val stored = repo.observeBudgets().first()
            assertEquals(1, stored.size)
            assertEquals(20_000L, stored.single { it.categoryId == BuiltInCategories.SHOPPING.id }.limitCents)
        }

    @Test
    fun setBudget_multipleCategories_eachTrackedIndependently() =
        runBlocking {
            val repo = FakeBudgetRepository()

            repo.setBudget(BuiltInCategories.SHOPPING.id, 10_000L)
            repo.setBudget(BuiltInCategories.TRANSFER.id, 5_000L)
            repo.setBudget(BuiltInCategories.SHOPPING.id, 15_000L) // update, not a new entry

            val stored = repo.observeBudgets().first().associateBy { it.categoryId }
            assertEquals(2, stored.size)
            assertEquals(15_000L, stored.getValue(BuiltInCategories.SHOPPING.id).limitCents)
            assertEquals(5_000L, stored.getValue(BuiltInCategories.TRANSFER.id).limitCents)
        }

    @Test
    fun setBudget_acceptsZeroAndNegativeLimitCents_withoutValidation() =
        runBlocking {
            // FakeBudgetRepository performs no validation of limitCents; this test documents that
            // contract so a future validating implementation change is caught deliberately, not
            // accidentally.
            val repo = FakeBudgetRepository()

            val zeroResult = repo.setBudget(BuiltInCategories.SHOPPING.id, 0L)
            val negativeResult = repo.setBudget(BuiltInCategories.TRANSFER.id, -500L)

            assertTrue(zeroResult.isSuccess)
            assertTrue(negativeResult.isSuccess)
            val stored = repo.observeBudgets().first().associateBy { it.categoryId }
            assertEquals(0L, stored.getValue(BuiltInCategories.SHOPPING.id).limitCents)
            assertEquals(-500L, stored.getValue(BuiltInCategories.TRANSFER.id).limitCents)
        }

    // ---- nextObserveBudgetsError ----

    @Test
    fun observeBudgets_defaultNullError_behavesNormally() =
        runBlocking {
            val repo = FakeBudgetRepository(listOf(Budget(BuiltInCategories.SHOPPING.id, 10_000L)))

            assertNull(repo.nextObserveBudgetsError)
            val result = repo.observeBudgets().first()

            assertEquals(listOf(Budget(BuiltInCategories.SHOPPING.id, 10_000L)), result)
        }

    @Test
    fun observeBudgets_withErrorSet_flowThrowsThatExactThrowable() =
        runBlocking {
            val repo = FakeBudgetRepository(listOf(Budget(BuiltInCategories.SHOPPING.id, 10_000L)))
            val boom = IllegalStateException("Firestore unavailable")
            repo.nextObserveBudgetsError = boom

            var caught: Throwable? = null
            repo
                .observeBudgets()
                .catch { e -> caught = e }
                .collect { fail("expected the flow to throw, but got a value: $it") }

            assertSame(boom, caught)
        }

    @Test
    fun observeBudgets_withErrorSet_throwsOnEveryCollection_untilClearedAgain() =
        runBlocking {
            val repo = FakeBudgetRepository(listOf(Budget(BuiltInCategories.SHOPPING.id, 10_000L)))
            val boom = RuntimeException("boom")
            repo.nextObserveBudgetsError = boom

            var firstCollectionThrew = false
            try {
                repo.observeBudgets().first()
            } catch (e: RuntimeException) {
                firstCollectionThrew = true
                assertSame(boom, e)
            }
            assertTrue(firstCollectionThrew)

            // Clear the error: subsequent collections should go back to the normal flow.
            repo.nextObserveBudgetsError = null
            val result = repo.observeBudgets().first()
            assertEquals(1, result.size)
        }

    @Test
    fun nextObserveBudgetsError_doesNotAffectSetBudget() =
        runBlocking {
            val repo = FakeBudgetRepository()
            repo.nextObserveBudgetsError = RuntimeException("observe is broken")

            val result = repo.setBudget(BuiltInCategories.SHOPPING.id, 10_000L)

            assertTrue(result.isSuccess)

            // observeBudgets itself is still broken throughout, since the error was never cleared.
            var caught: Throwable? = null
            try {
                repo.observeBudgets().first()
            } catch (e: RuntimeException) {
                caught = e
            }
            assertSame(
                "observeBudgets should still throw since nextObserveBudgetsError was never cleared",
                repo.nextObserveBudgetsError,
                caught,
            )
        }

    // ---- clearBudget ----

    @Test
    fun clearBudget_removesOnlyThatCategory() =
        runBlocking {
            val repo =
                FakeBudgetRepository(
                    listOf(
                        Budget(BuiltInCategories.SHOPPING.id, 10_000L),
                        Budget(BuiltInCategories.TRANSFER.id, 5_000L),
                    ),
                )

            val result = repo.clearBudget(BuiltInCategories.SHOPPING.id)

            assertTrue(result.isSuccess)
            assertEquals(listOf(Budget(BuiltInCategories.TRANSFER.id, 5_000L)), repo.observeBudgets().first())
        }

    @Test
    fun clearBudget_missingCategory_succeeds_andLeavesStateUnchanged() =
        runBlocking {
            val seeded = listOf(Budget(BuiltInCategories.TRANSFER.id, 5_000L))
            val repo = FakeBudgetRepository(seeded)

            val result = repo.clearBudget(BuiltInCategories.SHOPPING.id)

            assertTrue(result.isSuccess)
            assertEquals(seeded, repo.observeBudgets().first())
        }

    @Test
    fun clearBudget_thenSetBudgetAgain_addsFreshEntry() =
        runBlocking {
            val repo = FakeBudgetRepository(listOf(Budget(BuiltInCategories.SHOPPING.id, 10_000L)))

            repo.clearBudget(BuiltInCategories.SHOPPING.id)
            repo.setBudget(BuiltInCategories.SHOPPING.id, 2_000L)

            assertEquals(listOf(Budget(BuiltInCategories.SHOPPING.id, 2_000L)), repo.observeBudgets().first())
        }

    // ---- one-shot failure knobs ----

    @Test
    fun nextSetBudgetError_failsOnce_leavesStateUnchanged_thenSelfClears() =
        runBlocking {
            val seeded = listOf(Budget(BuiltInCategories.SHOPPING.id, 10_000L))
            val boom = IllegalStateException("set failed")
            val repo = FakeBudgetRepository(seeded, nextSetBudgetError = boom)

            val first = repo.setBudget(BuiltInCategories.SHOPPING.id, 1L)

            assertSame(boom, first.exceptionOrNull())
            assertEquals(seeded, repo.observeBudgets().first())
            assertNull(repo.nextSetBudgetError)

            val second = repo.setBudget(BuiltInCategories.SHOPPING.id, 1L)
            assertTrue(second.isSuccess)
            assertEquals(listOf(Budget(BuiltInCategories.SHOPPING.id, 1L)), repo.observeBudgets().first())
        }

    @Test
    fun nextClearBudgetError_failsOnce_leavesStateUnchanged_thenSelfClears() =
        runBlocking {
            val seeded = listOf(Budget(BuiltInCategories.SHOPPING.id, 10_000L))
            val boom = IllegalStateException("clear failed")
            val repo = FakeBudgetRepository(seeded, nextClearBudgetError = boom)

            val first = repo.clearBudget(BuiltInCategories.SHOPPING.id)

            assertSame(boom, first.exceptionOrNull())
            assertEquals(seeded, repo.observeBudgets().first())
            assertNull(repo.nextClearBudgetError)

            val second = repo.clearBudget(BuiltInCategories.SHOPPING.id)
            assertTrue(second.isSuccess)
            assertTrue(repo.observeBudgets().first().isEmpty())
        }

    @Test
    fun nextClearBudgetError_doesNotAffectSetBudget_andViceVersa() =
        runBlocking {
            val repo = FakeBudgetRepository(nextClearBudgetError = IllegalStateException("clear failed"))

            assertTrue(repo.setBudget(BuiltInCategories.SHOPPING.id, 10_000L).isSuccess)
            assertTrue(repo.clearBudget(BuiltInCategories.SHOPPING.id).isFailure)

            repo.nextSetBudgetError = IllegalStateException("set failed")
            assertTrue(repo.clearBudget(BuiltInCategories.SHOPPING.id).isSuccess)
            assertTrue(repo.setBudget(BuiltInCategories.SHOPPING.id, 1L).isFailure)
            assertTrue(repo.observeBudgets().first().isEmpty())
        }
}
