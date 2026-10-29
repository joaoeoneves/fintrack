package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBudgetRepositoryTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalBudgetRepository(store) }
    private val food = CategoryId("FOOD")

    @Test
    fun observe_empty() =
        runStoreTest {
            assertEquals(emptyList<Budget>(), repo.observeBudgets().first())
        }

    @Test
    fun set_upsertsPerCategory() =
        runStoreTest {
            assertTrue(repo.setBudget(food, 100).isSuccess)
            assertTrue(repo.setBudget(CategoryId("SHOPPING"), 50).isSuccess)
            assertTrue(repo.setBudget(food, 200).isSuccess)
            assertEquals(
                listOf(Budget(food, 200), Budget(CategoryId("SHOPPING"), 50)),
                repo.observeBudgets().first().untimed(),
            )
        }

    @Test
    fun set_limitZeroOrNegative_failsNothingWritten() =
        runStoreTest {
            assertTrue(repo.setBudget(food, 0).exceptionOrNull() is IllegalArgumentException)
            assertTrue(repo.setBudget(food, -1).isFailure)
            assertFalse(dataFile.exists())
        }

    @Test
    fun set_limitOne_accepted() =
        runStoreTest {
            assertTrue(repo.setBudget(food, 1).isSuccess)
        }

    @Test
    fun set_unknownCategory_fails_storedCustomAccepted() =
        runStoreTest {
            assertTrue(repo.setBudget(CategoryId("custom"), 10).isFailure)
            store.seedCategory("custom")
            assertTrue(repo.setBudget(CategoryId("custom"), 10).isSuccess)
        }

    @Test
    fun clear_removesBudget() =
        runStoreTest {
            repo.setBudget(food, 100)
            assertTrue(repo.clearBudget(food).isSuccess)
            assertEquals(emptyList<Budget>(), repo.observeBudgets().first())
        }

    @Test
    fun clear_missing_succeedsWithoutRevisionBump() =
        runStoreTest {
            repo.setBudget(food, 100)
            assertTrue(repo.clearBudget(CategoryId("SHOPPING")).isSuccess)
            assertEquals(1L, store.data.first().revision)
        }

    @Test
    fun budgets_persistAcrossInstances() =
        runStoreTest {
            repo.setBudget(food, 123)
            val reloaded = LocalBudgetRepository(newStore()).observeBudgets().first().untimed()
            assertEquals(listOf(Budget(food, 123)), reloaded)
        }
}
