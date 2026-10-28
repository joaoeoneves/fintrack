package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationPlannerGoalsTest {
    @Test
    fun emptyAccount_copiesGoalsWithSameIds_andCountsThem() {
        val goals = listOf(localGoal("g1", recurringIds = listOf("r1")), localGoal("g2"))

        val plan = planMigration(localDoc().copy(goals = goals), remoteSnapshot())

        assertEquals(goals, plan.goals)
        assertEquals(2, plan.total)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun idAlreadyInAccount_isSkippedSilently() {
        val local = localDoc().copy(goals = listOf(localGoal("g1"), localGoal("g2")))

        val plan = planMigration(local, remoteSnapshot().copy(goalIds = setOf("g1")))

        assertEquals(listOf("g2"), plan.goals.map { it.id })
        assertEquals(0, plan.skipped)
    }

    @Test
    fun builtInCategoryLink_isKept() {
        val plan = planMigration(localDoc().copy(goals = listOf(localGoal("g1", "INVESTMENTS"))), remoteSnapshot())

        assertEquals("INVESTMENTS", plan.goals.single().linkedCategoryId)
    }

    @Test
    fun customCategoryNameCollision_remapsToAccountCategory() {
        val local =
            localDoc(categories = listOf(localCategory("gym", "Gym"))).copy(goals = listOf(localGoal("g1", "gym")))
        val remote = remoteSnapshot(categories = listOf(userCategory("acct-gym", "gym")))

        val plan = planMigration(local, remote)

        assertEquals("acct-gym", plan.goals.single().linkedCategoryId)
        assertTrue(plan.categories.isEmpty())
    }

    @Test
    fun customCategoryCopiedAcross_keepsItsLink() {
        val local =
            localDoc(categories = listOf(localCategory("gym", "Gym"))).copy(goals = listOf(localGoal("g1", "gym")))

        val plan = planMigration(local, remoteSnapshot())

        assertEquals(1, plan.categories.size)
        assertEquals(plan.categories.single().id, plan.goals.single().linkedCategoryId)
    }

    @Test
    fun unresolvableCategory_isDropped_butTheGoalStays() {
        val local = localDoc().copy(goals = listOf(localGoal("g1", "ghost", recurringIds = listOf("r1"))))

        val plan = planMigration(local, remoteSnapshot())

        val goal = plan.goals.single()
        assertNull(goal.linkedCategoryId)
        assertEquals(listOf("r1"), goal.linkedRecurringIds)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun invalidGoals_areSkippedAndCounted_idsInAccountNotCounted() {
        val bad = localGoal("bad").copy(targetCents = -1)
        val reversed = localGoal("rev").copy(targetDate = "2025-01-01")
        val haveBad = localGoal("have").copy(targetCents = -1)
        val local = localDoc().copy(goals = listOf(localGoal("ok"), bad, reversed, haveBad))

        val plan = planMigration(local, remoteSnapshot().copy(goalIds = setOf("have")))

        assertEquals(listOf("ok"), plan.goals.map { it.id })
        assertEquals(2, plan.skipped)
    }

    @Test
    fun noGoals_leavesPlanUntouched() {
        val plan = planMigration(localDoc(), remoteSnapshot())

        assertTrue(plan.goals.isEmpty())
        assertEquals(0, plan.total)
    }

    @Test
    fun afterCommit_replanningCopiesNothing() {
        val local = localDoc().copy(goals = listOf(localGoal("g1"), localGoal("g2")))
        val remote = remoteSnapshot()

        val second = planMigration(local, remote.after(planMigration(local, remote)))

        assertTrue(second.goals.isEmpty())
        assertEquals(0, second.total)
    }
}
