package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.testutil.day
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** [AutoPostRecurring.run]: `failed` is set by a failed read or write, and only then. */
class AutoPostRecurringOutcomeTest : AutoPostRecurringTestBase() {
    @Test
    fun noItems_isNotFailed() =
        runBlocking {
            assertEquals(AutomationOutcome(0), build(FakeRecurringRepository()).run(today, zone))
        }

    @Test
    fun nothingDue_isNotFailed() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto(handled = day(2026, 3, 15))))

            val expenses = expensesFor(day(2026, 1, 15), day(2026, 2, 15), day(2026, 3, 15))

            val outcome = build(repo, expenses).run(today, zone)

            assertEquals(AutomationOutcome(0), outcome)
        }

    @Test
    fun postedEverything_isNotFailed_andCountsThem() =
        runBlocking {
            val outcome = build(FakeRecurringRepository(listOf(auto()))).run(today, zone)

            assertEquals(AutomationOutcome(2), outcome)
        }

    @Test
    fun aFailedWrite_isFailed_withThePartialCount() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto()), nextConfirmError = IllegalStateException("offline"))

            val outcome = build(repo).run(today, zone)

            // Feb 15 is rejected and stops the item, so nothing was created.
            assertEquals(AutomationOutcome(0, failed = true), outcome)
        }

    @Test
    fun aFailedHealRead_isFailed() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto(handled = day(2026, 3, 15))))
            val expenses = expensesFor(day(2026, 1, 15))
            expenses.nextExpenseIdsError = IllegalStateException("offline")

            val outcome = build(repo, expenses).run(today, zone)

            assertEquals(true, outcome.failed)
            assertEquals(0, outcome.count)
        }

    @Test
    fun oneItemFailing_marksTheWholeRunFailed_butOthersStillPost() =
        runBlocking {
            val repo =
                FakeRecurringRepository(listOf(auto("a"), auto("b")), nextConfirmError = IllegalStateException("boom"))

            val expenses =
                FakeExpenseRepository(listOf(rentExpense(day(2026, 1, 15), "a"), rentExpense(day(2026, 1, 15), "b")))

            val outcome = build(repo, expenses).run(today, zone)

            assertEquals(true, outcome.failed)
            assertEquals(2, outcome.count)
        }
}
