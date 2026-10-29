package com.joaoeoneves.fintrack.ui.income

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.paydayIncomeId
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.ZoneOffset

/** Receiving (and undoing) a PAST month's income from its own page records it dated in that month. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class IncomeTabViewModelPastReceiveTest : IncomeTabViewModelTestBase() {
    private val sep = YearMonth.of(2026, 9)

    private fun september() = viewModel(SavedStateHandle(mapOf("period" to Period.Month(sep).encode())))

    @Test
    fun pastMonth_canReceive() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a"))
            val vm = september()
            collect(vm)

            assertTrue(
                vm
                    .content()
                    .receipts.canReceive
                    .isNotEmpty(),
            )
            assertEquals(LocalDate.of(2026, 9, 30), vm.content().receipts.payday)
        }

    @Test
    fun onReceive_pastMonth_recordsTheIncomeDatedInThatMonth() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a", 150_000))
            val vm = september()
            collect(vm)

            vm.onReceive(vm.content().incomeRows.single(), LocalDate.of(2026, 9, 30), null)
            advanceUntilIdle()

            val stored = actualIncomeRepo.getAllIncome().getOrThrow().single()
            assertEquals(paydayIncomeId("a", sep), stored.id)
            assertEquals(150_000L, stored.amountCents)
            assertEquals(LocalDate.of(2026, 9, 30), stored.date.atZone(ZoneOffset.UTC).toLocalDate())
            assertEquals(mapOf("a" to LocalDate.of(2026, 9, 30)), vm.content().receipts.receivedOn)
        }

    @Test
    fun onUndoReceive_pastMonth_removesIt() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a", 150_000))
            val vm = september()
            collect(vm)
            val row = vm.content().incomeRows.single()
            vm.onReceive(row, LocalDate.of(2026, 9, 12), null)
            advanceUntilIdle()
            assertEquals(1, actualIncomeRepo.getAllIncome().getOrThrow().size)

            vm.onUndoReceive(row)
            advanceUntilIdle()

            assertTrue(actualIncomeRepo.getAllIncome().getOrThrow().isEmpty())
            assertTrue(
                vm
                    .content()
                    .receipts.receivedOn
                    .isEmpty(),
            )
        }
}
