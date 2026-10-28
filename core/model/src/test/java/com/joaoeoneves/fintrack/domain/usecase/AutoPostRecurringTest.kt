package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.occurrenceTransactionId
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** [AutoPostRecurring]: posts + advances, only advances when the transaction exists, per-item failure isolation. */
class AutoPostRecurringTest {
    private val zone = ZoneId.of("UTC")
    private val today = day(2026, 3, 20)

    private fun auto(
        id: String = "r1",
        handled: LocalDate? = day(2026, 1, 15),
    ) = recurringItem(id = id, handledThrough = handled, autoPost = true)

    private fun build(
        repo: FakeRecurringRepository,
        expenses: FakeExpenseRepository = FakeExpenseRepository(),
    ) = AutoPostRecurring(
        repo,
        HandleRecurringOccurrence(repo, FakeCategoryRepository()),
        expenses,
    )

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
            val useCase = build(repo)
            useCase(today, zone)

            assertEquals(0, useCase(today, zone))
            assertEquals(2, repo.confirmedTransactions.size)
        }

    @Test
    fun manualItems_areIgnored() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(recurringItem(handledThrough = day(2026, 1, 15))))

            assertEquals(0, build(repo)(today, zone))
            assertTrue(repo.confirmedTransactions.isEmpty())
        }

    @Test
    fun existingTransaction_onlyAdvancesTheCursor() =
        runBlocking {
            val repo = FakeRecurringRepository(listOf(auto(handled = day(2026, 2, 15))))
            val existing =
                Expense(
                    id = occurrenceTransactionId("r1", day(2026, 3, 15)),
                    name = "Rent",
                    amountCents = 50_000L,
                    category = BuiltInCategories.FOOD,
                    date = Instant.parse("2026-03-15T12:00:00Z"),
                    note = null,
                )

            val posted = build(repo, FakeExpenseRepository(listOf(existing)))(today, zone)

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

            val posted = build(repo)(today, zone)

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
                recurringItem(id = "s", type = RecurringType.INCOME, handledThrough = day(2026, 2, 15), autoPost = true)
            val repo = FakeRecurringRepository(listOf(salary))

            assertEquals(0, build(repo)(today, zone))
            assertEquals(day(2026, 2, 15), repo.items.single().handledThrough)
        }
}
