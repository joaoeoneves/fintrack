package ptech.joaoe.agenticusage.data

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
import ptech.joaoe.agenticusage.domain.model.Budget
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory

/**
 * Unit tests for [FakeBudgetRepository], which is also the de-facto contract test for
 * [ptech.joaoe.agenticusage.domain.repository.BudgetRepository] since it is the only
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
            val seeded = listOf(Budget(ExpenseCategory.SHOPPING, 10_000L), Budget(ExpenseCategory.TRANSFER, 5_000L))
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

            repo.setBudget(ExpenseCategory.SHOPPING, 12_345L)
            yield() // let the collector's suspended continuation process the new emission

            job.cancel()

            assertEquals(2, snapshots.size)
            assertTrue("initial emission should be empty", snapshots[0].isEmpty())
            assertEquals(listOf(Budget(ExpenseCategory.SHOPPING, 12_345L)), snapshots[1])
        }

    // ---- setBudget: upserts (replaces existing entry rather than duplicating) ----

    @Test
    fun setBudget_newCategory_addsEntry() =
        runBlocking {
            val repo = FakeBudgetRepository()

            val result = repo.setBudget(ExpenseCategory.SHOPPING, 10_000L)

            assertTrue(result.isSuccess)
            val stored = repo.observeBudgets().first()
            assertEquals(listOf(Budget(ExpenseCategory.SHOPPING, 10_000L)), stored)
        }

    @Test
    fun setBudget_existingCategory_replacesLimitRatherThanDuplicating() =
        runBlocking {
            val repo = FakeBudgetRepository(listOf(Budget(ExpenseCategory.SHOPPING, 10_000L)))

            val result = repo.setBudget(ExpenseCategory.SHOPPING, 20_000L)

            assertTrue(result.isSuccess)
            val stored = repo.observeBudgets().first()
            assertEquals(1, stored.size)
            assertEquals(20_000L, stored.single { it.category == ExpenseCategory.SHOPPING }.limitCents)
        }

    @Test
    fun setBudget_multipleCategories_eachTrackedIndependently() =
        runBlocking {
            val repo = FakeBudgetRepository()

            repo.setBudget(ExpenseCategory.SHOPPING, 10_000L)
            repo.setBudget(ExpenseCategory.TRANSFER, 5_000L)
            repo.setBudget(ExpenseCategory.SHOPPING, 15_000L) // update, not a new entry

            val stored = repo.observeBudgets().first().associateBy { it.category }
            assertEquals(2, stored.size)
            assertEquals(15_000L, stored.getValue(ExpenseCategory.SHOPPING).limitCents)
            assertEquals(5_000L, stored.getValue(ExpenseCategory.TRANSFER).limitCents)
        }

    @Test
    fun setBudget_acceptsZeroAndNegativeLimitCents_withoutValidation() =
        runBlocking {
            // FakeBudgetRepository performs no validation of limitCents; this test documents that
            // contract so a future validating implementation change is caught deliberately, not
            // accidentally.
            val repo = FakeBudgetRepository()

            val zeroResult = repo.setBudget(ExpenseCategory.SHOPPING, 0L)
            val negativeResult = repo.setBudget(ExpenseCategory.TRANSFER, -500L)

            assertTrue(zeroResult.isSuccess)
            assertTrue(negativeResult.isSuccess)
            val stored = repo.observeBudgets().first().associateBy { it.category }
            assertEquals(0L, stored.getValue(ExpenseCategory.SHOPPING).limitCents)
            assertEquals(-500L, stored.getValue(ExpenseCategory.TRANSFER).limitCents)
        }

    // ---- nextObserveBudgetsError ----

    @Test
    fun observeBudgets_defaultNullError_behavesNormally() =
        runBlocking {
            val repo = FakeBudgetRepository(listOf(Budget(ExpenseCategory.SHOPPING, 10_000L)))

            assertNull(repo.nextObserveBudgetsError)
            val result = repo.observeBudgets().first()

            assertEquals(listOf(Budget(ExpenseCategory.SHOPPING, 10_000L)), result)
        }

    @Test
    fun observeBudgets_withErrorSet_flowThrowsThatExactThrowable() =
        runBlocking {
            val repo = FakeBudgetRepository(listOf(Budget(ExpenseCategory.SHOPPING, 10_000L)))
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
            val repo = FakeBudgetRepository(listOf(Budget(ExpenseCategory.SHOPPING, 10_000L)))
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

            val result = repo.setBudget(ExpenseCategory.SHOPPING, 10_000L)

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
}
