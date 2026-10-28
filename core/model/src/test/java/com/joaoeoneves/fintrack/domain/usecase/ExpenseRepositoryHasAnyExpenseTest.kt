package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The default body of `ExpenseRepository.observeHasAnyExpense` (the fake does not override it). */
class ExpenseRepositoryHasAnyExpenseTest {
    private fun expense(
        id: String,
        date: String = "2026-10-05T10:00:00Z",
    ) = Expense(id, id, 100L, BuiltInCategories.FOOD, Instant.parse(date), null)

    @Test
    fun empty_isFalse() =
        runTest {
            assertFalse(FakeExpenseRepository().observeHasAnyExpense().first())
        }

    @Test
    fun withAnExpense_isTrue() =
        runTest {
            assertTrue(FakeExpenseRepository(listOf(expense("a"))).observeHasAnyExpense().first())
        }

    @Test
    fun looksAtAllTime_notOnlyRecentExpenses() =
        runTest {
            val ancient = expense("old", date = "1971-01-01T00:00:00Z")

            assertTrue(FakeExpenseRepository(listOf(ancient)).observeHasAnyExpense().first())
        }

    @Test
    fun emitsOnlyChanges() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeExpenseRepository()
            val seen = mutableListOf<Boolean>()
            val job = launch { repo.observeHasAnyExpense().toList(seen) }

            repo.addExpense(expense("a"))
            repo.addExpense(expense("b"))
            repo.deleteExpense("a")
            repo.deleteExpense("b")

            assertEquals(listOf(false, true, false), seen)
            job.cancel()
        }
}
