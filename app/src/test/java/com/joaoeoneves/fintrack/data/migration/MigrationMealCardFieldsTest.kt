package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import com.joaoeoneves.fintrack.data.local.LocalExpense
import com.joaoeoneves.fintrack.data.local.LocalSalaryProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The guest to account migration carries the 5.2 fields: planned (MigrationPlanner) and written (PendingWrite). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MigrationMealCardFieldsTest {
    private val profile =
        LocalSalaryProfile(
            baseGrossCents = 100_000,
            sector = "PUBLIC",
            mealPayment = "WITH_SALARY",
            otherDeductionsPpm = 35_000,
        )
    private val line = localPlanned("p1").copy(payDay = 1, payDayEarlierIfClosed = true)
    private val expense = LocalExpense("e1", "Lunch", 500, "FOOD", "2026-10-01T12:00:00Z", paidWithMealCard = true)

    private fun writes(plan: MigrationPlan) = plan.toBatches().flatten().associateBy { it.collection }

    @Test
    fun planner_keepsTheFieldsOnProfileLineAndExpense() {
        val local = localDoc(expenses = listOf(expense)).copy(salaryProfile = profile, plannedIncome = listOf(line))

        val plan = planMigration(local, remoteSnapshot())

        assertEquals(profile, plan.salaryProfile)
        assertEquals(true, plan.plannedIncome.single().payDayEarlierIfClosed)
        assertEquals(true, plan.expenses.single().paidWithMealCard)
    }

    @Test
    fun planner_keepsTheCategoryFlag() {
        val category = localCategory("c1").copy(mealCardByDefault = true)

        val plan = planMigration(localDoc(categories = listOf(category)), remoteSnapshot())

        assertEquals(true, plan.categories.single().mealCardByDefault)
    }

    @Test
    fun pendingWrite_writesNonDefaultsOnly_forProfileAndLine() {
        val local = localDoc().copy(salaryProfile = profile, plannedIncome = listOf(line))
        val plan = planMigration(local, remoteSnapshot())
        val byCollection = writes(plan)

        val plan1 = byCollection.getValue("plan").data
        assertEquals("PUBLIC", plan1["sector"])
        assertEquals("WITH_SALARY", plan1["mealPayment"])
        assertEquals(35_000, plan1["otherDeductionsPpm"])
        assertEquals(true, byCollection.getValue("plannedIncome").data["payDayEarlierIfClosed"])
    }

    @Test
    fun pendingWrite_defaults_areAbsent() {
        val local =
            localDoc().copy(
                salaryProfile = LocalSalaryProfile(baseGrossCents = 1),
                plannedIncome = listOf(localPlanned("p1")),
            )
        val plan = planMigration(local, remoteSnapshot())
        val byCollection = writes(plan)

        val data = byCollection.getValue("plan").data
        assertFalse(data.containsKey("sector"))
        assertFalse(data.containsKey("mealPayment"))
        assertFalse(data.containsKey("otherDeductionsPpm"))
        assertFalse(byCollection.getValue("plannedIncome").data.containsKey("payDayEarlierIfClosed"))
    }

    @Test
    fun pendingWrite_expenseAndCategoryFlags_areAlwaysWritten() {
        val category = localCategory("c1").copy(mealCardByDefault = true)
        val plan = planMigration(localDoc(expenses = listOf(expense), categories = listOf(category)), remoteSnapshot())
        val byCollection = writes(plan)

        assertEquals(true, byCollection.getValue("expenses").data["paidWithMealCard"])
        assertEquals(true, byCollection.getValue("categories").data["mealCardByDefault"])
        val plain = planMigration(localDoc(expenses = listOf(expense.copy(paidWithMealCard = false))), remoteSnapshot())
        assertEquals(false, writes(plain).getValue("expenses").data["paidWithMealCard"])
    }
}
