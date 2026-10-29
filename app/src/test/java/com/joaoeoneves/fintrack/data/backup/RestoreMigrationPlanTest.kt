package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalAutomation
import com.joaoeoneves.fintrack.data.local.LocalBudget
import com.joaoeoneves.fintrack.data.local.LocalCategoryGroup
import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.data.local.LocalSalaryProfile
import com.joaoeoneves.fintrack.data.local.LocalSavingsGoal
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreMigrationPlanTest {
    private fun group(
        id: String,
        order: Int = 0,
        fundedBy: String? = null,
    ) = LocalCategoryGroup(id, id.uppercase(), order, fundedByLineId = fundedBy)

    private fun goal(
        id: String = "g1",
        linkedCategoryId: String? = null,
    ) = LocalSavingsGoal(id, "Trip", 100_000, startDate = "2026-01-01", linkedCategoryId = linkedCategoryId)

    @Test
    fun sameNameCategories_areNotMerged_andIdsAndOrderKept() {
        val local =
            LocalDataDocument(
                categories =
                    listOf(
                        backupCategory("a", "Gym", order = 7),
                        backupCategory("b", "Gym", order = 3),
                        backupCategory("c", "gym", order = 11),
                    ),
            )

        val plan = restoreMigrationPlanOf(local)

        assertEquals(listOf("a", "b", "c"), plan.categories.map { it.id })
        assertEquals(listOf(7, 3, 11), plan.categories.map { it.order })
        assertEquals(0, plan.skipped)
    }

    @Test
    fun recordOrder_isPreserved() {
        val local =
            LocalDataDocument(
                expenses = listOf(backupExpense("z"), backupExpense("a"), backupExpense("m")),
                plannedIncome = listOf(backupPlanned("p2", order = 5), backupPlanned("p1", order = 2)),
                categoryGroups = listOf(group("g2", 4), group("g1", 1)),
            )

        val plan = restoreMigrationPlanOf(local)

        assertEquals(listOf("z", "a", "m"), plan.expenses.map { it.id })
        assertEquals(listOf("p2", "p1"), plan.plannedIncome.map { it.id })
        assertEquals(listOf(5, 2), plan.plannedIncome.map { it.order })
        assertEquals(listOf("g2", "g1"), plan.categoryGroups.map { it.id })
    }

    @Test
    fun categoryInAMissingGroup_losesTheGroupLink_keepsTheCategory() {
        val local =
            LocalDataDocument(
                categories =
                    listOf(
                        backupCategory("a").copy(groupId = "gone"),
                        backupCategory("b").copy(groupId = "g1"),
                    ),
                categoryGroups = listOf(group("g1")),
            )

        val plan = restoreMigrationPlanOf(local)

        assertNull(plan.categories.first { it.id == "a" }.groupId)
        assertEquals("g1", plan.categories.first { it.id == "b" }.groupId)
    }

    @Test
    fun groupFundedByAMissingLine_isClearedNotDropped() {
        val local =
            LocalDataDocument(
                plannedIncome = listOf(backupPlanned("p1")),
                categoryGroups = listOf(group("g1", fundedBy = "ghost"), group("g2", fundedBy = "p1")),
            )

        val plan = restoreMigrationPlanOf(local)

        assertNull(plan.categoryGroups.first { it.id == "g1" }.fundedByLineId)
        assertEquals("p1", plan.categoryGroups.first { it.id == "g2" }.fundedByLineId)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun orphanPlaceholder_isNamedWithTheGivenDeletedCategoryName() {
        val local = LocalDataDocument(budgets = listOf(LocalBudget("ghost", 100)))

        val plan = restoreMigrationPlanOf(local, "Categoria apagada")

        assertEquals("Categoria apagada", plan.categories.single().name)
    }

    @Test
    fun goalLinkedToAMissingCategory_isClearedNotDropped() {
        val local =
            LocalDataDocument(
                goals = listOf(goal("g1", "ghost"), goal("g2", "gym"), goal("g3", "FOOD")),
                categories = listOf(backupCategory("gym")),
            )

        val goals = restoreMigrationPlanOf(local).goals.associateBy { it.id }

        assertNull(goals.getValue("g1").linkedCategoryId)
        assertEquals("gym", goals.getValue("g2").linkedCategoryId)
        assertEquals("FOOD", goals.getValue("g3").linkedCategoryId)
    }

    @Test
    fun automationCursorForAMissingLine_isDropped() {
        val local =
            LocalDataDocument(
                plannedIncome = listOf(backupPlanned("p1")),
                automation =
                    LocalAutomation(
                        lineThrough = mapOf("p1" to "2026-03", "ghost" to "2026-02"),
                    ),
            )

        val automation = restoreMigrationPlanOf(local).automation

        assertNotNull(automation)
        assertEquals(mapOf("p1" to "2026-03"), automation?.lineThrough)
    }

    @Test
    fun managedKey_isNulledWithoutAProfile_keptWithOne() {
        val managed = backupPlanned("m").copy(managedKey = MANAGED_NET_SALARY)
        val doc = LocalDataDocument(plannedIncome = listOf(managed))

        val without = restoreMigrationPlanOf(doc).plannedIncome.single()
        val with =
            restoreMigrationPlanOf(doc.copy(salaryProfile = LocalSalaryProfile(baseGrossCents = 100_000))).let {
                it.plannedIncome.single()
            }

        assertNull(without.managedKey)
        assertEquals(MANAGED_NET_SALARY, with.managedKey)
    }

    @Test
    fun expenseOfAMissingCategory_getsAnArchivedPlaceholder_notDropped() {
        val local = LocalDataDocument(expenses = listOf(backupExpense("e1", categoryId = "deleted")))

        val plan = restoreMigrationPlanOf(local, placeholderName = "Categoria apagada")

        assertEquals(listOf("e1"), plan.expenses.map { it.id })
        val placeholder = plan.categories.single { it.id == "deleted" }
        assertEquals("Categoria apagada", placeholder.name)
        assertTrue(placeholder.archived)
    }

    @Test
    fun invalidRecords_areDroppedAndCounted() {
        val local =
            LocalDataDocument(
                expenses = listOf(backupExpense("ok"), backupExpense("zero", amountCents = 0)),
                income = listOf(backupIncome("i-ok"), backupIncome("i-bad", amountCents = -5)),
                budgets = listOf(LocalBudget("FOOD", 100), LocalBudget("FOOD", 200), LocalBudget("FOOD", -1)),
            )

        val plan = restoreMigrationPlanOf(local)

        assertEquals(listOf("ok"), plan.expenses.map { it.id })
        assertEquals(listOf("i-ok"), plan.income.map { it.id })
        assertEquals(listOf(100L), plan.budgets.map { it.limitCents })
        assertEquals(4, plan.skipped)
    }

    @Test
    fun duplicateIds_keepTheFirst() {
        val local =
            LocalDataDocument(
                expenses = listOf(backupExpense("e1", amountCents = 111), backupExpense("e1", amountCents = 222)),
            )

        val plan = restoreMigrationPlanOf(local)

        assertEquals(listOf(111L), plan.expenses.map { it.amountCents })
        assertEquals(1, plan.skipped)
    }

    @Test
    fun emptyDocument_plansNothing() {
        val plan = restoreMigrationPlanOf(LocalDataDocument())

        assertEquals(0, plan.total)
        assertEquals(0, plan.skipped)
        assertNull(plan.salaryProfile)
        assertNull(plan.automation)
    }
}
