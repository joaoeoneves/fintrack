package com.joaoeoneves.fintrack.data.local

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LocalExpenseRepository.observeHasAnyExpense]: the guest store's "at least one expense" flag. */
class LocalExpenseRepositoryHasAnyTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalExpenseRepository(store, SequenceIds()) }

    @Test
    fun emptyStore_isFalse() =
        runStoreTest {
            assertFalse(repo.observeHasAnyExpense().first())
        }

    @Test
    fun afterAnAdd_isTrue() =
        runStoreTest {
            repo.addExpense(expense())

            assertTrue(repo.observeHasAnyExpense().first())
        }

    @Test
    fun followsDeletes() =
        runStoreTest {
            repo.addExpense(expense(id = "a"))
            repo.addExpense(expense(id = "b"))

            repo.deleteExpense("a")
            assertTrue(repo.observeHasAnyExpense().first())

            repo.deleteExpense("b")
            assertFalse(repo.observeHasAnyExpense().first())
        }

    @Test
    fun ignoresOtherData() =
        runStoreTest {
            store.seedCategory("c1")

            assertFalse(repo.observeHasAnyExpense().first())
        }
}
