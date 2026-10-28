package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.MANAGED_MEAL
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.paydayIncomeId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/** "Received" in [MonthlyPlanViewModel]: [IncomeReceipts] in the state and [MonthlyPlanViewModel.onReceive]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MonthlyPlanViewModelReceiveTest : MonthlyPlanViewModelTestBase() {
    private val oct = YearMonth.of(2026, 10)

    @Test
    fun receipts_carryPaydayAndToday_andNothingReceivedYet() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a"))
            val vm = viewModel()
            collect(vm)

            val receipts = vm.content().receipts

            assertEquals(LocalDate.of(2026, 10, 30), receipts.payday)
            assertEquals(LocalDate.of(2026, 10, 15), receipts.today)
            assertTrue(receipts.canReceive)
            assertTrue(receipts.receivedOn.isEmpty())
        }

    @Test
    fun onReceive_singleLine_recordsIt_andMarksTheRowReceived() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a", 150_000))
            val vm = viewModel()
            collect(vm)
            val row = vm.content().incomeRows.single()

            vm.onReceive(row, LocalDate.of(2026, 10, 10), null)
            advanceUntilIdle()

            val stored = actualIncomeRepo.getAllIncome().getOrThrow().single()
            assertEquals(paydayIncomeId("a", oct), stored.id)
            assertEquals(150_000L, stored.amountCents)
            assertEquals(mapOf("a" to LocalDate.of(2026, 10, 10)), vm.content().receipts.receivedOn)
        }

    @Test
    fun onReceive_amountOverride_isUsedForASingleLine() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a", 150_000))
            val vm = viewModel()
            collect(vm)

            vm.onReceive(vm.content().incomeRows.single(), LocalDate.of(2026, 10, 15), 149_000L)
            advanceUntilIdle()

            assertEquals(
                149_000L,
                actualIncomeRepo
                    .getAllIncome()
                    .getOrThrow()
                    .single()
                    .amountCents,
            )
        }

    @Test
    fun onReceive_multiLineRow_recordsAllLines_keyedByTheFirstLine() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("net1", 100_000, 0).copy(managedKey = MANAGED_NET_SALARY))
            incomeRepo.savePlannedIncome(line("net2", 20_000, 1).copy(managedKey = MANAGED_NET_SALARY))
            incomeRepo.savePlannedIncome(line("meal", 5_000, 2).copy(managedKey = MANAGED_MEAL))
            val vm = viewModel()
            collect(vm)
            val salary = vm.content().incomeRows.first { it.lines.size == 2 }

            assertEquals("net1", salary.key)
            vm.onReceive(salary, LocalDate.of(2026, 10, 15), null)
            advanceUntilIdle()

            assertEquals(
                setOf(paydayIncomeId("net1", oct), paydayIncomeId("net2", oct)),
                actualIncomeRepo
                    .getAllIncome()
                    .getOrThrow()
                    .map { it.id }
                    .toSet(),
            )
            assertEquals(
                setOf("net1"),
                vm
                    .content()
                    .receipts.receivedOn.keys,
            )
        }

    @Test
    fun partlyReceivedRow_isNotMarkedReceived() =
        runTest(testDispatcher) {
            val a = line("a", 100_000, 0).copy(managedKey = MANAGED_NET_SALARY)
            incomeRepo.savePlannedIncome(a)
            incomeRepo.savePlannedIncome(line("b", 20_000, 1).copy(managedKey = MANAGED_NET_SALARY))
            actualIncomeRepo.addIncome(
                com.joaoeoneves.fintrack.domain.model.Income(
                    paydayIncomeId("a", oct),
                    "a",
                    100_000,
                    java.time.Instant.parse("2026-10-10T12:00:00Z"),
                    null,
                ),
            )
            val vm = viewModel()
            collect(vm)

            val receivedOn = vm.content().receipts.receivedOn
            assertTrue(receivedOn.isEmpty())
        }

    @Test
    fun futureDate_isRejected_withAnErrorEvent_andNothingWritten() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a"))
            val vm = viewModel()
            collect(vm)

            vm.onReceive(vm.content().incomeRows.single(), LocalDate.of(2026, 10, 16), null)
            advanceUntilIdle()

            assertTrue(actualIncomeRepo.getAllIncome().getOrThrow().isEmpty())
            vm.errorEvent.first()
        }

    @Test
    fun futureMonthIsClampedToTheCurrentOne_pastMonthIsReceivable() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a"))
            val vm = viewModel()
            collect(vm)

            vm.periodActions.onSelect(Period.Month(YearMonth.of(2026, 12)))
            advanceUntilIdle()
            val future = vm.content()

            vm.periodActions.onSelect(Period.Month(YearMonth.of(2026, 8)))
            advanceUntilIdle()
            val past = vm.content()

            // The month picker clamps a future month to the current one, so nothing future is ever receivable.
            assertEquals(oct, future.workingDays.month)
            assertTrue(future.receipts.canReceive)
            assertTrue(past.receipts.canReceive)
            assertEquals(LocalDate.of(2026, 8, 31), past.receipts.payday)
        }

    @Test
    fun onReceive_beforeTheStateLoaded_isANoOp() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a"))
            val vm = viewModel()
            val row =
                com.joaoeoneves.fintrack.domain.model
                    .IncomeRow(null, listOf(line("a")), 100_000L)

            vm.onReceive(row, LocalDate.of(2026, 10, 15), null)
            advanceUntilIdle()

            assertTrue(actualIncomeRepo.getAllIncome().getOrThrow().isEmpty())
        }
}
