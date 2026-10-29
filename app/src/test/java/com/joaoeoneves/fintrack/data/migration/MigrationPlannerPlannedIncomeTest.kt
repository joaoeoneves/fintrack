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
    fun accountWithAnyLine_copiesNoneAndSkipsNone() {
        val remote = remoteSnapshot(plannedIncomeIds = setOf("other"))

        val plan = planMigration(localDoc().copy(plannedIncome = lines + localPlanned("bad", amountCents = 0)), remote)

        assertTrue(plan.plannedIncome.isEmpty())
        assertEquals(0, plan.skipped)
        assertEquals(0, plan.total)
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

        assertEquals(listOf("ok"), plan.plannedIncome.map { it.id })
        assertEquals(4, plan.skipped)
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
        assertEquals(0, second.skipped)
    }

    @Test
    fun plainLine_isCopiedUnchanged() {
        val line = localPlanned("p1")

        val plan = planMigration(localDoc().copy(plannedIncome = listOf(line)), remoteSnapshot())

        assertEquals(listOf(line), plan.plannedIncome)
    }
}
