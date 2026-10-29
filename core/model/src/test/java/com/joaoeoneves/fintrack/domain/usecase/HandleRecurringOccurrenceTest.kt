package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** [HandleRecurringOccurrence]: confirm creates + advances, skip only advances, re-reads, no-ops, refusals. */
class HandleRecurringOccurrenceTest {
    private val zone = ZoneId.of("UTC")
    private val today = day(2026, 3, 20)
    private val march15 = day(2026, 3, 15)
    private val item = recurringItem(id = "r1", handledThrough = day(2026, 2, 15))
    private val repo = FakeRecurringRepository(listOf(item))
    private val useCase = HandleRecurringOccurrence(repo, FakeCategoryRepository())

    private fun cursor() = repo.items.single().handledThrough

    @Test
    fun confirm_createsTheTransaction_andAdvancesTheCursor() =
        runBlocking {
            assertTrue(useCase.confirm("r1", march15, today, zone).isSuccess)

            val expense = repo.confirmedTransactions.single() as Expense
            assertEquals("rec_r1_20260315", expense.id)
            assertEquals(50_000L, expense.amountCents)
            assertEquals(march15, cursor())
        }

    @Test
    fun confirm_income_createsAnIncome() =
        runBlocking {
            val salary = recurringItem(id = "s", type = RecurringType.INCOME, name = "Salary")
            val incomeRepo = FakeRecurringRepository(listOf(salary))

            HandleRecurringOccurrence(incomeRepo, FakeCategoryRepository()).confirm("s", day(2026, 1, 15), today, zone)

            assertEquals("Salary", (incomeRepo.confirmedTransactions.single() as Income).source)
        }

    @Test
    fun confirm_forReview_flagsTheExpense_andTheDefaultDoesNot() =
        runBlocking {
            useCase.confirm("r1", march15, today, zone)
            val plain = repo.confirmedTransactions.single() as Expense
            assertEquals(false, plain.needsReview)

            val other = FakeRecurringRepository(listOf(item))
            HandleRecurringOccurrence(other, FakeCategoryRepository())
                .confirm("r1", march15, today, zone, forReview = true)

            assertEquals(true, (other.confirmedTransactions.single() as Expense).needsReview)
        }

    @Test
    fun confirm_forReview_flagsTheIncome_keepingAnAmountOverride() =
        runBlocking {
            val salary = recurringItem(id = "s", type = RecurringType.INCOME, name = "Salary")
            val incomeRepo = FakeRecurringRepository(listOf(salary))

            HandleRecurringOccurrence(incomeRepo, FakeCategoryRepository())
                .confirm("s", day(2026, 1, 15), today, zone, amountOverrideCents = 7_000L, forReview = true)

            val income = incomeRepo.confirmedTransactions.single() as Income
            assertEquals(true, income.needsReview)
            assertEquals(7_000L, income.amountCents)
        }

    @Test
    fun confirm_today_isAllowed() =
        runBlocking {
            assertTrue(useCase.confirm("r1", march15, march15, zone).isSuccess)
        }

    @Test
    fun confirm_twice_isANoOpSuccess() =
        runBlocking {
            useCase.confirm("r1", march15, today, zone)

            assertTrue(useCase.confirm("r1", march15, today, zone).isSuccess)

            assertEquals(1, repo.confirmedTransactions.size)
        }

    @Test
    fun confirm_alreadyHandled_isNoOpEvenForAFutureOccurrence() =
        runBlocking {
            repo.setItems(listOf(item.copy(handledThrough = day(2026, 6, 15))))

            assertTrue(useCase.confirm("r1", day(2026, 6, 15), today, zone).isSuccess)

            assertTrue(repo.confirmedTransactions.isEmpty())
        }

    @Test
    fun confirm_futureOccurrence_isRefused() =
        runBlocking {
            val result = useCase.confirm("r1", day(2026, 3, 21), today, zone)

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(repo.confirmedTransactions.isEmpty())
            assertEquals(day(2026, 2, 15), cursor())
        }

    @Test
    fun confirm_andSkip_missingItem_failWithNoSuchElement() =
        runBlocking {
            assertTrue(useCase.confirm("nope", march15, today, zone).exceptionOrNull() is NoSuchElementException)
            assertTrue(useCase.skip("nope", march15).exceptionOrNull() is NoSuchElementException)
        }

    @Test
    fun skip_recordsTheOccurrence_andAdvancesTheCursor() =
        runBlocking {
            assertTrue(useCase.skip("r1", march15).isSuccess)

            assertEquals(march15, cursor())
            assertEquals(setOf(march15), repo.items.single().skipped)
            assertTrue(repo.confirmedTransactions.isEmpty())
        }

    @Test
    fun skip_alreadySkipped_isNoOp() =
        runBlocking {
            repo.setItems(listOf(item.copy(skipped = setOf(march15))))

            assertTrue(useCase.skip("r1", march15).isSuccess)

            assertEquals(day(2026, 2, 15), cursor())
            assertTrue(repo.calls.isEmpty())
        }

    @Test
    fun skip_failure_changesNothing() =
        runBlocking {
            repo.nextSkipError = IllegalStateException("offline")

            assertTrue(useCase.skip("r1", march15).isFailure)

            assertTrue(
                repo.items
                    .single()
                    .skipped
                    .isEmpty(),
            )
            assertEquals(day(2026, 2, 15), cursor())
        }

    @Test
    fun skip_alreadyHandled_isNoOp() =
        runBlocking {
            assertTrue(useCase.skip("r1", day(2026, 2, 15)).isSuccess)
            assertTrue(useCase.skip("r1", day(2026, 1, 15)).isSuccess)

            assertEquals(day(2026, 2, 15), cursor())
            assertTrue(
                repo.items
                    .single()
                    .skipped
                    .isEmpty(),
            )
        }

    @Test
    fun repositoryFailures_surfaceAsFailure_andCancellationIsRethrown() =
        runBlocking {
            val boom = IllegalStateException("offline")
            repo.nextConfirmError = boom
            assertSame(boom, useCase.confirm("r1", march15, today, zone).exceptionOrNull())
            assertEquals(day(2026, 2, 15), cursor())

            repo.nextSkipError = CancellationException("cancelled")
            assertThrows(CancellationException::class.java) { runBlocking { useCase.skip("r1", march15) } }
            Unit
        }
}
