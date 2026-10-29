package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalAutomation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationPlannerIncomeLinesTest {
    @Test
    fun copiedLineThrough_isFilteredToCopiedLines() {
        val automation = LocalAutomation(lineThrough = mapOf("p1" to "2026-09", "ghost" to "2026-08"))
        val local = localDoc().copy(plannedIncome = listOf(localPlanned("p1")), automation = automation)

        val plan = planMigration(local, remoteSnapshot())

        assertEquals(mapOf("p1" to "2026-09"), plan.automation?.lineThrough)
    }

    @Test
    fun accountWithLines_copiesNoLinesAndNoCursors() {
        val automation = LocalAutomation(lineThrough = mapOf("p1" to "2026-09"))
        val local = localDoc().copy(plannedIncome = listOf(localPlanned("p1")), automation = automation)

        val plan = planMigration(local, remoteSnapshot(plannedIncomeIds = setOf("other")))

        assertTrue(plan.plannedIncome.isEmpty())
        assertTrue(
            plan.automation
                ?.lineThrough
                .orEmpty()
                .isEmpty(),
        )
    }

    @Test
    fun accountAutomation_wins() {
        val local =
            localDoc().copy(
                plannedIncome = listOf(localPlanned("r1")),
                automation = LocalAutomation(lineThrough = mapOf("r1" to "2026-09")),
            )

        val plan = planMigration(local, remoteSnapshot(plannedIncomeIds = setOf("x")).copy(hasAutomation = true))

        assertTrue(plan.automation == null)
    }
}
