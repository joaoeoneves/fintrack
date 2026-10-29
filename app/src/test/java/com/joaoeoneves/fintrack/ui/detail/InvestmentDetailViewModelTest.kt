package com.joaoeoneves.fintrack.ui.detail

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.usecase.ObserveInvestmentHistory
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.userCategory
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class InvestmentDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)
    private val etf = userCategory("etf", kind = CategoryKind.SAVING)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun inv(
        id: String,
        cents: Long,
        m: Int,
        d: Int,
    ): Expense {
        val date = LocalDate.of(2026, m, d).atTime(12, 0).toInstant(ZoneOffset.UTC)
        return testExpense(id, cents, date, etf).copy(name = "VWCE")
    }

    private fun vm(vararg args: Pair<String, Any?>): Pair<InvestmentDetailViewModel, SavedStateHandle> {
        val data = listOf(inv("jan", 1_000, 1, 5), inv("sep", 3_000, 9, 5), inv("oct", 4_000, 10, 5))
        val handle = SavedStateHandle(mapOf(*args))
        val use = ObserveInvestmentHistory(FakeExpenseRepository(data), FakeCategoryRepository(listOf(etf)))
        return InvestmentDetailViewModel(handle, use, clock) to handle
    }

    private fun TestScope.content(vm: InvestmentDetailViewModel): InvestmentDetailUiState.Content {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm.uiState.value as InvestmentDetailUiState.Content
    }

    @Test
    fun monthScale_showsTheSelectedMonthAmount_yearAndAllTime() =
        runTest(testDispatcher) {
            val c = content(vm().first)

            assertEquals(12, c.months.size)
            assertEquals(Period.CurrentMonth, c.period)
            assertEquals(4_000L, c.amountCents)
            assertEquals(8_000L, c.yearCents)
            assertEquals(8_000L, c.allTimeCents)
            assertTrue(c.isCurrentMonth)
            assertEquals(listOf("oct"), c.movements.map { it.id })
        }

    @Test
    fun selectingAnotherMonth_changesTheAmountAndPersists() =
        runTest(testDispatcher) {
            val (vm, handle) = vm()
            content(vm)

            vm.onSelectMonth(YearMonth.of(2026, 9))
            advanceUntilIdle()

            assertEquals(3_000L, (vm.uiState.value as InvestmentDetailUiState.Content).amountCents)
            assertEquals("month:2026-09", handle.get<String>("selectedPeriod"))
        }

    @Test
    fun yearScale_sumsTheYear_withNoSelection() =
        runTest(testDispatcher) {
            val c = content(vm("period" to "year:2026").first)

            assertEquals(Period.Year(2026), c.period)
            assertEquals(NO_SELECTION, c.selectedIndex)
            assertEquals(8_000L, c.amountCents)
            assertEquals(3, c.movements.size)
        }

    @Test
    fun emptyYear_isZero() =
        runTest(testDispatcher) {
            val c = content(vm("period" to "year:2025").first)

            assertEquals(0L, c.amountCents)
            assertTrue(c.movements.isEmpty())
        }
}
