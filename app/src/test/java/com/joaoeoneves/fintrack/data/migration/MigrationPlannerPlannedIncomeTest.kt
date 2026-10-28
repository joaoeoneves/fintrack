package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalCategoryGroup
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun earmarkedLine(
        id: String,
        type: String?,
        target: String?,
    ) = localPlanned(id).copy(earmarkType = type, earmarkId = target)

    @Test
    fun earmark_group_followsGroupToTheAccountId() {
        val remote = remoteSnapshot(categoryGroups = listOf(Envelope("acct-food", "Food", 0)))
        val local =
            localDoc()
                .copy(
                    categoryGroups = listOf(LocalCategoryGroup("g-food", "food", 0)),
                    plannedIncome = listOf(earmarkedLine("p1", "GROUP", "g-food")),
                )

        val line = planMigration(local, remote).plannedIncome.single()

        assertEquals("GROUP", line.earmarkType)
        assertEquals("acct-food", line.earmarkId)
    }

    @Test
    fun earmark_newGroup_keepsItsId() {
        val local =
            localDoc().copy(
                categoryGroups = listOf(LocalCategoryGroup("g-food", "Food", 0)),
                plannedIncome = listOf(earmarkedLine("p1", "GROUP", "g-food")),
            )

        assertEquals("g-food", planMigration(local, remoteSnapshot()).plannedIncome.single().earmarkId)
    }

    @Test
    fun earmark_customCategory_followsNameCollision() {
        val remote = remoteSnapshot(categories = listOf(userCategory("acct-gym", "Gym")))
        val local =
            localDoc(categories = listOf(localCategory("gym", "gym")))
                .copy(plannedIncome = listOf(earmarkedLine("p1", "CATEGORY", "gym")))

        val line = planMigration(local, remote).plannedIncome.single()

        assertEquals("CATEGORY", line.earmarkType)
        assertEquals("acct-gym", line.earmarkId)
    }

    @Test
    fun earmark_builtInCategory_isKept() {
        val local = localDoc().copy(plannedIncome = listOf(earmarkedLine("p1", "CATEGORY", "FOOD")))

        val line = planMigration(local, remoteSnapshot()).plannedIncome.single()

        assertEquals("FOOD", line.earmarkId)
    }

    @Test
    fun earmark_unresolvable_isDropped_lineKept_andNotCountedSkipped() {
        val local =
            localDoc().copy(
                plannedIncome =
                    listOf(
                        earmarkedLine("p1", "GROUP", "missing-group"),
                        earmarkedLine("p2", "CATEGORY", "missing-cat"),
                        earmarkedLine("p3", "WALLET", "x"),
                        earmarkedLine("p4", "GROUP", null),
                    ),
            )

        val plan = planMigration(local, remoteSnapshot())

        assertEquals(listOf("p1", "p2", "p3", "p4"), plan.plannedIncome.map { it.id })
        plan.plannedIncome.forEach {
            assertNull(it.earmarkType)
            assertNull(it.earmarkId)
        }
        assertEquals(0, plan.skipped)
    }

    @Test
    fun earmark_unearmarkedLine_unchanged() {
        val line = localPlanned("p1")

        val plan = planMigration(localDoc().copy(plannedIncome = listOf(line)), remoteSnapshot())

        assertEquals(listOf(line), plan.plannedIncome)
    }
}
