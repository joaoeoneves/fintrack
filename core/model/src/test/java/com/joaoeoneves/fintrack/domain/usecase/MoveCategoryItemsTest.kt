package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

/** [MoveCategoryItems] preview and invoke on top of the in-memory fakes. */
@OptIn(ExperimentalCoroutinesApi::class)
class MoveCategoryItemsTest {
    private val source = BuiltInCategories.RECURRING
    private val target = BuiltInCategories.FOOD
    private val date = Instant.parse("2026-10-10T12:00:00Z")
    private val dispatcher = StandardTestDispatcher()

    private val expenses =
        FakeExpenseRepository(
            listOf(
                testExpense("a", 100L, date, source),
                testExpense("b", 200L, date, source),
                testExpense("c", 300L, date, BuiltInCategories.SHOPPING),
            ),
        )
    private val recurring =
        FakeRecurringRepository(
            listOf(
                recurringItem("r1", categoryId = source.id),
                recurringItem("r2", categoryId = BuiltInCategories.FOOD.id),
            ),
        )
    private val budgets = FakeBudgetRepository(listOf(Budget(source.id, 9_000L)))

    private fun useCase() = MoveCategoryItems(expenses, budgets, recurring, dispatcher)

    // ---- preview ----

    @Test
    fun preview_countsEverythingReferencingTheSource() =
        runTest(dispatcher) {
            val preview = useCase().preview(source.id).getOrThrow()

            assertEquals(CategoryMovePreview(2, 1, hasBudget = true, targetHasBudget = false), preview)
            assertFalse(preview.isEmpty)
        }

    @Test
    fun preview_withTarget_reportsTargetBudget() =
        runTest(dispatcher) {
            budgets.setBudget(target.id, 1_000L)

            assertTrue(useCase().preview(source.id, target.id).getOrThrow().targetHasBudget)
            assertFalse(useCase().preview(source.id).getOrThrow().targetHasBudget)
        }

    @Test
    fun preview_nothingReferencesSource_isEmpty() =
        runTest(dispatcher) {
            val preview = useCase().preview(BuiltInCategories.TRANSFER.id).getOrThrow()

            assertEquals(CategoryMovePreview(0, 0, hasBudget = false, targetHasBudget = false), preview)
            assertTrue(preview.isEmpty)
        }

    @Test
    fun preview_budgetOnlyOrRecurringOnly_isNotEmpty() {
        assertFalse(CategoryMovePreview(0, 0, hasBudget = true, targetHasBudget = false).isEmpty)
        assertFalse(CategoryMovePreview(0, 1, hasBudget = false, targetHasBudget = false).isEmpty)
        assertFalse(CategoryMovePreview(1, 0, hasBudget = false, targetHasBudget = false).isEmpty)
    }

    @Test
    fun preview_countFailure_isReturnedAsFailure() =
        runTest(dispatcher) {
            val boom = IllegalStateException("offline")
            expenses.nextCountExpensesError = boom

            assertEquals(boom, useCase().preview(source.id).exceptionOrNull())
        }

    @Test
    fun preview_observeFailure_isReturnedAsFailure() =
        runTest(dispatcher) {
            budgets.nextObserveBudgetsError = IllegalStateException("x")

            assertTrue(useCase().preview(source.id).isFailure)
        }

    // ---- invoke ----

    @Test
    fun invoke_movesExpensesRecurringAndBudget() =
        runTest(dispatcher) {
            val result = useCase()(source.id, target.id).getOrThrow()

            assertEquals(CategoryMoveResult(2, 1, budgetMoved = true), result)
            val moved = expenses.getAllExpenses().getOrThrow()
            assertEquals(listOf("a", "b", "c"), moved.map { it.id })
            assertEquals(listOf(target, target, BuiltInCategories.SHOPPING), moved.map { it.category })
            assertEquals(listOf(target.id, target.id), recurring.items.map { it.categoryId })
            assertEquals(listOf(Budget(target.id, 9_000L)), budgets.observeBudgets().first())
        }

    @Test
    fun invoke_targetKeepsItsOwnBudget_sourcesDropped() =
        runTest(dispatcher) {
            budgets.setBudget(target.id, 1_000L)

            val result = useCase()(source.id, target.id).getOrThrow()

            assertTrue(result.budgetMoved)
            assertEquals(listOf(Budget(target.id, 1_000L)), budgets.observeBudgets().first())
        }

    @Test
    fun invoke_nothingToMove_succeedsWithZeros() =
        runTest(dispatcher) {
            val result = useCase()(BuiltInCategories.TRANSFER.id, target.id).getOrThrow()

            assertEquals(CategoryMoveResult(0, 0, budgetMoved = false), result)
        }

    @Test
    fun invoke_sameSourceAndTarget_failsWithoutTouchingData() =
        runTest(dispatcher) {
            val result = useCase()(source.id, source.id)

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(2, expenses.countExpensesWithCategory(source.id).getOrThrow())
        }

    @Test
    fun invoke_expenseStepFails_stopsBeforeRecurringAndBudget() =
        runTest(dispatcher) {
            val boom = IllegalStateException("network")
            expenses.nextMoveExpensesError = boom

            assertEquals(boom, useCase()(source.id, target.id).exceptionOrNull())

            assertEquals(source.id, recurring.items.first { it.id == "r1" }.categoryId)
            assertEquals(listOf(Budget(source.id, 9_000L)), budgets.observeBudgets().first())
        }

    @Test
    fun invoke_recurringStepFails_expensesStayMoved_budgetUntouched_rerunFinishes() =
        runTest(dispatcher) {
            recurring.nextMoveCategoryError = IllegalStateException("boom")

            assertTrue(useCase()(source.id, target.id).isFailure)
            assertEquals(0, expenses.countExpensesWithCategory(source.id).getOrThrow())
            assertEquals(source.id, recurring.items.first { it.id == "r1" }.categoryId)
            assertEquals(listOf(Budget(source.id, 9_000L)), budgets.observeBudgets().first())

            // Re-running completes the rest; the already-moved expenses count as zero this time.
            val rerun = useCase()(source.id, target.id).getOrThrow()
            assertEquals(CategoryMoveResult(0, 1, budgetMoved = true), rerun)
            assertEquals(listOf(Budget(target.id, 9_000L)), budgets.observeBudgets().first())
        }

    @Test
    fun invoke_budgetStepFails_earlierStepsStay_rerunFinishes() =
        runTest(dispatcher) {
            budgets.nextMoveBudgetError = IllegalStateException("boom")

            assertTrue(useCase()(source.id, target.id).isFailure)
            assertEquals(listOf(target.id, target.id), recurring.items.map { it.categoryId })
            assertEquals(listOf(Budget(source.id, 9_000L)), budgets.observeBudgets().first())

            assertEquals(CategoryMoveResult(0, 0, budgetMoved = true), useCase()(source.id, target.id).getOrThrow())
            assertEquals(listOf(Budget(target.id, 9_000L)), budgets.observeBudgets().first())
        }

    @Test
    fun invoke_cancellation_isRethrownNotCaptured() =
        runTest(dispatcher) {
            expenses.nextMoveExpensesError = CancellationException("cancelled")

            try {
                useCase()(source.id, target.id)
                fail("expected CancellationException")
            } catch (_: CancellationException) {
                // expected
            }
        }
}
