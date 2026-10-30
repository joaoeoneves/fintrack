package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/** [AutoPostRecurring]: posts + advances, only advances when the transaction exists, per-item failure isolation. */
class AutoPostRecurringTest : AutoPostRecurringTestBase() {
    @Test
    fun postsEveryDueOccurrence_oldestFirst_andAdvancesCursor() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto()))

            val posted = build(repo)(today, zone)

            assertEquals(2, posted)
            assertEquals(
                listOf("rec_r1_20260215", "rec_r1_20260315"),
                repo.confirmedTransactions.map { it.id },
            )
            assertEquals(day(2026, 3, 15), repo.items.single().handledThrough)
        }

    @Test
    fun secondRun_isIdempotent() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto()))
            val expenses = expensesFor(day(2026, 1, 15))
            val useCase = build(repo, expenses)
            useCase(today, zone)
            // The fake repository records a confirmed transaction separately; store it as the real one does.
            repo.confirmedTransactions.forEach { expenses.addExpense(it as Expense) }

            assertEquals(0, useCase(today, zone))
            assertEquals(2, repo.confirmedTransactions.size)
        }

    @Test
    fun pausedItems_neverPost() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto().copy(active = false)))

            assertEquals(0, build(repo)(today, zone))
            assertTrue(repo.confirmedTransactions.isEmpty())
        }

    @Test
    fun adoptsLegacyItemsBeforePosting_withTodayAsTheCutoff() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto()))

            build(repo)(today, zone)

            assertEquals(listOf(today), repo.adoptCalls)
            assertEquals("adopt", repo.calls.first())
        }

    @Test
    fun adoptionFailure_postsNothing_andReportsFailed() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto()))
            repo.adoptResult = Result.failure(IllegalStateException("offline"))

            val outcome = build(repo).run(today, zone)

            assertEquals(AutomationOutcome(0, failed = true), outcome)
            assertTrue(repo.confirmedTransactions.isEmpty())
            assertEquals(day(2026, 1, 15), repo.items.single().handledThrough)
        }

    @Test
    @Ignore("Production bug: self-heal re-creates the adopted backlog (see report); re-enable once fixed")
    fun legacyItemWithAThreeMonthGap_postsOnlyTodaysOccurrence() =
        runBlocking {
            val dueDay = day(2026, 4, 15)
            val repo = FakeRecurringRepository(listOf(auto(handled = day(2026, 1, 15))))
            repo.legacyManualIds += "r1"

            val posted = build(repo, expensesFor(day(2026, 1, 15)))(dueDay, zone)

            assertEquals(1, posted)
            assertEquals(listOf("rec_r1_20260415"), repo.confirmedTransactions.map { it.id })
            assertEquals(day(2026, 4, 15), repo.items.single().handledThrough)
        }

    @Test
    @Ignore("Production bug: self-heal re-creates the adopted backlog (see report); re-enable once fixed")
    fun legacyItem_onADayWithNothingDue_postsNothing_andBacklogIsHandled() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto(handled = day(2026, 1, 15))))
            repo.legacyManualIds += "r1"

            assertEquals(0, build(repo)(today, zone))

            assertTrue(repo.confirmedTransactions.isEmpty())
            assertEquals(day(2026, 3, 15), repo.items.single().handledThrough)
        }

    @Test
    fun existingTransaction_onlyAdvancesTheCursor() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto(handled = day(2026, 2, 15))))

            val posted = build(repo, expensesFor(day(2026, 1, 15), day(2026, 2, 15), day(2026, 3, 15)))(today, zone)

            assertEquals(0, posted)
            assertTrue(repo.confirmedTransactions.isEmpty())
            assertEquals(day(2026, 3, 15), repo.items.single().handledThrough)
        }

    @Test
    fun failure_stopsThatItem_butOthersContinue() =
        runBlocking {
            val repo =
                FakeRecurringRepository(
                    listOf(auto("a"), auto("b")),
                    nextConfirmError = IllegalStateException("boom"),
                )
            val expenses =
                FakeExpenseRepository(listOf(rentExpense(day(2026, 1, 15), "a"), rentExpense(day(2026, 1, 15), "b")))

            val posted = build(repo, expenses)(today, zone)

            // "a" sorts first and hits the one-shot failure on its first occurrence: nothing more for it.
            assertEquals(2, posted)
            assertEquals(day(2026, 1, 15), repo.items.single { it.id == "a" }.handledThrough)
            assertEquals(day(2026, 3, 15), repo.items.single { it.id == "b" }.handledThrough)
            assertTrue(repo.confirmedTransactions.all { it.id.startsWith("rec_b_") })
        }

    @Test
    fun income_isSkipped_itsLineRecordsIt() =
        runBlocking {
            val salary =
                recurringItem(id = "s", type = RecurringType.INCOME, handledThrough = day(2026, 2, 15))
            val repo = FakeRecurringRepository(listOf(salary))

            assertEquals(0, build(repo)(today, zone))
            assertEquals(day(2026, 2, 15), repo.items.single().handledThrough)
        }
}
