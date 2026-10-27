package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.testutil.CountingExpenseRepository
import com.joaoeoneves.fintrack.testutil.assertFailsWithError
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveBudgetProgressTest {
    private val october = YearMonth.of(2026, 10)

    private fun List<CategoryBudget>.food() = single { it.category == BuiltInCategories.FOOD }

    @Test
    fun observesOnlyTheMonth_andReEmitsOnBudgetChange() =
        runTest {
            val expenses =
                CountingExpenseRepository(
                    listOf(
                        testExpense("oct", 4_000L, Instant.parse("2026-10-31T23:59:59Z"), BuiltInCategories.FOOD),
                        testExpense("nov", 9_000L, Instant.parse("2026-11-01T00:00:00Z"), BuiltInCategories.FOOD),
                    ),
                )
            val budgets = FakeBudgetRepository()
            val emissions = mutableListOf<List<CategoryBudget>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                ObserveBudgetProgress(
                    expenses,
                    budgets,
                    FakeCategoryRepository(),
                )(october, ZoneOffset.UTC).toList(emissions)
            }

            budgets.setBudget(BuiltInCategories.FOOD.id, 3_000L)

            assertEquals(listOf(october.toInstantRange(ZoneOffset.UTC)), expenses.listeners.observedRanges)
            assertEquals(4_000L, emissions.first().food().spentCents)
            assertNull(emissions.first().food().limitCents)
            assertEquals(3_000L, emissions.last().food().limitCents)
            assertTrue(emissions.last().food().isOverBudget)
        }

    @Test
    fun budgetFlowError_propagates() =
        runTest {
            val error = IllegalStateException("budgets down")
            val budgets = FakeBudgetRepository(nextObserveBudgetsError = error)
            val useCase = ObserveBudgetProgress(CountingExpenseRepository(), budgets, FakeCategoryRepository())

            assertFailsWithError(error) { useCase(october, ZoneOffset.UTC).first() }
        }
}
