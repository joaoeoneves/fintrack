package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import com.joaoeoneves.fintrack.data.local.LocalPlannedIncome
import com.joaoeoneves.fintrack.data.local.LocalSalaryProfile
import com.joaoeoneves.fintrack.data.local.LocalSavingsGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PendingWriteGoalsTest {
    private fun plan(
        goals: List<LocalSavingsGoal> = emptyList(),
        plannedIncome: List<LocalPlannedIncome> = emptyList(),
        profile: LocalSalaryProfile? = null,
    ) = MigrationPlan(
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        plannedIncome,
        emptyList(),
        0,
        salaryProfile = profile,
        goals = goals,
    )

    @Test
    fun goals_areCommittedLast_afterEverythingElse() {
        val batches =
            plan(
                goals = listOf(localGoal("g1")),
                plannedIncome = listOf(localPlanned("p1")),
                profile = LocalSalaryProfile(baseGrossCents = 100_000),
            ).toBatches()

        assertEquals(listOf("plannedIncome", "plan", "goals"), batches.map { it.first().collection })
    }

    @Test
    fun goalWrite_usesTheGoalIdAndFirestoreFields() {
        val write =
            plan(goals = listOf(localGoal("g1", "FOOD", listOf("r2", "r1")).copy(initialCents = 700)))
                .toBatches()
                .single()
                .single()

        assertEquals("goals", write.collection)
        assertEquals("g1", write.id)
        assertEquals("Goal g1", write.data["name"])
        assertEquals(120_000L, write.data["targetCents"])
        assertEquals(700L, write.data["initialCents"])
        assertEquals("ANNUAL", write.data["targetType"])
        assertEquals("2026-01-01", write.data["startDate"])
        assertNull(write.data["targetDate"])
        assertEquals("FOOD", write.data["linkedCategoryId"])
        assertEquals(listOf("r1", "r2"), write.data["linkedRecurringIds"])
        assertEquals(false, write.data["archived"])
    }

    @Test
    fun noGoals_noGoalWrites() {
        val batches = plan(plannedIncome = listOf(localPlanned("p1"))).toBatches()

        assertTrue(batches.none { it.first().collection == "goals" })
    }

    @Test
    fun manyGoals_areChunkedAtFourHundred() {
        val goals = (1..401).map { localGoal("g$it") }

        val batches = plan(goals = goals).toBatches()

        assertEquals(listOf(400, 1), batches.map { it.size })
    }

    @Test
    fun unmappableGoal_throws_insteadOfBeingDropped() {
        val broken = localGoal("g1").copy(targetType = "WEEKLY")

        assertThrows(IllegalStateException::class.java) { plan(goals = listOf(broken)).toBatches() }
    }
}
