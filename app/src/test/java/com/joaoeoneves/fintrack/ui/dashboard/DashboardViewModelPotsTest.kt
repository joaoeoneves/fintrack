package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryGroupRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryGroup
import com.joaoeoneves.fintrack.domain.model.DailyAllowance
import com.joaoeoneves.fintrack.domain.model.Earmark
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pots (earmarked income) in [DashboardViewModel]: the mapping and `safeToSpendCents`. "Now" is 2026-10-20. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardViewModelPotsTest : DashboardViewModelTestBase() {
    private val salary = PlannedIncome(id = "salary", label = "Salary", amountCents = 100_000L, order = 0)
    private val shopping = BuiltInCategories.SHOPPING

    private fun potsViewModel(
        lines: List<PlannedIncome>,
        budgets: List<Budget> = emptyList(),
        expenses: FakeExpenseRepository = FakeExpenseRepository(),
        categories: FakeCategoryRepository = FakeCategoryRepository(),
        groups: FakeCategoryGroupRepository = FakeCategoryGroupRepository(),
    ) = dashboardViewModel(
        DashboardRepositories(
            expenseRepository = expenses,
            budgetRepository = FakeBudgetRepository(budgets),
            incomeRepository = FakeIncomeRepository(),
            categoryRepository = categories,
            plannedIncomeRepository = FakePlannedIncomeRepository(lines),
            categoryGroupRepository = groups,
        ),
        SavedStateHandle(),
        clock,
    )

    @Test
    fun noEarmarks_safeToSpendEqualsPlan_andNoPots() =
        runTest(testDispatcher) {
            val vm = potsViewModel(listOf(salary), listOf(Budget(shopping.id, 20_000L)))
            collect(vm)

            val content = vm.content()
            assertEquals(emptyList<Any>(), content.pots?.pots)
            assertEquals(80_000L, content.plan.safeToSpendCents)
            assertEquals(80_000L, content.safeToSpendCents)
            assertEquals(DailyAllowance(cents = 6_666L, daysLeft = 12), content.dailyAllowance)
        }

    @Test
    fun categoryEarmark_mapsPot_andSafeToSpendIsBaseFree() =
        runTest(testDispatcher) {
            val meal = PlannedIncome("meal", "Meal", 10_000L, 1, earmark = Earmark.Category(shopping.id.value))
            val vm = potsViewModel(listOf(salary, meal), listOf(Budget(shopping.id, 4_000L)))
            collect(vm)

            val content = vm.content()
            val pot = content.pots!!.pots.single()
            assertEquals(Earmark.Category(shopping.id.value), pot.target)
            assertEquals(10_000L, pot.expectedIncomeCents)
            assertEquals(6_000L, pot.freeCents)
            assertEquals(100_000L, content.pots.base.freeCents)
            assertEquals(100_000L, content.safeToSpendCents)
            // The plan itself is unchanged: it knows nothing of pots.
            assertEquals(106_000L, content.plan.safeToSpendCents)
        }

    @Test
    fun overspentPot_spillsIntoSafeToSpend_andDailyAllowance() =
        runTest(testDispatcher) {
            val meal = PlannedIncome("meal", "Meal", 10_000L, 1, earmark = Earmark.Category(shopping.id.value))
            val expenses = FakeExpenseRepository()
            val vm = potsViewModel(listOf(salary, meal), listOf(Budget(shopping.id, 4_000L)), expenses)
            expenses.addExpense(expense("e1", 15_000L, shopping, date = daysAgo(1)))
            collect(vm)

            val content = vm.content()
            assertEquals(
                -5_000L,
                content.pots!!
                    .pots
                    .single()
                    .freeCents,
            )
            assertEquals(95_000L, content.safeToSpendCents)
            // 95 000 over 12 days left, rounded down.
            assertEquals(DailyAllowance(cents = 7_916L, daysLeft = 12), content.dailyAllowance)
        }

    @Test
    fun groupEarmark_mapsGroupPot() =
        runTest(testDispatcher) {
            val group = CategoryGroup("g-fun", "Fun", 0)
            val hobby = userCategory("hobby", "Hobby").copy(groupId = group.id)
            val meal = PlannedIncome("fun", "Fun money", 5_000L, 1, earmark = Earmark.Group(group.id))
            val vm =
                potsViewModel(
                    listOf(salary, meal),
                    listOf(Budget(hobby.id, 2_000L)),
                    categories = FakeCategoryRepository(listOf(hobby)),
                    groups = FakeCategoryGroupRepository(listOf(group)),
                )
            collect(vm)

            val pot =
                vm
                    .content()
                    .pots!!
                    .pots
                    .single()
            assertEquals(Earmark.Group(group.id), pot.target)
            assertEquals(group, pot.group)
            assertEquals(3_000L, pot.freeCents)
            assertEquals(100_000L, vm.content().safeToSpendCents)
        }

    @Test
    fun nothingConfigured_safeToSpendIsZero() =
        runTest(testDispatcher) {
            val vm = potsViewModel(emptyList())
            collect(vm)

            assertNotNull(vm.content().pots)
            assertEquals(0L, vm.content().safeToSpendCents)
            assertNull(vm.content().dailyAllowance)
        }

    @Test
    fun safeToSpend_fallsBackToPlanWhenPotsAbsent() =
        runTest(testDispatcher) {
            val vm = potsViewModel(listOf(salary))
            collect(vm)

            val content = vm.content().copy(pots = null)

            assertEquals(content.plan.safeToSpendCents, content.safeToSpendCents)
        }
}
