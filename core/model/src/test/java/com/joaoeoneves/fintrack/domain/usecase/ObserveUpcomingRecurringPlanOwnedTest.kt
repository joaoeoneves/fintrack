package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan-owned savings items are not bills: excluded from the upcoming rows and the annual total. */
class ObserveUpcomingRecurringPlanOwnedTest {
    private val today = day(2026, 3, 15)
    private val rent = recurringItem(id = "rent", amountCents = 1_000, handledThrough = day(2026, 2, 15))
    private val owned = recurringItem(id = "own", amountCents = 9_000, handledThrough = day(2026, 2, 15))

    private val start = day(2026, 1, 1)

    private fun goal(owned: String?) = SavingsGoal("g1", "Plan", 0, GoalTargetType.ANNUAL, start, recurringId = owned)

    private fun overview(goals: List<SavingsGoal>) =
        runBlocking {
            ObserveUpcomingRecurring(FakeRecurringRepository(listOf(rent, owned)), FakeGoalRepository(goals))(today)
                .first()
        }

    @Test
    fun planOwnedItem_isExcludedFromRowsAndTotals_butStillCountsAsAnItem() {
        val result = overview(listOf(goal("own")))

        assertEquals(listOf("rent"), result.upcoming.map { it.item.id })
        assertEquals(12_000L, result.annualExpenseCents)
        assertTrue(result.hasItems)
    }

    @Test
    fun goalWithoutOwnedItem_excludesNothing() {
        val result = overview(listOf(goal(null)))

        assertEquals(setOf("rent", "own"), result.upcoming.map { it.item.id }.toSet())
        assertEquals(120_000L, result.annualExpenseCents)
    }
}
