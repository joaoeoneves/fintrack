package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [AutoPostRecurring] self-healing: re-creates handled occurrences whose transaction is gone. */
class AutoPostRecurringHealTest : AutoPostRecurringTestBase() {
    private val jan15 = day(2026, 1, 15)
    private val feb15 = day(2026, 2, 15)
    private val mar15 = day(2026, 3, 15)

    private suspend fun ids(expenses: FakeExpenseRepository) =
        expenses
            .getAllExpenses()
            .getOrThrow()
            .map { it.id }
            .sorted()

    @Test
    fun heals_missingHandledOccurrences_underTheSameId_withoutMovingTheCursor() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto(handled = mar15)))
            val expenses = expensesFor(jan15)

            val created = build(repo, expenses)(today, zone)

            assertEquals(2, created)
            assertEquals(listOf("rec_r1_20260115", "rec_r1_20260215", "rec_r1_20260315"), ids(expenses))
            val healed = expenses.getExpense("rec_r1_20260215").getOrThrow()!!
            assertEquals(50_000L, healed.amountCents)
            assertEquals("Rent", healed.name)
            assertEquals(mar15, repo.items.single().handledThrough)
            assertTrue(repo.confirmedTransactions.isEmpty())
        }

    @Test
    fun doesNotRecreate_skippedOccurrences() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto(handled = mar15, skipped = setOf(feb15))))
            val expenses = expensesFor(jan15, mar15)

            assertEquals(0, build(repo, expenses)(today, zone))

            assertEquals(listOf("rec_r1_20260115", "rec_r1_20260315"), ids(expenses))
        }

    @Test
    fun skippedOnADifferentDayOfTheSameMonth_stillSuppressesTheHeal() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto(handled = mar15, skipped = setOf(day(2026, 2, 10)))))
            val expenses = expensesFor(jan15, mar15)

            assertEquals(0, build(repo, expenses)(today, zone))
        }

    @Test
    fun secondRun_isIdempotent() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto(handled = mar15)))
            val expenses = expensesFor(jan15)
            val useCase = build(repo, expenses)
            useCase(today, zone)

            assertEquals(0, useCase(today, zone))

            assertEquals(3, ids(expenses).size)
        }

    @Test
    fun aTransactionOnAnotherDayOfTheSameMonth_countsAsPresent() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto(handled = mar15)))
            val expenses = expensesFor(jan15, mar15)
            expenses.addExpense(rentExpense(day(2026, 2, 10)))

            assertEquals(0, build(repo, expenses)(today, zone))
        }

    @Test
    fun aFailingRead_skipsTheHeal_butDueOccurrencesStillPost() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto(handled = jan15)))
            val expenses = FakeExpenseRepository(emptyList())
            expenses.nextExpenseIdsError = IllegalStateException("offline")

            val created = build(repo, expenses)(today, zone)

            // Jan 15 stays missing (no heal) but Feb 15 and Mar 15 are posted.
            assertEquals(2, created)
            assertEquals(listOf("rec_r1_20260215", "rec_r1_20260315"), repo.confirmedTransactions.map { it.id })
            assertTrue(ids(expenses).isEmpty())
            assertEquals(mar15, repo.items.single().handledThrough)
        }

    @Test
    fun healAndDuePosting_runTogether() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto(handled = feb15)))
            val expenses = expensesFor(jan15)

            val created = build(repo, expenses)(today, zone)

            assertEquals(2, created)
            assertEquals(listOf("rec_r1_20260315"), repo.confirmedTransactions.map { it.id })
            assertEquals(listOf("rec_r1_20260115", "rec_r1_20260215"), ids(expenses))
            assertEquals(mar15, repo.items.single().handledThrough)
        }

    @Test
    fun pausedAndManualItems_areNotHealed() =
        runBlocking {
            val paused = auto(id = "p", handled = mar15).copy(active = false)
            val manual = recurringItem(id = "m", handledThrough = mar15)
            val repo = FakeRecurringRepository(listOf(paused, manual))
            val expenses = FakeExpenseRepository()

            assertEquals(0, build(repo, expenses)(today, zone))

            assertTrue(ids(expenses).isEmpty())
        }

    @Test
    fun anItemWithoutACursor_isNotHealed() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto(handled = null).copy(startDate = day(2026, 3, 1))))
            val expenses = FakeExpenseRepository()

            // Nothing before the cursor to heal; only the due March occurrence is posted.
            assertEquals(1, build(repo, expenses)(today, zone))
            assertTrue(ids(expenses).isEmpty())
        }

    @Test
    fun yearlyItem_healsOnePerYear_matchingAnyDayOfTheYear() =
        runBlocking {
            val yearly =
                recurringItem(
                    id = "y",
                    frequency = RecurringFrequency.YEARLY,
                    startDate = day(2024, 1, 1),
                    handledThrough = mar15,
                    autoPost = true,
                )
            val expenses = FakeExpenseRepository()

            assertEquals(1, build(FakeRecurringRepository(listOf(yearly)), expenses)(today, zone))
            assertEquals(listOf("rec_y_20260315"), ids(expenses))

            val withOtherDay = FakeExpenseRepository(listOf(rentExpense(day(2026, 3, 1), "y")))
            assertEquals(0, build(FakeRecurringRepository(listOf(yearly)), withOtherDay)(today, zone))
        }
}
