package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.CombinedTransactionRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.CashFlowSummary
import com.joaoeoneves.fintrack.domain.model.InstantRange
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
class ObserveCashFlowSummaryTest {
    private val range = InstantRange(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"))
    private val inside = Instant.parse("2026-10-10T12:00:00Z")

    @Test
    fun summarizesTransactionsInRange_andReEmits() =
        runTest {
            val expenses = FakeExpenseRepository(listOf(testExpense("e", 3_000L, inside)))
            val income = FakeIncomeRepository(listOf(testIncome("i", 10_000L, inside)))
            val emissions = mutableListOf<CashFlowSummary>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                ObserveCashFlowSummary(
                    CombinedTransactionRepository(expenses, income),
                    FakeCategoryRepository(),
                )(range).toList(emissions)
            }

            expenses.addExpense(testExpense("outside", 99L, Instant.parse("2026-11-01T00:00:00Z")))
            expenses.addExpense(testExpense("r", 1_000L, inside, isRefund = true))

            assertEquals(7_000L, emissions.first().netCents)
            assertEquals(2_000L, emissions.last().spendingCents)
            assertEquals(8_000L, emissions.last().netCents)
        }

    @Test
    fun propagatesRepositoryError() =
        runTest {
            val error = IllegalStateException("down")
            val useCase =
                ObserveCashFlowSummary(
                    CombinedTransactionRepository(
                        FakeExpenseRepository(nextObserveExpensesError = error),
                        FakeIncomeRepository(),
                    ),
                    FakeCategoryRepository(),
                )

            assertFailsWithError(error) { useCase(range).first() }
        }
}
