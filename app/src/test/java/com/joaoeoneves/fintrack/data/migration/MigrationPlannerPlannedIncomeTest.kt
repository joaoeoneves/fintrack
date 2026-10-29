package com.joaoeoneves.fintrack.data.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationPlannerPlannedIncomeTest {
    private val lines = listOf(localPlanned("p1", order = 0), localPlanned("p2", order = 1))

    @Test
    fun emptyAccount_copiesAllValidLinesWithSameIds() {
        val plan = planMigration(localDoc().copy(plannedIncome = lines), remoteSnapshot())

        assertEquals(lines, plan.plannedIncome)
        assertEquals(2, plan.total)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun accountLineWithTheSameNormalisedLabel_isNotCopiedAndCountsAsSkipped() {
        val remote = remoteSnapshot(plannedIncomeIds = setOf("other"), plannedIncomeLabels = setOf("  SALARY  p1 "))
        val extra = localPlanned("p3", label = "Bonus")

        val plan = planMigration(localDoc().copy(plannedIncome = lines + extra), remote)

        assertEquals(listOf("p2", "p3"), plan.plannedIncome.map { it.id })
        assertEquals(1, plan.skipped)
    }

    @Test
    fun accountLineWithTheSameId_isNotCopiedAndCountsAsSkipped() {
        val plan = planMigration(localDoc().copy(plannedIncome = lines), remoteSnapshot(plannedIncomeIds = setOf("p1")))

        assertEquals(listOf("p2"), plan.plannedIncome.map { it.id })
        assertEquals(1, plan.skipped)
    }

    @Test
    fun invalidLines_areSkippedAndCounted() {
        val local =
            localDoc().copy(
                plannedIncome =
                    listOf(
                        localPlanned("ok"),
                        localPlanned("zero", amountCents = 0),
                        localPlanned("neg", amountCents = -3),
                        localPlanned("blank", label = " "),
                        localPlanned("long", label = "x".repeat(200)),
                    ),
            )

        val plan = planMigration(local, remoteSnapshot())

        // Zero is a valid amount now; only negative, blank and over-long labels are dropped.
        assertEquals(listOf("ok", "zero"), plan.plannedIncome.map { it.id })
        assertEquals(3, plan.skipped)
    }

    @Test
    fun total_includesPlannedIncomeAlongsideOtherRecords() {
        val local = localDoc(expenses = listOf(localExpense("e1"))).copy(plannedIncome = lines)

        assertEquals(3, planMigration(local, remoteSnapshot()).total)
    }

    @Test
    fun replanningAfterCommit_copiesNothing() {
        val remote = remoteSnapshot()
        val local = localDoc().copy(plannedIncome = lines)
        val first = planMigration(local, remote)

        val second = planMigration(local, remote.after(first))

        assertTrue(second.plannedIncome.isEmpty())
        // The lines the account now has are duplicates, so they count as skipped.
        assertEquals(2, second.skipped)
    }

    @Test
    fun plainLine_isCopiedUnchanged() {
        val line = localPlanned("p1")

        val plan = planMigration(localDoc().copy(plannedIncome = listOf(line)), remoteSnapshot())

        assertEquals(listOf(line), plan.plannedIncome)
    }
}
