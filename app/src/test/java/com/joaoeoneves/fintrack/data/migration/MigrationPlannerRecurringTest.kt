package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationPlannerRecurringTest {
    private val items = listOf(localRecurring("r1"), localRecurring("r2", categoryId = null, type = "INCOME"))

    @Test
    fun emptyAccount_copiesAllValidItemsWithSameIds() {
        val plan = planMigration(localDoc(recurring = items), remoteSnapshot())

        assertEquals(items, plan.recurring)
        assertEquals(2, plan.total)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun idAlreadyInAccount_isLeftOutSilently() {
        val plan = planMigration(localDoc(recurring = items), remoteSnapshot(recurringIds = setOf("r1")))

        assertEquals(listOf("r2"), plan.recurring.map { it.id })
        assertEquals(0, plan.skipped)
    }

    @Test
    fun customCategoryNameCollision_remapsToAccountCategory() {
        val local =
            localDoc(categories = listOf(localCategory("gym", "Gym")), recurring = listOf(localRecurring("r1", "gym")))
        val remote = remoteSnapshot(categories = listOf(userCategory("acct-gym", "gym")))

        val plan = planMigration(local, remote)

        assertEquals("acct-gym", plan.recurring.single().categoryId)
        assertTrue(plan.categories.isEmpty())
    }

    @Test
    fun deletedCategory_getsPlaceholderAndItemSurvives() {
        val plan = planMigration(localDoc(recurring = listOf(localRecurring("r1", "gone"))), remoteSnapshot())

        assertEquals(listOf("gone"), plan.categories.map { it.id })
        assertEquals("gone", plan.recurring.single().categoryId)
        assertEquals(2, plan.total)
    }

    @Test
    fun unresolvableCategory_isSkippedAndCounted() {
        val local =
            localDoc(
                categories = listOf(localCategory("bad", name = "")),
                recurring = listOf(localRecurring("r1", "bad")),
            )

        val plan = planMigration(local, remoteSnapshot())

        // The invalid category itself counts as skipped too.
        assertTrue(plan.recurring.isEmpty())
        assertEquals(2, plan.skipped)
    }

    @Test
    fun invalidItems_areSkippedAndCounted() {
        val local = localDoc(recurring = listOf(localRecurring("ok"), localRecurring("zero", amountCents = 0)))

        val plan = planMigration(local, remoteSnapshot())

        assertEquals(listOf("ok"), plan.recurring.map { it.id })
        assertEquals(1, plan.skipped)
    }

    @Test
    fun replanningAfterFullWrite_isEmpty() {
        val remote = remoteSnapshot()
        val local = localDoc(recurring = items)
        val first = planMigration(local, remote)

        val second = planMigration(local, remote.after(first))

        assertTrue(second.recurring.isEmpty())
        assertEquals(0, second.total)
        assertEquals(0, second.skipped)
    }

    @Test
    fun total_includesRecurringAlongsideOtherRecords() {
        val local = localDoc(expenses = listOf(localExpense("e1")), recurring = items)

        assertEquals(3, planMigration(local, remoteSnapshot()).total)
    }
}
