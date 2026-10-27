package com.joaoeoneves.fintrack.ui.plan.annual

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryGroupRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import com.joaoeoneves.fintrack.domain.usecase.ObserveAnnualPlan
import com.joaoeoneves.fintrack.domain.usecase.PlanDataSources
import com.joaoeoneves.fintrack.testutil.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AnnualPlanViewModelTest {
    private class Transactions : TransactionRepository {
        var failNext: Boolean = false

        override fun observeTransactions(range: InstantRange): Flow<TransactionsInRange> =
            flow {
                if (failNext) {
                    failNext = false
                    error("boom")
                }
                emit(TransactionsInRange(range, emptyList(), emptyList()))
            }
    }

    private val dispatcher = StandardTestDispatcher()
    private val transactions = Transactions()
    private val lines = FakePlannedIncomeRepository(listOf(PlannedIncome("salary", "salary", 1_000L, 0)))
    private val clock = MutableClock(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        AnnualPlanViewModel(
            ObserveAnnualPlan(
                transactions,
                PlanDataSources(
                    FakeBudgetRepository(),
                    lines,
                    FakeMonthSettingsRepository(),
                    FakeCategoryRepository(),
                    FakeCategoryGroupRepository(),
                    FakeRecurringRepository(),
                ),
                FakeGoalRepository(),
            ),
            handle,
            clock,
        )

    private fun TestScope.collect(vm: AnnualPlanViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        backgroundScope.launch { vm.selectedTab.collect {} }
        advanceUntilIdle()
    }

    private fun AnnualPlanViewModel.content() = uiState.value as AnnualPlanUiState.Content

    @Test
    fun defaultTab_isMonth() =
        runTest(dispatcher) {
            assertEquals(PlanTab.MONTH, viewModel().selectedTab.value)
        }

    @Test
    fun unknownStoredTab_fallsBackToMonth() =
        runTest(dispatcher) {
            assertEquals(PlanTab.MONTH, viewModel(SavedStateHandle(mapOf("planTab" to "bogus"))).selectedTab.value)
        }

    @Test
    fun selectedTab_survivesRecreation_viaSavedState() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val first = viewModel(handle)
            collect(first)

            first.onSelectTab(PlanTab.YEAR)
            advanceUntilIdle()

            assertEquals(PlanTab.YEAR, first.selectedTab.value)
            assertEquals("year", handle.get<String>("planTab"))
            assertEquals(PlanTab.YEAR, viewModel(handle).selectedTab.value)
        }

    @Test
    fun year_defaultsToTheClocksYear() =
        runTest(dispatcher) {
            val vm = viewModel()
            collect(vm)

            assertEquals(2026, vm.content().year)
            assertEquals(2026, vm.content().plan.year)
            assertEquals(
                12_000L,
                vm
                    .content()
                    .plan.income.regularCents,
            )
        }

    @Test
    fun previousAndNext_moveTheYear_andPersistIt() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val vm = viewModel(handle)
            collect(vm)

            vm.onPreviousYear()
            advanceUntilIdle()
            assertEquals(2025, vm.content().year)
            vm.onNextYear()
            vm.onNextYear()
            advanceUntilIdle()
            assertEquals(2027, vm.content().year)

            val recreated = viewModel(handle)
            collect(recreated)
            assertEquals(2027, recreated.content().year)
        }

    @Test
    fun year_isClampedToTheSupportedRange() =
        runTest(dispatcher) {
            val handle = SavedStateHandle(mapOf("annualYear" to 2000))
            val vm = viewModel(handle)
            collect(vm)

            assertFalse(vm.content().canGoPrevious)
            assertTrue(vm.content().canGoNext)
            vm.onPreviousYear()
            advanceUntilIdle()
            assertEquals(2000, vm.content().year)

            handle["annualYear"] = 2100
            advanceUntilIdle()
            assertTrue(vm.content().canGoPrevious)
            assertFalse(vm.content().canGoNext)
            vm.onNextYear()
            advanceUntilIdle()
            assertEquals(2100, vm.content().year)
        }

    @Test
    fun emptyPlan_hasNoData_andPlannedIncomeHasData() =
        runTest(dispatcher) {
            val vm = viewModel()
            collect(vm)
            assertTrue(vm.content().hasData)

            lines.deletePlannedIncome("salary")
            advanceUntilIdle()
            assertFalse(vm.content().hasData)
        }

    @Test
    fun error_thenRetry_recovers() =
        runTest(dispatcher) {
            transactions.failNext = true
            val vm = viewModel()
            collect(vm)

            assertTrue(vm.uiState.value is AnnualPlanUiState.Error)

            vm.onRetry()
            advanceUntilIdle()

            assertTrue(vm.uiState.value is AnnualPlanUiState.Content)
        }
}
