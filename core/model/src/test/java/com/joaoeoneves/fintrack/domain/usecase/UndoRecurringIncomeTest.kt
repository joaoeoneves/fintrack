package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** [UndoRecurringOccurrence.undoConfirm] of an INCOME item deletes the income, not an expense. */
class UndoRecurringIncomeTest {
    private val zone = ZoneId.of("UTC")
    private val today = day(2026, 3, 20)
    private val march15 = day(2026, 3, 15)
    private val salary =
        recurringItem(id = "s", type = RecurringType.INCOME, amountCents = 100_000L, handledThrough = day(2026, 2, 15))
    private val repo = FakeRecurringRepository(listOf(salary))
    private val incomes = FakeIncomeRepository()
    private val expenses = FakeExpenseRepository()

    @Test
    fun undoConfirm_ofAnIncomeItem_deletesTheIncome_andRewindsTheCursor() =
        runTest {
            val handle = HandleRecurringOccurrence(repo, FakeCategoryRepository())
            handle.confirm("s", march15, today, zone, null)
            // The fake recurring repo keeps its own transaction list; mirror the posted income into the income repo.
            val posted = repo.confirmedTransactions.single()
            incomes.addIncome(posted as com.joaoeoneves.fintrack.domain.model.Income)
            val undo = UndoRecurringOccurrence(repo, expenses, incomes)

            val result = undo.undoConfirm("s", march15, day(2026, 2, 15), RecurringType.INCOME)

            assertTrue(result.isSuccess)
            assertTrue(incomes.getAllIncome().getOrThrow().isEmpty())
            assertEquals(day(2026, 2, 15), repo.items.single().handledThrough)
        }

    @Test
    fun undoConfirm_withoutAnIncomeRepository_failsForAnIncomeItem() =
        runTest {
            val undo = UndoRecurringOccurrence(repo, expenses)

            assertTrue(undo.undoConfirm("s", march15, null, RecurringType.INCOME).isFailure)
        }
}
