package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.occurrenceTransactionId
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Shared fixtures for the [AutoPostRecurring] test classes: monthly rent on the 15th, "today" is 2026-03-20. */
abstract class AutoPostRecurringTestBase {
    protected val zone: ZoneId = ZoneId.of("UTC")
    protected val today: LocalDate = day(2026, 3, 20)

    protected fun auto(
        id: String = "r1",
        handled: LocalDate? = day(2026, 1, 15),
        skipped: Set<LocalDate> = emptySet(),
    ) = recurringItem(id = id, handledThrough = handled, autoPost = true, skipped = skipped)

    /** The expense an auto-post pass would have created for [date] of item [itemId]. */
    protected fun rentExpense(
        date: LocalDate,
        itemId: String = "r1",
        id: String = occurrenceTransactionId(itemId, date),
    ) = Expense(
        id = id,
        name = "Rent",
        amountCents = 50_000L,
        category = BuiltInCategories.FOOD,
        date = Instant.parse("${date}T12:00:00Z"),
        note = null,
    )

    /** An expense repository already holding the transactions for [dates] of item "r1". */
    protected fun expensesFor(vararg dates: LocalDate) = FakeExpenseRepository(dates.map { rentExpense(it) })

    protected fun build(
        repo: FakeRecurringRepository,
        expenses: FakeExpenseRepository = expensesFor(day(2026, 1, 15)),
    ) = AutoPostRecurring(
        repo,
        HandleRecurringOccurrence(repo, FakeCategoryRepository()),
        expenses,
        FakeCategoryRepository(),
    )
}
