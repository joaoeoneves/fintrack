package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationPlannerCategoriesTest {
    @Test
    fun emptyLocal_givesEmptyPlan() {
        val plan = planMigration(localDoc(), remoteSnapshot())

        assertEquals(emptyPlan(skipped = 0), plan)
        assertEquals(0, plan.total)
    }

    @Test
    fun newCustomCategories_keepIdAndGetOrdersAfterAccountMax() {
        val local = localDoc(categories = listOf(localCategory("a", order = 0), localCategory("b", order = 0)))
        val remote = remoteSnapshot(categories = listOf(userCategory("r", "Travel", order = 20)))

        val plan = planMigration(local, remote)

        assertEquals(listOf("a", "b"), plan.categories.map { it.id })
        assertEquals(listOf(21, 22), plan.categories.map { it.order })
    }

    @Test
    fun emptyAccount_ordersStartAfterBuiltIns() {
        val local = localDoc(categories = listOf(localCategory("a", order = 99)))

        val plan = planMigration(local, remoteSnapshot())

        assertEquals(BUILT_IN_MAX_ORDER + 1, plan.categories.single().order)
    }

    @Test
    fun accountMax_ignoresIntMaxValueOrders() {
        val local = localDoc(categories = listOf(localCategory("a")))
        val remote = remoteSnapshot(categories = listOf(userCategory("r", "R", order = Int.MAX_VALUE)))

        val plan = planMigration(local, remote)

        assertEquals(BUILT_IN_MAX_ORDER + 1, plan.categories.single().order)
    }

    @Test
    fun localIdAlreadyInAccount_isUsedAsIsAndNotWritten() {
        val local =
            localDoc(
                categories = listOf(localCategory("gym", "Gym")),
                expenses = listOf(localExpense("e1", "gym")),
            )
        val remote = remoteSnapshot(categories = listOf(userCategory("gym", "Totally different")))

        val plan = planMigration(local, remote)

        assertTrue(plan.categories.isEmpty())
        assertEquals("gym", plan.expenses.single().categoryId)
    }

    @Test
    fun sameNameCaseInsensitiveTrimmed_remapsToAccountIdWithoutWriting() {
        val local =
            localDoc(
                categories = listOf(localCategory("local-gym", "  GYM ")),
                expenses = listOf(localExpense("e1", "local-gym")),
                recurring = listOf(localRecurring("r1", "local-gym")),
            )
        val remote = remoteSnapshot(categories = listOf(userCategory("acct-gym", "gym")))

        val plan = planMigration(local, remote)

        assertTrue(plan.categories.isEmpty())
        assertEquals("acct-gym", plan.expenses.single().categoryId)
        assertEquals("acct-gym", plan.recurring.single().categoryId)
    }

    @Test
    fun twoLocalCategoriesWithSameName_secondMergesIntoFirst() {
        val local =
            localDoc(
                categories = listOf(localCategory("a", "Gym"), localCategory("b", "gym")),
                expenses = listOf(localExpense("e1", "a"), localExpense("e2", "b")),
            )

        val plan = planMigration(local, remoteSnapshot())

        assertEquals(listOf("a"), plan.categories.map { it.id })
        assertEquals(listOf("a", "a"), plan.expenses.map { it.categoryId })
    }

    @Test
    fun builtInOverride_accountOverrideWinsElseLocalIsCopied() {
        val local =
            localDoc(
                categories =
                    listOf(
                        localCategory("FOOD", "Meals"),
                        localCategory("SHOPPING", "Stuff"),
                    ),
                expenses = listOf(localExpense("e1", "FOOD")),
            )
        val remote = remoteSnapshot(categories = listOf(userCategory("FOOD", "Eating out")))

        val plan = planMigration(local, remote)

        assertEquals(listOf("SHOPPING"), plan.categories.map { it.id })
        assertEquals("FOOD", plan.expenses.single().categoryId)
    }

    @Test
    fun orphanReferences_getArchivedPlaceholderAndRecordsAreKept() {
        val local =
            localDoc(
                expenses = listOf(localExpense("e1", "deleted-cat")),
                recurring = listOf(localRecurring("r1", "deleted-cat")),
            )

        val plan = planMigration(local, remoteSnapshot())

        val placeholder = plan.categories.single()
        assertEquals("deleted-cat", placeholder.id)
        assertTrue(placeholder.archived)
        assertEquals(1, plan.expenses.size)
        assertEquals(1, plan.recurring.size)
        assertEquals(0, plan.skipped)
        assertEquals(3, plan.total)
    }

    @Test
    fun orphanPlaceholder_isNamedWithTheGivenDeletedCategoryName() {
        val local = localDoc(expenses = listOf(localExpense("e1", "deleted-cat")))

        val plan = planMigration(local, remoteSnapshot(), "Categoria apagada")

        assertEquals("Categoria apagada", plan.categories.single().name)
    }

    @Test
    fun invalidCustomCategory_isSkippedAndItsRecordsWithIt() {
        val local =
            localDoc(
                categories = listOf(localCategory("bad", name = "")),
                expenses = listOf(localExpense("e1", "bad"), localExpense("e2", "FOOD")),
                recurring = listOf(localRecurring("r1", "bad")),
            )

        val plan = planMigration(local, remoteSnapshot())

        assertTrue(plan.categories.isEmpty())
        assertEquals(listOf("e2"), plan.expenses.map { it.id })
        assertTrue(plan.recurring.isEmpty())
        assertEquals(3, plan.skipped)
    }

    @Test
    fun whitespaceNamedCategory_isSkippedWithItsRecords() {
        val local =
            localDoc(
                categories = listOf(localCategory("ws", name = "   ")),
                expenses = listOf(localExpense("e1", "ws")),
                recurring = listOf(localRecurring("r1", "ws")),
            )

        val plan = planMigration(local, remoteSnapshot())

        assertEquals(emptyPlan(skipped = 3), plan)
    }

    private fun emptyPlan(skipped: Int): MigrationPlan {
        val none = emptyList<Nothing>()
        return MigrationPlan(none, none, none, none, none, skipped)
    }
}
