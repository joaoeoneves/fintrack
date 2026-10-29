package com.joaoeoneves.fintrack.ui.income

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.MANAGED_DUODECIMOS
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.MAX_PLANNED_INCOME_LINES
import com.joaoeoneves.fintrack.domain.model.SalaryComponent
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.salaryProfileLines
import com.joaoeoneves.fintrack.ui.plan.IncomeSchedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** The plan's per-component income rows, limit groups and the income line cap around managed lines. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class IncomeTabViewModelIncomeRowsTest : IncomeTabViewModelTestBase() {
    private val profile = SalaryProfile(baseGrossCents = 100_000, irsBasePpm = 100_000, mealRatePerDayCents = 900)
    private val managed = salaryProfileLines(profile, emptyList())

    @Test
    fun incomeRows_areOnePerComponent_thenManualLines() =
        runTest(testDispatcher) {
            managed.forEach { incomeRepo.savePlannedIncome(it) }
            incomeRepo.savePlannedIncome(line("own", amountCents = 5_000, order = 50))
            val vm = viewModel()
            collect(vm)

            val rows = vm.content().incomeRows

            assertEquals(
                listOf(SalaryComponent.SALARY, SalaryComponent.MEAL, SalaryComponent.SUBSIDIES, null),
                rows.map { it.component },
            )
            assertEquals(managed.size + 1, incomeRepo.lines.size)
            assertEquals(5_000L, rows.last().cents)
            assertEquals(79_000L, rows.first().cents)
        }

    @Test
    fun incomeRows_empty_withoutLines() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            assertTrue(vm.content().incomeRows.isEmpty())
        }

    @Test
    fun cap_ignoresManagedLines() =
        runTest(testDispatcher) {
            managed.forEach { incomeRepo.savePlannedIncome(it) }
            repeat(MAX_PLANNED_INCOME_LINES - 1) { incomeRepo.savePlannedIncome(line("l$it", order = 100 + it)) }
            val vm = viewModel()
            collect(vm)

            assertTrue(vm.content().canAddIncomeLine)
            vm.onAddIncomeLine()
            advanceUntilIdle()
            assertTrue(vm.content().editor != null)
        }

    @Test
    fun cap_reachedByManualLinesOnly() =
        runTest(testDispatcher) {
            managed.forEach { incomeRepo.savePlannedIncome(it) }
            repeat(MAX_PLANNED_INCOME_LINES) { incomeRepo.savePlannedIncome(line("l$it", order = 100 + it)) }
            val vm = viewModel()
            collect(vm)

            vm.onAddIncomeLine()
            advanceUntilIdle()

            assertFalse(vm.content().canAddIncomeLine)
            assertNull(vm.content().editor)
        }

    @Test
    fun saveManagedLine_withItsLabel_keepsAmountAndSchedule() =
        runTest(testDispatcher) {
            managed.forEach { incomeRepo.savePlannedIncome(it) }
            val vm = viewModel()
            collect(vm)
            vm.onEditIncomeLine(MANAGED_DUODECIMOS)
            advanceUntilIdle()

            val original = managed.single { it.id == MANAGED_DUODECIMOS }
            vm.onSaveIncomeLine(original.label, 1, IncomeSchedule(), YearMonth.of(2026, 10))
            advanceUntilIdle()

            val saved = incomeRepo.lines.single { it.id == MANAGED_DUODECIMOS }
            assertEquals(original, saved)
            assertTrue(incomeRepo.lines.any { it.id == MANAGED_NET_SALARY })
            assertNull(vm.content().editor)
        }
}
