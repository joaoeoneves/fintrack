package com.joaoeoneves.fintrack.ui.income

import com.joaoeoneves.fintrack.ui.plan.IncomeSchedule
import android.app.Application
import com.joaoeoneves.fintrack.domain.model.Earmark
import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.MANAGED_HOLIDAY
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** Lines generated from the salary profile are read-only except for their label and earmark. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class IncomeTabViewModelManagedTest : IncomeTabViewModelTestBase() {
    private val net =
        PlannedIncome(
            id = MANAGED_NET_SALARY,
            label = "Net salary",
            amountCents = 86_000,
            order = 0,
            frequency = IncomeFrequency.MONTHLY,
            managedKey = MANAGED_NET_SALARY,
        )
    private val holiday =
        PlannedIncome(
            id = MANAGED_HOLIDAY,
            label = "Holiday",
            amountCents = 42_000,
            order = 1,
            frequency = IncomeFrequency.YEARLY,
            monthOfYear = 6,
            managedKey = MANAGED_HOLIDAY,
        )
    private val month = YearMonth.of(2026, 10)

    @Test
    fun deleteManagedLine_isIgnored() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(net)
            val vm = viewModel()
            collect(vm)

            vm.onDeleteIncomeLine(MANAGED_NET_SALARY)
            advanceUntilIdle()

            assertEquals(listOf(net), incomeRepo.lines)
        }

    @Test
    fun deleteOwnLine_stillWorks() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a"))
            val vm = viewModel()
            collect(vm)

            vm.onDeleteIncomeLine("a")
            advanceUntilIdle()

            assertTrue(incomeRepo.lines.isEmpty())
        }

    @Test
    fun editManagedLine_opensEditorMarkedManaged() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(net)
            incomeRepo.savePlannedIncome(line("own", order = 1))
            val vm = viewModel()
            collect(vm)

            vm.onEditIncomeLine(MANAGED_NET_SALARY)
            advanceUntilIdle()
            assertTrue(vm.content().editor!!.managed)

            vm.onEditIncomeLine("own")
            advanceUntilIdle()
            assertEquals(false, vm.content().editor!!.managed)
        }

    @Test
    fun saveManagedLine_keepsAmountAndSchedule_changesLabelAndEarmark() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(net)
            val vm = viewModel()
            collect(vm)
            vm.onEditIncomeLine(MANAGED_NET_SALARY)
            advanceUntilIdle()
            val tampered =
                IncomeSchedule(
                    basis = IncomeBasis.PER_WORKING_DAY,
                    frequency = IncomeFrequency.MONTHLY,
                    skipMonths = setOf(2),
                    earmark = Earmark.Group("g1"),
                )

            vm.onSaveIncomeLine(" My pay ", 1, tampered, month)
            advanceUntilIdle()

            val saved = incomeRepo.lines.single()
            assertEquals("My pay", saved.label)
            assertEquals(Earmark.Group("g1"), saved.earmark)
            assertEquals(86_000, saved.amountCents)
            assertEquals(IncomeFrequency.MONTHLY, saved.frequency)
            assertEquals(IncomeBasis.FIXED, saved.basis)
            assertEquals(emptySet<Int>(), saved.skipMonths)
            assertEquals(MANAGED_NET_SALARY, saved.managedKey)
            assertEquals(null, vm.content().editor)
        }

    @Test
    fun saveManagedYearlyLine_ignoresTheEarmark() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(holiday)
            val vm = viewModel()
            collect(vm)
            vm.onEditIncomeLine(MANAGED_HOLIDAY)
            advanceUntilIdle()

            vm.onSaveIncomeLine(
                "Subsidy",
                5,
                IncomeSchedule(frequency = IncomeFrequency.YEARLY, monthOfYear = 9, earmark = Earmark.Group("g")),
                month,
            )
            advanceUntilIdle()

            val saved = incomeRepo.lines.single()
            assertEquals("Subsidy", saved.label)
            assertEquals(null, saved.earmark)
            assertEquals(6, saved.monthOfYear)
            assertEquals(42_000, saved.amountCents)
        }

    @Test
    fun saveManagedLine_blankLabel_isRejected_andKeepsTheLine() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(net)
            val vm = viewModel()
            collect(vm)
            vm.onEditIncomeLine(MANAGED_NET_SALARY)
            advanceUntilIdle()

            vm.onSaveIncomeLine("  ", 86_000, IncomeSchedule(), month)
            advanceUntilIdle()

            assertEquals(listOf(net), incomeRepo.lines)
            assertNotNull(vm.content().editor!!.labelError)
        }
}
