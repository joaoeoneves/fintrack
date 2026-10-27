package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PlannedSource
import com.joaoeoneves.fintrack.testutil.assertFailsWithError
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The category groups and recurring-derived planned amounts [ObservePeriodOverview] assembles. */
class ObservePeriodOverviewGroupsTest : ObservePeriodOverviewTestBase() {
    private val billsCategory = BuiltInCategories.RECURRING.id

    @Test
    fun noGroupsOrData_hasNoGroupRows() =
        runTest {
            assertTrue(overview(Period.CurrentMonth).groups.isEmpty())
        }

    @Test
    fun groups_sumPlannedFromBudgetsAndRecurring_andSpend() =
        runTest {
            val food = groups.addGroup("Food").getOrThrow()
            val bills = groups.addGroup("Bills").getOrThrow()
            categories.setGroup(BuiltInCategories.FOOD.id, food)
            categories.setGroup(BuiltInCategories.RECURRING.id, bills)
            budgets.delegate.setBudget(BuiltInCategories.FOOD.id, 16_000L)
            recurring.saveRecurring(recurringItem("r1", amountCents = 5_000L, categoryId = billsCategory))
            recurring.saveRecurring(recurringItem("r2", amountCents = 3_650L, categoryId = billsCategory))
            expenses.delegate.addExpense(testExpense("e1", 4_000L, noonOn(october, 5), BuiltInCategories.FOOD))

            val result = overview(Period.CurrentMonth)

            assertEquals(listOf("Food", "Bills"), result.groups.map { it.group?.name })
            assertEquals(16_000L, result.groups[0].planned)
            assertEquals(4_000L, result.groups[0].spent)
            assertEquals(8_650L, result.groups[1].planned)
            assertEquals(2, result.recurring.size)
            val row = result.budgets.single { it.category.id == BuiltInCategories.RECURRING.id }
            assertNull(row.limitCents)
            assertEquals(PlannedSource.RECURRING, row.plannedSource)
        }

    @Test
    fun skippedMonthBudget_fallsBackToRecurring_inTheBudgetMonth() =
        runTest {
            budgets.delegate.setBudget(BuiltInCategories.FOOD.id, 16_000L, setOf(10))
            recurring.saveRecurring(recurringItem("r1", amountCents = 1_500L, categoryId = BuiltInCategories.FOOD.id))

            val food = overview(Period.CurrentMonth).budgets.single { it.category == BuiltInCategories.FOOD }

            assertEquals(1_500L, food.planned)
            assertEquals(PlannedSource.RECURRING, food.plannedSource)
            assertEquals(16_000L, food.limitCents)
            assertEquals(setOf(10), food.skipMonths)
        }

    @Test
    fun ungroupedSpend_appearsAsOtherGroup() =
        runTest {
            groups.addGroup("Food")
            expenses.delegate.addExpense(testExpense("e1", 700L, noonOn(october, 5), BuiltInCategories.SHOPPING))

            val result = overview(Period.CurrentMonth).groups

            assertNull(result.last().group)
            assertEquals(700L, result.last().spent)
        }

    @Test
    fun groupsFlowError_propagates() =
        runTest {
            val error = IllegalStateException("groups down")
            groups.nextObserveGroupsError = error

            assertFailsWithError(error) { overview(Period.CurrentMonth) }
        }

    @Test
    fun recurringFlowError_propagates() =
        runTest {
            val error = IllegalStateException("recurring down")
            recurring.nextObserveError = error

            assertFailsWithError(error) { overview(Period.CurrentMonth) }
        }

    @Test
    fun budgetWithOtherMonthSkipped_stillApplies() =
        runTest {
            budgets.delegate.setBudget(BuiltInCategories.FOOD.id, 100L, setOf(8))

            val food = overview(Period.CurrentMonth).budgets.single { it.category == BuiltInCategories.FOOD }

            assertEquals(PlannedSource.BUDGET, food.plannedSource)
            assertEquals(100L, food.planned)
        }
}
