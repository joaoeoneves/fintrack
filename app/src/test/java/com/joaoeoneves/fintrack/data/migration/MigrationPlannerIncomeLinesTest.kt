package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalAutomation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationPlannerIncomeLinesTest {
    @Test
    fun rincLinesMissingFromTheAccount_areCopiedEvenWhenItHasLines() {
        val local =
            localDoc().copy(
                plannedIncome = listOf(localPlanned("p1"), localPlanned("rinc_r1"), localPlanned("rinc_r2")),
            )
        val remote = remoteSnapshot(plannedIncomeIds = setOf("other", "rinc_r2"))

        val plan = planMigration(local, remote)

        assertEquals(listOf("rinc_r1"), plan.plannedIncome.map { it.id })
    }

    @Test
    fun copiedLineThrough_isFilteredToCopiedLines() {
        val automation =
            LocalAutomation(
                lineThrough = mapOf("rinc_r1" to "2026-09", "p1" to "2026-09", "rinc_r2" to "2026-08"),
            )
        val local =
            localDoc().copy(
                plannedIncome = listOf(localPlanned("p1"), localPlanned("rinc_r1"), localPlanned("rinc_r2")),
                automation = automation,
            )
        val remote = remoteSnapshot(plannedIncomeIds = setOf("other", "rinc_r2"))

        val plan = planMigration(local, remote)

        assertEquals(mapOf("rinc_r1" to "2026-09"), plan.automation?.lineThrough)
    }

    @Test
    fun accountAutomation_winsEvenForRincLines() {
        val local =
            localDoc().copy(
                plannedIncome = listOf(localPlanned("rinc_r1")),
                automation = LocalAutomation(lineThrough = mapOf("rinc_r1" to "2026-09")),
            )

        val plan = planMigration(local, remoteSnapshot(plannedIncomeIds = setOf("x")).copy(hasAutomation = true))

        assertTrue(plan.automation == null)
    }
}
