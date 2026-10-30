package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun useCase() = MoveCategoryItems(expenses, recurring, dispatcher)

    // ---- preview ----

    @Test
    fun preview_countsEverythingReferencingTheSource() =
        runTest(dispatcher) {
            val preview = useCase().preview(source.id).getOrThrow()

            assertEquals(CategoryMovePreview(2, 1), preview)
            assertFalse(preview.isEmpty)
        }

    @Test
    fun preview_nothingReferencesSource_isEmpty() =
        runTest(dispatcher) {
            val preview = useCase().preview(BuiltInCategories.TRANSFER.id).getOrThrow()

            assertEquals(CategoryMovePreview(0, 0), preview)
            assertTrue(preview.isEmpty)
        }

    @Test
    fun preview_recurringOnlyOrExpensesOnly_isNotEmpty() {
        assertFalse(CategoryMovePreview(0, 1).isEmpty)
        assertFalse(CategoryMovePreview(1, 0).isEmpty)
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
            recurring.nextObserveError = IllegalStateException("x")

            assertTrue(useCase().preview(source.id).isFailure)
        }

    // ---- invoke ----

    @Test
    fun invoke_movesExpensesAndRecurring() =
        runTest(dispatcher) {
            val result = useCase()(source.id, target.id).getOrThrow()

            assertEquals(CategoryMoveResult(2, 1), result)
            val moved = expenses.getAllExpenses().getOrThrow()
            assertEquals(listOf("a", "b", "c"), moved.map { it.id })
            assertEquals(listOf(target, target, BuiltInCategories.SHOPPING), moved.map { it.category })
            assertEquals(listOf(target.id, target.id), recurring.items.map { it.categoryId })
        }

    @Test
    fun invoke_nothingToMove_succeedsWithZeros() =
        runTest(dispatcher) {
            val result = useCase()(BuiltInCategories.TRANSFER.id, target.id).getOrThrow()

            assertEquals(CategoryMoveResult(0, 0), result)
        }

    @Test
    fun invoke_sameSourceAndTarget_failsWithoutTouchingData() =
        runTest(dispatcher) {
            val result = useCase()(source.id, source.id)

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(2, expenses.countExpensesWithCategory(source.id).getOrThrow())
        }

    @Test
    fun invoke_expenseStepFails_stopsBeforeRecurring() =
        runTest(dispatcher) {
            val boom = IllegalStateException("network")
            expenses.nextMoveExpensesError = boom

            assertEquals(boom, useCase()(source.id, target.id).exceptionOrNull())

            assertEquals(source.id, recurring.items.first { it.id == "r1" }.categoryId)
        }

    @Test
    fun invoke_recurringStepFails_expensesStayMoved_rerunFinishes() =
        runTest(dispatcher) {
            recurring.nextMoveCategoryError = IllegalStateException("boom")

            assertTrue(useCase()(source.id, target.id).isFailure)
            assertEquals(0, expenses.countExpensesWithCategory(source.id).getOrThrow())
            assertEquals(source.id, recurring.items.first { it.id == "r1" }.categoryId)

            // Re-running completes the rest; the already-moved expenses count as zero this time.
            val rerun = useCase()(source.id, target.id).getOrThrow()
            assertEquals(CategoryMoveResult(0, 1), rerun)
            assertEquals(listOf(target.id, target.id), recurring.items.map { it.categoryId })
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
