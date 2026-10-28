package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.DailyAllowance
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Envelopes (and their funding) in [DashboardViewModel]: the mapping and `safeToSpendCents`. "Now" is 2026-10-20. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardViewModelEnvelopesTest : DashboardViewModelTestBase() {
    private val salary = PlannedIncome(id = "salary", label = "Salary", amountCents = 100_000L, order = 0)
    private val shopping = BuiltInCategories.SHOPPING

    private fun envelopesViewModel(
        lines: List<PlannedIncome>,
        budgets: List<Budget> = emptyList(),
        expenses: FakeExpenseRepository = FakeExpenseRepository(),
        categories: FakeCategoryRepository = FakeCategoryRepository(),
        groups: FakeEnvelopeRepository = FakeEnvelopeRepository(),
    ) = dashboardViewModel(
        DashboardRepositories(
            expenseRepository = expenses,
            budgetRepository = FakeBudgetRepository(budgets),
            incomeRepository = FakeIncomeRepository(),
            categoryRepository = categories,
            plannedIncomeRepository = FakePlannedIncomeRepository(lines),
            envelopeRepository = groups,
        ),
        SavedStateHandle(),
        clock,
    )

    private fun shoppingEnvelope(fundedBy: String? = null) =
        Envelope(
            "g-shop",
            "Shopping",
            0,
            fundedByLineId = fundedBy,
        )

    private fun categoriesInShop() = FakeCategoryRepository(listOf(shopping.copy(groupId = "g-shop")))

    @Test
    fun noFunding_safeToSpendEqualsPlan() =
        runTest(testDispatcher) {
            val vm =
                envelopesViewModel(
                    listOf(salary),
                    listOf(Budget(shopping.id, 20_000L)),
                    categories = categoriesInShop(),
                    groups = FakeEnvelopeRepository(listOf(shoppingEnvelope())),
                )
            collect(vm)

            val content = vm.content()
            assertEquals(80_000L, content.plan.safeToSpendCents)
            assertEquals(80_000L, content.safeToSpendCents)
            assertEquals(DailyAllowance(cents = 6_666L, daysLeft = 12), content.dailyAllowance)
        }

    @Test
    fun fundedEnvelope_surplusStaysOutOfSafeToSpend() =
        runTest(testDispatcher) {
            val meal = PlannedIncome("meal", "Meal", 10_000L, 1)
            val vm =
                envelopesViewModel(
                    listOf(salary, meal),
                    listOf(Budget(shopping.id, 4_000L)),
                    categories = categoriesInShop(),
                    groups = FakeEnvelopeRepository(listOf(shoppingEnvelope("meal"))),
                )
            collect(vm)

            val content = vm.content()
            val progress = content.envelopes!!.envelopes.single()
            assertEquals(10_000L, progress.fundingCents)
            assertEquals(10_000L, progress.leftoverCents)
            assertEquals(100_000L, content.safeToSpendCents)
            // The plan itself is unchanged: it knows nothing of funding.
            assertEquals(106_000L, content.plan.safeToSpendCents)
        }

    @Test
    fun overspentFundedEnvelope_spillsIntoSafeToSpend_andDailyAllowance() =
        runTest(testDispatcher) {
            val meal = PlannedIncome("meal", "Meal", 10_000L, 1)
            val expenses = FakeExpenseRepository()
            val vm =
                envelopesViewModel(
                    listOf(salary, meal),
                    listOf(Budget(shopping.id, 4_000L)),
                    expenses,
                    categories = categoriesInShop(),
                    groups = FakeEnvelopeRepository(listOf(shoppingEnvelope("meal"))),
                )
            expenses.addExpense(expense("e1", 15_000L, shopping, date = daysAgo(1)))
            collect(vm)

            val content = vm.content()
            assertEquals(
                -5_000L,
                content.envelopes!!
                    .envelopes
                    .single()
                    .leftoverCents,
            )
            assertEquals(95_000L, content.safeToSpendCents)
            assertEquals(DailyAllowance(cents = 7_916L, daysLeft = 12), content.dailyAllowance)
        }

    @Test
    fun nothingConfigured_safeToSpendIsZero() =
        runTest(testDispatcher) {
            val vm = envelopesViewModel(emptyList())
            collect(vm)

            assertNotNull(vm.content().envelopes)
            assertEquals(0L, vm.content().safeToSpendCents)
            assertNull(vm.content().dailyAllowance)
        }

    @Test
    fun safeToSpend_fallsBackToPlanWhenEnvelopesAbsent() =
        runTest(testDispatcher) {
            val vm = envelopesViewModel(listOf(salary))
            collect(vm)

            val content = vm.content().copy(envelopes = null)

            assertEquals(content.plan.safeToSpendCents, content.safeToSpendCents)
        }
}
