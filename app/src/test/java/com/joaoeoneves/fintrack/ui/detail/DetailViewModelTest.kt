package com.joaoeoneves.fintrack.ui.detail

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.CombinedTransactionRepository
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.IncomeSourceKey
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.rowKey
import com.joaoeoneves.fintrack.domain.usecase.ObserveMonthlyHistory
import com.joaoeoneves.fintrack.domain.usecase.ObservePeriodOverview
import com.joaoeoneves.fintrack.domain.usecase.PlanDataSources
import com.joaoeoneves.fintrack.testutil.CountingExpenseRepository
import com.joaoeoneves.fintrack.testutil.CountingIncomeRepository
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import com.joaoeoneves.fintrack.ui.activity.analytics.encode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)
    private val rent = PlannedIncome("rent", "Rent", 20_000L, 1)
    private val rentKey = IncomeSourceKey.Row(null, null, "rent")
    private val salary = PlannedIncome("sal", "Salário líquido", 100_000L, 0, managedKey = MANAGED_NET_SALARY)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun at(
        y: Int,
        m: Int,
        d: Int,
    ): Instant = LocalDate.of(y, m, d).atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC)

    private val expenses: List<Expense> =
        listOf(
            testExpense("oct", 1_000L, at(2026, 10, 3), BuiltInCategories.SHOPPING),
            testExpense("sep", 3_000L, at(2026, 9, 3), BuiltInCategories.SHOPPING),
            testExpense("aug", 5_000L, at(2026, 8, 3), BuiltInCategories.SHOPPING),
        )
    private val incomes: List<Income> =
        listOf(
            testIncome("pay_rent_202610", 20_000L, at(2026, 10, 5)),
            testIncome("pay_rent_202609", 10_000L, at(2026, 9, 5)),
            testIncome("pay_sal_202610", 100_000L, at(2026, 10, 25)),
            testIncome("gift", 5_000L, at(2026, 10, 6)).copy(source = "Gift"),
        )

    private suspend fun detail(vararg args: Pair<String, Any?>): Pair<DetailViewModel, SavedStateHandle> {
        val transactions =
            CombinedTransactionRepository(CountingExpenseRepository(expenses), CountingIncomeRepository(incomes))
        val plannedIncome = FakePlannedIncomeRepository()
        plannedIncome.savePlannedIncome(rent)
        plannedIncome.savePlannedIncome(salary)
        val sources =
            PlanDataSources(
                budgets = FakeBudgetRepository(),
                plannedIncome = plannedIncome,
                monthSettings = FakeMonthSettingsRepository(),
                categories = FakeCategoryRepository(),
                envelopes = FakeEnvelopeRepository(),
                recurring = FakeRecurringRepository(),
            )
        val handle = SavedStateHandle(mapOf(*args))
        val vm =
            DetailViewModel(
                handle,
                ObserveMonthlyHistory(transactions, sources),
                ObservePeriodOverview(transactionRepository = transactions, sources = sources),
                FakeCategoryRepository(),
                clock,
            )
        return vm to handle
    }

    private fun TestScope.state(vm: DetailViewModel): DetailUiState {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm.uiState.value
    }

    private val shopping = BuiltInCategories.SHOPPING.id.value

    @Test
    fun categoryMode_showsTwelveMonths_selectsTheRouteMonth_andMovements() =
        runTest(testDispatcher) {
            val c = state(detail("categoryId" to shopping).first) as DetailUiState.Content

            assertEquals(12, c.months.size)
            assertEquals(Period.CurrentMonth, c.period)
            assertEquals(1_000L, c.amountCents)
            assertEquals(listOf("oct"), c.movements.map { it.id })
            assertEquals(DetailEditTarget.CategoryEditor(shopping), c.editTarget)
            // (1000 vs the 3000/5000 average of 4000) = -75%.
            assertEquals(-75, c.delta?.percent)
        }

    @Test
    fun categoryMode_selectedMonthPersistsUnderSelectedPeriodKey_andOutOfWindowIsIgnored() =
        runTest(testDispatcher) {
            val (vm, handle) = detail("categoryId" to shopping)
            state(vm)

            vm.onSelectMonth(YearMonth.of(2026, 9))
            advanceUntilIdle()
            assertEquals("month:2026-09", handle.get<String>("selectedPeriod"))
            val c = vm.uiState.value as DetailUiState.Content
            assertEquals(3_000L, c.amountCents)
            assertEquals(listOf("sep"), c.movements.map { it.id })

            vm.onSelectMonth(YearMonth.of(2024, 1))
            advanceUntilIdle()
            assertEquals("month:2026-09", handle.get<String>("selectedPeriod"))
        }

    @Test
    fun yearRoute_opensTheYear_andMonthSelectionIsIgnored() =
        runTest(testDispatcher) {
            val (vm, handle) = detail("categoryId" to shopping, "period" to "year:2026")
            val c = state(vm) as DetailUiState.Content
            assertEquals(Period.Year(2026), c.period)

            vm.onSelectMonth(YearMonth.of(2026, 9))
            advanceUntilIdle()
            assertEquals(Period.Year(2026), (vm.uiState.value as DetailUiState.Content).period)
            assertEquals(null, handle.get<String>("selectedPeriod")?.takeIf { it.startsWith("month") })
        }

    @Test
    fun restoredMonth_isUsed_andGarbageFallsBackToTheEndMonth() =
        runTest(testDispatcher) {
            val restored = state(detail("categoryId" to shopping, "selectedPeriod" to "month:2026-08").first)
            assertEquals(5_000L, (restored as DetailUiState.Content).amountCents)

            val garbage = state(detail("categoryId" to shopping, "selectedPeriod" to "nope").first)
            assertEquals(Period.CurrentMonth, (garbage as DetailUiState.Content).period)
        }

    @Test
    fun routePeriod_endsTheWindowAtThatMonth() =
        runTest(testDispatcher) {
            val period = Period.Month(YearMonth.of(2026, 9)).encode()
            val c = state(detail("categoryId" to shopping, "period" to period).first) as DetailUiState.Content

            assertEquals(YearMonth.of(2026, 9), c.months.last().month)
            assertEquals(3_000L, c.amountCents)
        }

    @Test
    fun unknownCategoryWithoutData_isNotFound() =
        runTest(testDispatcher) {
            assertEquals(DetailUiState.NotFound, state(detail("categoryId" to "ghost").first))
        }

    @Test
    fun noArguments_orMalformedSourceKey_isNotFound() =
        runTest(testDispatcher) {
            assertEquals(DetailUiState.NotFound, state(detail().first))
            assertEquals(DetailUiState.NotFound, state(detail("sourceKey" to "garbage").first))
        }

    @Test
    fun sourceMode_manualLine_editsIncomeLinesOfTheRoutePeriod() =
        runTest(testDispatcher) {
            val c = state(detail("sourceKey" to rentKey.encode()).first) as DetailUiState.Content

            assertTrue(c.isIncome)
            assertEquals(20_000L, c.amountCents)
            assertEquals(listOf("pay_rent_202610"), c.movements.map { it.id })
            assertEquals(DetailEditTarget.IncomeLines(Period.CurrentMonth.encode()), c.editTarget)
            assertEquals("Rent", (c.subject as DetailSubject.OfSource).label)
        }

    @Test
    fun sourceMode_managedComponent_editsTheSalaryProfile() =
        runTest(testDispatcher) {
            val key = salary.rowKey().encode()
            val c = state(detail("sourceKey" to key).first) as DetailUiState.Content

            assertEquals(DetailEditTarget.SalaryProfile, c.editTarget)
        }

    @Test
    fun sourceMode_namedSource_hasNoEditTarget() =
        runTest(testDispatcher) {
            val key = IncomeSourceKey.Named("gift").encode()
            val c = state(detail("sourceKey" to key).first) as DetailUiState.Content

            assertNull(c.editTarget)
            assertEquals(5_000L, c.amountCents)
        }
}
