package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecurringBillsTest {
    private fun plan(
        id: String,
        recurringId: String?,
    ) = SavingsGoal(id, id, 0, GoalTargetType.ANNUAL, LocalDate.of(2026, 1, 1), recurringId = recurringId)

    @Test
    fun billsOf_keepsOnlyExpenseItems() {
        val items =
            listOf(
                recurringItem("a"),
                recurringItem("inc", type = RecurringType.INCOME, amountCents = 1L),
            )

        assertEquals(listOf("a"), billsOf(items, emptyList()).map { it.id })
    }

    @Test
    fun billsOf_excludesSavingsPlanOwnedItems_butNotMerelyLinkedOnes() {
        val items = listOf(recurringItem("bill"), recurringItem("own"), recurringItem("linked"))
        val goals = listOf(plan("p1", "own"), plan("p2", null).copy(linkedRecurringIds = setOf("linked")))

        assertEquals(listOf("bill", "linked"), billsOf(items, goals).map { it.id })
    }

    @Test
    fun billsOf_empty_isEmpty() {
        assertEquals(emptyList<RecurringItem>(), billsOf(emptyList(), emptyList()))
    }

    @Test
    fun monthlyEquivalent_roundsToNearest() {
        assertEquals(0L, monthlyEquivalentCents(0L))
        assertEquals(1_000L, monthlyEquivalentCents(12_000L))
        assertEquals(8L, monthlyEquivalentCents(100L))
        assertEquals(9L, monthlyEquivalentCents(103L))
        // Exactly half rounds up.
        assertEquals(1L, monthlyEquivalentCents(6L))
        assertEquals(0L, monthlyEquivalentCents(5L))
    }
}
