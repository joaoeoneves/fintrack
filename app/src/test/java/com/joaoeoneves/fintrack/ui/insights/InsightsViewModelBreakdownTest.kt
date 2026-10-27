package com.joaoeoneves.fintrack.ui.insights

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryTotal
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.dashboard.clampedForChart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The actual-vs-planned category breakdown, refunds, empty state and the Error state of [InsightsViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class InsightsViewModelBreakdownTest : InsightsViewModelTestBase() {
    @Test
    fun emptyRepositories_isEmpty_withAllCategoriesAtZero() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            val content = vm.content()
            assertTrue(content.isEmpty)
            assertFalse(content.breakdown.hasExpenses)
            assertTrue(content.breakdown.plannedSlices.isEmpty())
            assertEquals(
                BuiltInCategories.all.filterNot { it.archived },
                content.breakdown.categoryTotals.map { it.category },
            )
            assertTrue(content.breakdown.categoryTotals.all { it.totalCents == 0L })
        }

    @Test
    fun actualTotals_sumPerCategory_andIgnoreOtherMonths() =
        runTest(testDispatcher) {
            val repo =
                FakeExpenseRepository(
                    listOf(
                        expense(id = "s1", amountCents = 1_000L),
                        expense(id = "s2", amountCents = 500L, date = daysAgo(2)),
                        expense(id = "t1", amountCents = 2_000L, category = BuiltInCategories.TRANSFER),
                        expense(id = "i1", amountCents = 3_000L, category = BuiltInCategories.INVESTMENTS),
                        expense(id = "old", amountCents = 9_999L, date = noonOn(previousMonth, 28)),
                    ),
                )
            val vm = viewModel(repo)
            collect(vm)

            val content = vm.content()
            assertFalse(content.isEmpty)
            assertTrue(content.breakdown.hasExpenses)
            assertEquals(1_500L, content.total(BuiltInCategories.SHOPPING))
            assertEquals(2_000L, content.total(BuiltInCategories.TRANSFER))
            assertEquals(3_000L, content.total(BuiltInCategories.INVESTMENTS))
            assertEquals(0L, content.total(BuiltInCategories.FOOD))
        }

    @Test
    fun foodExpense_appearsOnlyUnderFood() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(expense("f", 1_234L, BuiltInCategories.FOOD))))
            collect(vm)

            val content = vm.content()
            assertEquals(1_234L, content.total(BuiltInCategories.FOOD))
            val others = content.breakdown.categoryTotals.filter { it.category != BuiltInCategories.FOOD }
            assertTrue(others.all { it.totalCents == 0L })
        }

    @Test
    fun plannedSlices_listOnlyCategoriesWithALimit() =
        runTest(testDispatcher) {
            val budgets =
                FakeBudgetRepository(
                    listOf(
                        Budget(BuiltInCategories.SHOPPING.id, 24_000L),
                        Budget(BuiltInCategories.INVESTMENTS.id, 10_000L),
                    ),
                )
            val vm = viewModel(budgetRepository = budgets)
            collect(vm)

            val content = vm.content()
            assertEquals(
                setOf(
                    CategoryTotal(BuiltInCategories.INVESTMENTS, 10_000L),
                    CategoryTotal(BuiltInCategories.SHOPPING, 24_000L),
                ),
                content.breakdown.plannedSlices.toSet(),
            )
            assertEquals(2, content.breakdown.plannedSlices.size)
        }

    @Test
    fun planWithoutExpenses_isNotEmpty() =
        runTest(testDispatcher) {
            val budgets = FakeBudgetRepository(listOf(Budget(BuiltInCategories.SHOPPING.id, 5_000L)))
            val vm = viewModel(budgetRepository = budgets)
            collect(vm)

            val content = vm.content()
            assertFalse(content.breakdown.hasExpenses)
            assertFalse(content.isEmpty)
        }

    @Test
    fun expensesWithoutPlan_isNotEmpty_withNoPlannedSlices() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(expense("a", 100L))))
            collect(vm)

            val content = vm.content()
            assertTrue(content.breakdown.plannedSlices.isEmpty())
            assertFalse(content.isEmpty)
        }

    @Test
    fun zeroLimit_isNotAPlannedSlice() =
        runTest(testDispatcher) {
            val budgets = FakeBudgetRepository(listOf(Budget(BuiltInCategories.SHOPPING.id, 0L)))
            val vm = viewModel(budgetRepository = budgets)
            collect(vm)

            assertTrue(
                vm
                    .content()
                    .breakdown.plannedSlices
                    .isEmpty(),
            )
        }

    @Test
    fun plannedSlices_followBudgetChangesLive() =
        runTest(testDispatcher) {
            val budgets = FakeBudgetRepository()
            val vm = viewModel(budgetRepository = budgets)
            collect(vm)
            assertTrue(
                vm
                    .content()
                    .breakdown.plannedSlices
                    .isEmpty(),
            )

            budgets.setBudget(BuiltInCategories.FOOD.id, 7_000L)
            advanceUntilIdle()

            assertEquals(
                listOf(CategoryTotal(BuiltInCategories.FOOD, 7_000L)),
                vm.content().breakdown.plannedSlices,
            )
        }

    @Test
    fun refund_reducesCategoryTotal() =
        runTest(testDispatcher) {
            val repo =
                FakeExpenseRepository(listOf(expense("a", 10_000L), refund("r", 4_000L)))
            val vm = viewModel(repo)
            collect(vm)

            assertEquals(6_000L, vm.content().total(BuiltInCategories.SHOPPING))
        }

    @Test
    fun refundLargerThanSpend_keepsNegativeTotal_andChartClampsToZero() =
        runTest(testDispatcher) {
            val repo =
                FakeExpenseRepository(
                    listOf(
                        expense("small", 1_000L),
                        refund("big", 3_500L),
                        expense("rent", 20_000L, BuiltInCategories.TRANSFER),
                    ),
                )
            val vm = viewModel(repo)
            collect(vm)

            val content = vm.content()
            assertEquals(-2_500L, content.total(BuiltInCategories.SHOPPING))
            val chart = content.breakdown.categoryTotals.clampedForChart()
            assertEquals(0L, chart.single { it.category == BuiltInCategories.SHOPPING }.totalCents)
            assertEquals(20_000L, chart.single { it.category == BuiltInCategories.TRANSFER }.totalCents)
        }

    @Test
    fun refundInOtherCategory_doesNotAffectThisCategory() =
        runTest(testDispatcher) {
            val repo =
                FakeExpenseRepository(listOf(expense("a", 6_000L), refund("r", 5_000L, BuiltInCategories.TRANSFER)))
            val vm = viewModel(repo)
            collect(vm)

            val content = vm.content()
            assertEquals(6_000L, content.total(BuiltInCategories.SHOPPING))
            assertEquals(-5_000L, content.total(BuiltInCategories.TRANSFER))
        }

    @Test
    fun zeroAmountExpense_countsAsHavingExpenses_withZeroTotal() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(expense("z", 0L))))
            collect(vm)

            val content = vm.content()
            assertTrue(content.breakdown.hasExpenses)
            assertEquals(0L, content.total(BuiltInCategories.SHOPPING))
        }

    @Test
    fun expensesFailure_surfacesErrorState() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(nextObserveExpensesError = IllegalStateException("Firestore unavailable"))
            val vm = viewModel(repo)
            collect(vm)

            assertEquals(
                InsightsUiState.Error(UiText.StringResource(R.string.error_generic_fallback)),
                vm.uiState.value,
            )
        }

    @Test
    fun retry_afterFailure_recoversToContent() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(nextObserveExpensesError = IllegalStateException("transient"))
            val vm = viewModel(repo)
            collect(vm)
            assertTrue(vm.uiState.value is InsightsUiState.Error)

            repo.nextObserveExpensesError = null
            vm.onRetry()
            advanceUntilIdle()

            assertTrue(vm.uiState.value is InsightsUiState.Content)
        }

    @Test
    fun budgetsFailure_surfacesErrorState() =
        runTest(testDispatcher) {
            val budgets = FakeBudgetRepository()
            budgets.nextObserveBudgetsError = IllegalStateException("boom")
            val vm = viewModel(budgetRepository = budgets)
            collect(vm)

            assertTrue(vm.uiState.value is InsightsUiState.Error)
        }
}
