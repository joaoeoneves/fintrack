package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import com.joaoeoneves.fintrack.testutil.assertFailsWithError
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CombinedTransactionRepositoryTest {
    private val range = InstantRange(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"))
    private val inside = Instant.parse("2026-10-10T12:00:00Z")
    private val outside = Instant.parse("2026-11-01T00:00:00Z")

    @Test
    fun combinesBothRepositories_filteredToRange() =
        runTest {
            val expenses =
                FakeExpenseRepository(listOf(testExpense("in", 100L, inside), testExpense("out", 1L, outside)))
            val income = FakeIncomeRepository(listOf(testIncome("i", 500L, inside), testIncome("iOut", 1L, outside)))

            val tx = CombinedTransactionRepository(expenses, income).observeTransactions(range).first()

            assertEquals(range, tx.range)
            assertEquals(listOf("in"), tx.expenses.map { it.id })
            assertEquals(listOf("i"), tx.income.map { it.id })
        }

    @Test
    fun reEmits_whenEitherSideChanges() =
        runTest {
            val expenses = FakeExpenseRepository()
            val income = FakeIncomeRepository()
            val emissions = mutableListOf<TransactionsInRange>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                CombinedTransactionRepository(expenses, income).observeTransactions(range).toList(emissions)
            }

            expenses.addExpense(testExpense("e", 100L, inside))
            income.addIncome(testIncome("i", 200L, inside))

            assertEquals(3, emissions.size)
            assertEquals(listOf(0, 1, 1), emissions.map { it.expenses.size })
            assertEquals(listOf(0, 0, 1), emissions.map { it.income.size })
        }

    @Test
    fun propagatesExpenseError() =
        runTest {
            val error = IllegalStateException("expenses down")
            val repo =
                CombinedTransactionRepository(
                    FakeExpenseRepository(nextObserveExpensesError = error),
                    FakeIncomeRepository(),
                )

            assertFailsWithError(error) { repo.observeTransactions(range).first() }
        }

    @Test
    fun propagatesIncomeError() =
        runTest {
            val error = IllegalStateException("income down")
            val repo =
                CombinedTransactionRepository(
                    FakeExpenseRepository(),
                    FakeIncomeRepository(nextObserveIncomeError = error),
                )

            assertFailsWithError(error) { repo.observeTransactions(range).first() }
        }
}
