package com.joaoeoneves.fintrack.ui.income

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.payDateIn
import com.joaoeoneves.fintrack.ui.plan.IncomeSchedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/** Auto-record, pay day and the salary-duplicate warning in [IncomeTabViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class IncomeTabViewModelAutoRecordTest : IncomeTabViewModelTestBase() {
    private val oct = YearMonth.of(2026, 10)
    private val sep = YearMonth.of(2026, 9)

    private val net = PlannedIncome("net", "Net salary", 100_000L, 0, managedKey = MANAGED_NET_SALARY)

    @Test
    fun saveNewLine_persistsAutoRecordAndPayDay() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            vm.onAddIncomeLine()
            advanceUntilIdle()
            vm.onSaveIncomeLine("Rent", 40_000L, IncomeSchedule(autoRecord = true, payDay = 5), oct)
            advanceUntilIdle()

            val saved = incomeRepo.lines.single()
            assertTrue(saved.autoRecord)
            assertEquals(5, saved.payDay)
        }

    @Test
    fun changingPayDay_dropsTheLinesCursor_butUnrelatedEditsKeepIt() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a", 40_000).copy(autoRecord = true, payDay = 5))
            automationRepo.updateLineCursors(set = mapOf("a" to sep))
            val vm = viewModel()
            collect(vm)

            vm.onEditIncomeLine("a")
            advanceUntilIdle()
            vm.onSaveIncomeLine("Renamed", 40_000L, IncomeSchedule(autoRecord = true, payDay = 5), oct)
            advanceUntilIdle()
            assertEquals(sep, automationRepo.settings.lineRecordedThrough["a"])

            vm.onEditIncomeLine("a")
            advanceUntilIdle()
            vm.onSaveIncomeLine("Renamed", 40_000L, IncomeSchedule(autoRecord = true, payDay = 20), oct)
            advanceUntilIdle()
            assertNull(automationRepo.settings.lineRecordedThrough["a"])
        }

    @Test
    fun deletingALine_dropsItsCursor() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a").copy(autoRecord = true))
            automationRepo.updateLineCursors(set = mapOf("a" to sep))
            val vm = viewModel()
            collect(vm)

            vm.onDeleteIncomeLine("a")
            advanceUntilIdle()

            assertTrue(automationRepo.settings.lineRecordedThrough.isEmpty())
            assertTrue(incomeRepo.lines.isEmpty())
        }

    @Test
    fun salaryLookalike_isHeldOnce_thenSavedOnTheSecondTap() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(net)
            val vm = viewModel()
            collect(vm)

            vm.onAddIncomeLine()
            advanceUntilIdle()
            vm.onSaveIncomeLine("Salary", 100_000L, IncomeSchedule(), oct)
            advanceUntilIdle()

            assertTrue(vm.content().editor!!.looksLikeSalary)
            assertEquals(1, incomeRepo.lines.size)

            vm.onSaveIncomeLine("Salary", 100_000L, IncomeSchedule(), oct)
            advanceUntilIdle()

            assertEquals(2, incomeRepo.lines.size)
            assertNull(vm.content().editor)
            assertTrue(vm.content().duplicateKeys.isNotEmpty())
        }

    @Test
    fun unrelatedLine_savesAtOnce() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(net)
            val vm = viewModel()
            collect(vm)

            vm.onAddIncomeLine()
            advanceUntilIdle()
            vm.onSaveIncomeLine("Side gig", 20_000L, IncomeSchedule(), oct)
            advanceUntilIdle()

            assertEquals(2, incomeRepo.lines.size)
            assertNull(vm.content().editor)
        }

    @Test
    fun managedLine_persistsAutoRecordAndPayDay_withLabel() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(net)
            val vm = viewModel()
            collect(vm)

            vm.onEditIncomeLine("net")
            advanceUntilIdle()
            vm.onSaveIncomeLine(
                "Pay",
                1,
                IncomeSchedule(autoRecord = true, payDay = 25),
                oct,
            )
            advanceUntilIdle()

            val saved = incomeRepo.lines.single()
            assertEquals("Pay", saved.label)
            assertTrue(saved.autoRecord)
            assertEquals(25, saved.payDay)
            assertEquals(100_000L, saved.amountCents)
        }

    @Test
    fun receiveDefaultDate_isThePayDateCappedAtToday() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("late").copy(payDay = 28))
            incomeRepo.savePlannedIncome(line("early", order = 1).copy(payDay = 5))
            incomeRepo.savePlannedIncome(line("last", order = 2))
            val vm = viewModel()
            collect(vm)

            val today = vm.content().receipts.today
            val defaults =
                vm.content().incomeRows.associate { row ->
                    row.lines.first().id to minOf(row.payDateIn(oct), today)
                }

            assertEquals(LocalDate.of(2026, 10, 15), today)
            assertEquals(LocalDate.of(2026, 10, 15), defaults["late"])
            assertEquals(LocalDate.of(2026, 10, 5), defaults["early"])
            assertEquals(LocalDate.of(2026, 10, 15), defaults["last"])
        }
}
