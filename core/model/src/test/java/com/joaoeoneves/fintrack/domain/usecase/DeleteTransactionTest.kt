package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** [DeleteTransaction]: plain deletes, and deleting a recurring occurrence skips it (undo restores both). */
class DeleteTransactionTest {
    private val date = day(2026, 3, 15)
    private val occurrenceId = "rec_r1_20260315"
    private val instant = Instant.parse("2026-03-15T12:00:00Z")
    private val occurrence = testExpense(occurrenceId, 50_000L, instant)
    private val log = mutableListOf<String>()
    private val expenses = FakeExpenseRepository(listOf(occurrence))
    private val incomes = FakeIncomeRepository()
    private val recurring = FakeRecurringRepository(listOf(recurringItem(id = "r1", handledThrough = date)))
    private var nextDeleteError: Throwable? = null

    private val loggingExpenses =
        object : ExpenseRepository by expenses {
            override suspend fun deleteExpense(id: String): Result<Unit> {
                log += "delete:$id"
                nextDeleteError?.let { return Result.failure(it) }
                return expenses.deleteExpense(id)
            }

            override suspend fun addExpense(expense: Expense): Result<String> {
                log += "add:${expense.id}"
                return expenses.addExpense(expense)
            }
        }

    private val loggingRecurring =
        object : RecurringRepository by recurring {
            override suspend fun skipOccurrences(
                itemId: String,
                dates: Set<LocalDate>,
                advanceTo: LocalDate?,
            ): Result<Unit> {
                log += "skip:$itemId"
                return recurring.skipOccurrences(itemId, dates, advanceTo)
            }

            override suspend fun unskipOccurrences(
                itemId: String,
                dates: Set<LocalDate>,
            ): Result<Unit> {
                log += "unskip:$itemId"
                return recurring.unskipOccurrences(itemId, dates)
            }
        }

    private val useCase = DeleteTransaction(loggingExpenses, incomes, loggingRecurring)

    private fun skipped() = recurring.items.single().skipped

    @Test
    fun recurringExpense_isSkippedFirst_thenDeleted() =
        runBlocking {
            val result = useCase(occurrence).getOrThrow()

            assertEquals(listOf("skip:r1", "delete:$occurrenceId"), log)
            val ignored = IgnoredOccurrence("r1", "Rent", date, newlySkipped = true)
            assertEquals(DeletedTransaction(occurrence, ignored), result)
            assertEquals(setOf(date), skipped())
            assertNull(expenses.getExpense(occurrenceId).getOrThrow())
            assertEquals(date, recurring.items.single().handledThrough)
        }

    @Test
    fun undo_readdsTheExpense_thenUnskips() =
        runBlocking {
            val deleted = useCase(occurrence).getOrThrow()
            log.clear()

            assertTrue(useCase.undo(deleted).isSuccess)

            assertEquals(listOf("add:$occurrenceId", "unskip:r1"), log)
            assertEquals(occurrence, expenses.getExpense(occurrenceId).getOrThrow())
            assertTrue(skipped().isEmpty())
        }

    @Test
    fun alreadySkipped_isNotNewlySkipped_andUndoKeepsTheSkip() =
        runBlocking {
            recurring.setItems(listOf(recurring.items.single().copy(skipped = setOf(date))))

            val deleted = useCase(occurrence).getOrThrow()

            assertEquals(false, deleted.ignored?.newlySkipped)
            assertEquals(listOf("delete:$occurrenceId"), log)

            log.clear()
            assertTrue(useCase.undo(deleted).isSuccess)
            assertEquals(listOf("add:$occurrenceId"), log)
            assertEquals(setOf(date), skipped())
        }

    @Test
    fun missingItem_isAPlainDelete_andAPlainUndo() =
        runBlocking {
            recurring.setItems(emptyList())

            val deleted = useCase(occurrence).getOrThrow()

            assertEquals(DeletedTransaction(occurrence), deleted)
            assertEquals(listOf("delete:$occurrenceId"), log)
            log.clear()
            assertTrue(useCase.undo(deleted).isSuccess)
            assertEquals(listOf("add:$occurrenceId"), log)
        }

    @Test
    fun anIncomeItemWithTheSameId_isNotSkipped() =
        runBlocking {
            recurring.setItems(listOf(recurringItem(id = "r1", type = RecurringType.INCOME)))

            val deleted = useCase(occurrence).getOrThrow()

            assertNull(deleted.ignored)
            assertTrue(skipped().isEmpty())
        }

    @Test
    fun aNonRecurringExpense_isAPlainDelete() =
        runBlocking {
            val manual = testExpense("manual", 100L, instant)
            expenses.addExpense(manual)

            val deleted = useCase(manual).getOrThrow()

            assertEquals(DeletedTransaction(manual), deleted)
            assertEquals(listOf("delete:manual"), log)
            assertTrue(skipped().isEmpty())
        }

    @Test
    fun anUnreadableRecurringList_fallsBackToAPlainDelete() =
        runBlocking {
            val broken = FakeRecurringRepository(nextObserveError = IllegalStateException("offline"))

            val deleted = DeleteTransaction(loggingExpenses, incomes, broken)(occurrence).getOrThrow()

            assertNull(deleted.ignored)
            assertNull(expenses.getExpense(occurrenceId).getOrThrow())
        }

    @Test
    fun income_isDeletedAndRestoredPlainly() =
        runBlocking {
            val income = testIncome("i1", 90_000L, instant)
            incomes.addIncome(income)

            val deleted = useCase(income).getOrThrow()
            assertEquals(DeletedTransaction(income), deleted)
            assertNull(incomes.getIncome("i1").getOrThrow())

            assertTrue(useCase.undo(deleted).isSuccess)
            assertEquals(income, incomes.getIncome("i1").getOrThrow())
            assertTrue(log.isEmpty())
        }

    @Test
    fun skipFailure_deletesNothing() =
        runBlocking {
            recurring.nextSkipError = IllegalStateException("offline")

            val result = useCase(occurrence)

            assertTrue(result.isFailure)
            assertEquals(listOf("skip:r1"), log)
            assertEquals(occurrence, expenses.getExpense(occurrenceId).getOrThrow())
            assertTrue(skipped().isEmpty())
        }

    @Test
    fun deleteFailure_rollsTheSkipBack() =
        runBlocking {
            val boom = IllegalStateException("denied")
            nextDeleteError = boom

            val result = useCase(occurrence)

            assertEquals(boom, result.exceptionOrNull())
            assertEquals(listOf("skip:r1", "delete:$occurrenceId", "unskip:r1"), log)
            assertTrue(skipped().isEmpty())
            assertEquals(occurrence, expenses.getExpense(occurrenceId).getOrThrow())
        }

    @Test
    fun deleteFailure_ofAnAlreadySkippedOccurrence_keepsTheSkip() =
        runBlocking {
            recurring.setItems(listOf(recurring.items.single().copy(skipped = setOf(date))))
            nextDeleteError = IllegalStateException("denied")

            assertTrue(useCase(occurrence).isFailure)

            assertEquals(listOf("delete:$occurrenceId"), log)
            assertEquals(setOf(date), skipped())
        }

    @Test
    fun undo_whenReaddingFails_doesNotUnskip() =
        runBlocking {
            val deleted = useCase(occurrence).getOrThrow()
            val offline = Result.failure<String>(IllegalStateException("offline"))
            val failingAdd =
                object : ExpenseRepository by expenses {
                    override suspend fun addExpense(expense: Expense): Result<String> = offline
                }
            log.clear()

            val result = DeleteTransaction(failingAdd, incomes, loggingRecurring).undo(deleted)

            assertTrue(result.isFailure)
            assertTrue(log.isEmpty())
            assertEquals(setOf(date), skipped())
        }

    @Test
    fun undo_whenUnskipFails_reportsTheFailure() =
        runBlocking {
            val deleted = useCase(occurrence).getOrThrow()
            recurring.nextUnskipError = IllegalStateException("offline")

            assertTrue(useCase.undo(deleted).isFailure)
            assertEquals(occurrence, expenses.getExpense(occurrenceId).getOrThrow())
        }
}
