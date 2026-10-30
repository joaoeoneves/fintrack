package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** [BulkEditExpenses]: category / meal card / delete over several expenses, each undoable. */
class BulkEditExpensesTest {
    private val at = Instant.parse("2026-03-15T12:00:00Z")
    private val a = testExpense("a", 1_000L, at, BuiltInCategories.FOOD).copy(needsReview = true)
    private val b = testExpense("b", 2_000L, at, BuiltInCategories.FOOD).copy(paidWithMealCard = true)
    private val untouched = testExpense("c", 3_000L, at, BuiltInCategories.FOOD)
    private val expenses = FakeExpenseRepository(listOf(a, b, untouched))
    private val recurring = FakeRecurringRepository(listOf(recurringItem(id = "r1", handledThrough = day(2026, 3, 15))))
    private val useCase = BulkEditExpenses(expenses, recurring)

    private suspend fun stored(): Map<String, Expense> = expenses.getAllExpenses().getOrThrow().associateBy { it.id }

    @Test
    fun changeCategory_updatesOnlyTheSelection_clearsReview_andUndoRestores() =
        runBlocking {
            val undo = useCase.changeCategory(listOf(a, b), BuiltInCategories.SHOPPING).getOrThrow()

            val after = stored()
            assertEquals(BuiltInCategories.SHOPPING, after.getValue("a").category)
            assertEquals(BuiltInCategories.SHOPPING, after.getValue("b").category)
            assertEquals(BuiltInCategories.FOOD, after.getValue("c").category)
            assertFalse(after.getValue("a").needsReview)
            assertEquals(BulkKind.CATEGORY, undo.kind)

            assertTrue(useCase.undo(undo).isSuccess)
            assertEquals(listOf(a, b, untouched), expenses.getAllExpenses().getOrThrow())
        }

    @Test
    fun setMealCard_setsAllToTheGivenValue_andUndoRestoresMixedState() =
        runBlocking {
            val on = useCase.setMealCard(listOf(a, b), enabled = true).getOrThrow()
            assertTrue(stored().getValue("a").paidWithMealCard)
            assertTrue(stored().getValue("b").paidWithMealCard)

            assertTrue(useCase.undo(on).isSuccess)
            assertFalse(stored().getValue("a").paidWithMealCard)
            assertTrue(stored().getValue("b").paidWithMealCard)

            val off = useCase.setMealCard(listOf(a, b), enabled = false).getOrThrow()
            assertEquals(BulkKind.MEAL_CARD, off.kind)
            assertFalse(stored().getValue("b").paidWithMealCard)
        }

    @Test
    fun edit_failureLeavesEverythingUnchanged() =
        runBlocking {
            val ghost = testExpense("ghost", 1L, at)

            val result = useCase.changeCategory(listOf(a, ghost), BuiltInCategories.SHOPPING)

            assertTrue(result.isFailure)
            assertEquals(listOf(a, b, untouched), expenses.getAllExpenses().getOrThrow())
        }

    @Test
    fun delete_plainExpenses_removesThem_undoReaddsWithSameIds() =
        runBlocking {
            val undo = useCase.delete(listOf(a, b)).getOrThrow()

            assertEquals(listOf("c"), stored().keys.toList())
            assertTrue(undo.skipped.isEmpty())
            assertEquals(0, recurring.calls.count { it == "skip" })

            assertTrue(useCase.undo(undo).isSuccess)
            assertEquals(setOf("a", "b", "c"), stored().keys)
            assertEquals(a, stored().getValue("a"))
        }

    @Test
    fun delete_recurringOccurrence_isSkippedFirst_thenUnskippedOnUndo() =
        runBlocking {
            val occurrence = testExpense("rec_r1_20260315", 5_000L, at)
            expenses.addExpense(occurrence)

            val undo = useCase.delete(listOf(a, occurrence)).getOrThrow()

            assertEquals(mapOf("r1" to setOf(day(2026, 3, 15))), undo.skipped)
            assertEquals(setOf(day(2026, 3, 15)), recurring.items.single().skipped)
            assertTrue(stored().keys.none { it == "a" || it.startsWith("rec_") })

            assertTrue(useCase.undo(undo).isSuccess)
            assertTrue(
                recurring.items
                    .single()
                    .skipped
                    .isEmpty(),
            )
            assertNotNull(stored()["rec_r1_20260315"])
        }

    @Test
    fun delete_alreadySkippedOccurrence_isNotNewlySkipped_andUndoKeepsTheSkip() =
        runBlocking {
            recurring.setItems(listOf(recurring.items.single().copy(skipped = setOf(day(2026, 3, 15)))))
            val occurrence = testExpense("rec_r1_20260315", 5_000L, at)
            expenses.addExpense(occurrence)

            val undo = useCase.delete(listOf(occurrence)).getOrThrow()
            assertTrue(undo.skipped.isEmpty())

            assertTrue(useCase.undo(undo).isSuccess)
            assertEquals(setOf(day(2026, 3, 15)), recurring.items.single().skipped)
        }

    @Test
    fun delete_failure_unskipsWhatItSkipped_andKeepsTheExpenses() =
        runBlocking {
            val occurrence = testExpense("rec_r1_20260315", 5_000L, at)
            expenses.addExpense(occurrence)
            val failing =
                object : ExpenseRepository by expenses {
                    override suspend fun deleteExpenses(ids: Collection<String>): Result<Unit> =
                        Result.failure(IllegalStateException("boom"))
                }

            val result = BulkEditExpenses(failing, recurring).delete(listOf(occurrence))

            assertTrue(result.isFailure)
            assertTrue(
                recurring.items
                    .single()
                    .skipped
                    .isEmpty(),
            )
            assertEquals(listOf("skip", "unskip"), recurring.calls.filter { it == "skip" || it == "unskip" })
            assertNotNull(stored()["rec_r1_20260315"])
        }

    @Test
    fun failedUndo_doesNotUnskip() =
        runBlocking {
            val occurrence = testExpense("rec_r1_20260315", 5_000L, at)
            expenses.addExpense(occurrence)
            val undo = useCase.delete(listOf(occurrence)).getOrThrow()
            val failing =
                object : ExpenseRepository by expenses {
                    override suspend fun addExpensesKeepingIds(expenses: List<Expense>): Result<List<String>> =
                        Result.failure(IllegalStateException("boom"))
                }

            assertTrue(BulkEditExpenses(failing, recurring).undo(undo).isFailure)

            assertEquals(setOf(day(2026, 3, 15)), recurring.items.single().skipped)
            assertTrue(recurring.observeRecurring().first().isNotEmpty())
        }
}
