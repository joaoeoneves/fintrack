package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalBudget
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationPlannerRecordsTest {
    @Test
    fun expensesAndIncome_keepIdsAndCountInTotal() {
        val local =
            localDoc(
                expenses = listOf(localExpense("e1"), localExpense("e2")),
                income = listOf(localIncome("i1")),
            )

        val plan = planMigration(local, remoteSnapshot())

        assertEquals(listOf("e1", "e2"), plan.expenses.map { it.id })
        assertEquals(listOf("i1"), plan.income.map { it.id })
        assertEquals(3, plan.total)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun recordsAlreadyInAccount_areLeftOutAndNotCountedAsSkipped() {
        val local =
            localDoc(
                expenses = listOf(localExpense("e1"), localExpense("e2")),
                income = listOf(localIncome("i1"), localIncome("i2")),
            )
        val remote = remoteSnapshot(expenseIds = setOf("e1"), incomeIds = setOf("i2"))

        val plan = planMigration(local, remote)

        assertEquals(listOf("e2"), plan.expenses.map { it.id })
        assertEquals(listOf("i1"), plan.income.map { it.id })
        assertEquals(0, plan.skipped)
    }

    @Test
    fun invalidRecords_zeroNegativeAmountAndBadDate_areSkipped() {
        val local =
            localDoc(
                expenses =
                    listOf(
                        localExpense("zero", amountCents = 0),
                        localExpense("neg", amountCents = -5),
                        localExpense("ok", amountCents = 1),
                        localExpense("bad-date").copy(date = "yesterday"),
                    ),
                income = listOf(localIncome("i-zero", amountCents = 0), localIncome("i-ok")),
            )

        val plan = planMigration(local, remoteSnapshot())

        assertEquals(listOf("ok"), plan.expenses.map { it.id })
        assertEquals(listOf("i-ok"), plan.income.map { it.id })
        assertEquals(4, plan.skipped)
    }

    @Test
    fun budgets_accountBudgetWinsSilently() {
        val local = localDoc(budgets = listOf(LocalBudget("FOOD", 100), LocalBudget("SHOPPING", 200)))
        val remote = remoteSnapshot(budgetCategoryIds = setOf("FOOD"))

        val plan = planMigration(local, remote)

        assertEquals(listOf(LocalBudget("SHOPPING", 200)), plan.budgets)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun budgets_duplicateTargetsKeepFirst() {
        val local =
            localDoc(
                categories = listOf(localCategory("a", "Gym"), localCategory("b", "Gym")),
                budgets = listOf(LocalBudget("a", 100), LocalBudget("b", 999)),
            )

        val plan = planMigration(local, remoteSnapshot())

        assertEquals(listOf(LocalBudget("a", 100)), plan.budgets)
        // The dropped duplicate counts as skipped.
        assertEquals(1, plan.skipped)
    }

    @Test
    fun budgets_negativeLimit_isSkipped_zeroKept() {
        val local = localDoc(budgets = listOf(LocalBudget("FOOD", 0), LocalBudget("SHOPPING", -1)))

        val plan = planMigration(local, remoteSnapshot())

        assertEquals(listOf("FOOD"), plan.budgets.map { it.categoryId })
        assertEquals(1, plan.skipped)
    }

    @Test
    fun replanningAgainstAccountThatHasEverythingWritten_isEmpty() {
        val local =
            localDoc(
                categories = listOf(localCategory("gym", "Gym"), localCategory("FOOD", "Meals")),
                expenses = listOf(localExpense("e1", "gym"), localExpense("e2", "orphan")),
                income = listOf(localIncome("i1")),
                budgets = listOf(LocalBudget("gym", 100), LocalBudget("SHOPPING", 50)),
            )
        val remote = remoteSnapshot(categories = listOf(userCategory("other", "Other", order = 7)))

        val first = planMigration(local, remote)
        val second = planMigration(local, remote.after(first))

        assertTrue(first.total > 0)
        assertEquals(0, first.skipped)
        assertEquals(0, second.total)
        assertEquals(0, second.skipped)
    }

    @Test
    fun replanningAfterPartialRun_returnsOnlyWhatIsLeft() {
        val local =
            localDoc(
                categories = listOf(localCategory("gym", "Gym")),
                expenses = listOf(localExpense("e1", "gym"), localExpense("e2", "gym")),
            )
        val first = planMigration(local, remoteSnapshot())
        // Categories and one expense were committed before the failure.
        val partial = remoteSnapshot(expenseIds = setOf("e1")).after(first.copy(expenses = emptyList()))

        val second = planMigration(local, partial)

        assertTrue(second.categories.isEmpty())
        assertEquals(listOf("e2"), second.expenses.map { it.id })
    }
}
