package com.joaoeoneves.fintrack.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/** [LocalExpenseRepository.expenseIdsBetween]: the inclusive lexicographic id range. */
class LocalExpenseRepositoryIdsBetweenTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalExpenseRepository(store, SequenceIds()) }

    private suspend fun seed(vararg ids: String) = ids.forEach { repo.addExpense(expense(id = it)) }

    @Test
    fun bounds_areInclusive() =
        runStoreTest {
            seed("rec_r1_20260301", "rec_r1_20260315", "rec_r1_20260331", "rec_r1_20260401", "rec_r1_20260228")

            val ids = repo.expenseIdsBetween("rec_r1_20260301", "rec_r1_20260331").getOrThrow()

            assertEquals(setOf("rec_r1_20260301", "rec_r1_20260315", "rec_r1_20260331"), ids)
        }

    @Test
    fun otherItemsAndPlainIds_areExcluded() =
        runStoreTest {
            seed("rec_r1_20260315", "rec_r2_20260315", "abc")

            val ids = repo.expenseIdsBetween("rec_r1_20260301", "rec_r1_20260331").getOrThrow()
            assertEquals(setOf("rec_r1_20260315"), ids)
        }

    @Test
    fun emptyStoreOrRange_isEmpty() =
        runStoreTest {
            assertEquals(emptySet<String>(), repo.expenseIdsBetween("a", "z").getOrThrow())
            seed("m")
            assertEquals(emptySet<String>(), repo.expenseIdsBetween("z", "a").getOrThrow())
        }
}
