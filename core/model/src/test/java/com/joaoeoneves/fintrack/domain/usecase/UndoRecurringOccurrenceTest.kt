package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** [UndoRecurringOccurrence] and the amount override of [HandleRecurringOccurrence.confirm]. */
class UndoRecurringOccurrenceTest {
    private val zone = ZoneId.of("UTC")
    private val today = day(2026, 3, 20)
    private val march15 = day(2026, 3, 15)
    private val feb15 = day(2026, 2, 15)
    private val item = recurringItem(id = "r1", handledThrough = feb15)
    private val repo = FakeRecurringRepository(listOf(item))
    private val expenses = FakeExpenseRepository()
    private val handle = HandleRecurringOccurrence(repo, FakeCategoryRepository())
    private val undo = UndoRecurringOccurrence(repo, expenses)

    private fun cursor() = repo.items.single().handledThrough

    @Test
    fun confirm_withOverride_postsTheOverriddenExpenseAmount() =
        runBlocking {
            assertTrue(handle.confirm("r1", march15, today, zone, 12_345L).isSuccess)

            assertEquals(12_345L, (repo.confirmedTransactions.single() as Expense).amountCents)
            assertEquals(march15, cursor())
        }

    @Test
    fun confirm_withOverride_postsTheOverriddenIncomeAmount() =
        runBlocking {
            val salary = recurringItem(id = "s", type = RecurringType.INCOME, amountCents = 100_000L)
            val incomeRepo = FakeRecurringRepository(listOf(salary))

            HandleRecurringOccurrence(incomeRepo, FakeCategoryRepository())
                .confirm("s", day(2026, 1, 15), today, zone, 90_000L)

            assertEquals(90_000L, (incomeRepo.confirmedTransactions.single() as Income).amountCents)
        }

    @Test
    fun confirm_withoutOverride_usesTheItemAmount() =
        runBlocking {
            handle.confirm("r1", march15, today, zone, null)

            assertEquals(50_000L, (repo.confirmedTransactions.single() as Expense).amountCents)
        }

    @Test
    fun confirm_withZeroOverride_postsZero() =
        runBlocking {
            handle.confirm("r1", march15, today, zone, 0L)

            assertEquals(0L, (repo.confirmedTransactions.single() as Expense).amountCents)
        }

    @Test
    fun undoConfirm_deletesTheOccurrenceTransaction_andRewindsTheCursor() =
        runBlocking {
            handle.confirm("r1", march15, today, zone)
            expenses.addExpense(repo.confirmedTransactions.single() as Expense)

            assertTrue(undo.undoConfirm("r1", march15, feb15).isSuccess)

            assertTrue(expenses.getAllExpenses().getOrThrow().isEmpty())
            assertEquals(feb15, cursor())
        }

    @Test
    fun undoConfirm_toNullCursor_clearsIt() =
        runBlocking {
            handle.confirm("r1", march15, today, zone)

            assertTrue(undo.undoConfirm("r1", march15, null).isSuccess)

            assertEquals(null, cursor())
        }

    @Test
    fun undoConfirm_thenConfirmAgain_postsAgain() =
        runBlocking {
            handle.confirm("r1", march15, today, zone)
            undo.undoConfirm("r1", march15, feb15)

            handle.confirm("r1", march15, today, zone)

            assertEquals(2, repo.confirmedTransactions.size)
            assertEquals(march15, cursor())
        }

    @Test
    fun undoConfirm_deleteFailure_leavesTheCursor() =
        runBlocking {
            handle.confirm("r1", march15, today, zone)
            val failing = UndoRecurringOccurrence(repo, FailingDeleteExpenses())

            assertTrue(failing.undoConfirm("r1", march15, feb15).isFailure)

            assertEquals(march15, cursor())
        }

    @Test
    fun undoSkip_unskipsTheDate_andRewindsTheCursor() =
        runBlocking {
            handle.skip("r1", march15)
            assertEquals(setOf(march15), repo.items.single().skipped)

            assertTrue(undo.undoSkip("r1", march15, feb15).isSuccess)

            assertTrue(
                repo.items
                    .single()
                    .skipped
                    .isEmpty(),
            )
            assertEquals(feb15, cursor())
        }

    @Test
    fun undoSkip_keepsOtherSkippedDates() =
        runBlocking {
            repo.setItems(listOf(item.copy(skipped = setOf(day(2026, 1, 15)))))
            handle.skip("r1", march15)

            undo.undoSkip("r1", march15, feb15)

            assertEquals(setOf(day(2026, 1, 15)), repo.items.single().skipped)
        }

    @Test
    fun undoSkip_failure_isReported() =
        runBlocking {
            repo.nextUnskipError = IllegalStateException("offline")

            assertTrue(undo.undoSkip("r1", march15, feb15).isFailure)
        }

    @Test
    fun undo_missingItem_skipFails_confirmRewindsNothing() =
        runBlocking {
            // Unskipping an unknown item fails; the rewind guard only moves a cursor that sits at the occurrence.
            assertTrue(undo.undoSkip("nope", march15, null).isFailure)
            assertTrue(undo.undoConfirm("nope", march15, null).isSuccess)
            assertEquals(feb15, cursor())
        }
}

private class FailingDeleteExpenses(
    delegate: ExpenseRepository = FakeExpenseRepository(),
) : ExpenseRepository by delegate {
    override suspend fun deleteExpense(id: String): Result<Unit> = Result.failure(IllegalStateException("offline"))
}
