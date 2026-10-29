package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/** The payday header (common pay date or none) and the "month" route argument of [MonthlyPlanViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MonthlyPlanViewModelPaydayTest : MonthlyPlanViewModelTestBase() {
    private fun fixed(
        id: String,
        day: Int,
    ) = PlannedIncome(id = id, label = id, amountCents = 100_000L, order = 0, payDay = day)

    @Test
    fun linesPayingOnTheSameDay_giveACommonPayday() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(fixed("a", 10))
            incomeRepo.savePlannedIncome(fixed("b", 10))
            val vm = viewModel()
            collect(vm)

            assertEquals(LocalDate.of(2026, 10, 10), vm.content().receipts.payday)
        }

    @Test
    fun linesPayingOnDifferentDays_paydayIsTheLatest() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(fixed("a", 5))
            incomeRepo.savePlannedIncome(fixed("b", 20))
            val vm = viewModel()
            collect(vm)

            assertEquals(LocalDate.of(2026, 10, 20), vm.content().receipts.payday)
        }

    @Test
    fun monthRouteArgument_opensThePlanOnThatMonth() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(fixed("a", 10))
            val vm = viewModel(SavedStateHandle(mapOf("month" to "month:2026-08")))
            collect(vm)

            assertEquals(YearMonth.of(2026, 8), vm.content().workingDays.month)
            assertEquals(LocalDate.of(2026, 8, 10), vm.content().receipts.payday)
        }

    @Test
    fun noMonthArgument_opensOnTheCurrentMonth() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            assertEquals(YearMonth.of(2026, 10), vm.content().workingDays.month)
        }
}
