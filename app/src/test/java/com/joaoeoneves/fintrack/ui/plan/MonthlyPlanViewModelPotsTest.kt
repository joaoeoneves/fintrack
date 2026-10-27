package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.Earmark
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** Earmarks on income lines in [MonthlyPlanViewModel]: saving through [IncomeSchedule], editor and pots state. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MonthlyPlanViewModelPotsTest : MonthlyPlanViewModelTestBase() {
    private val oct = YearMonth.of(2026, 10)
    private val food = Earmark.Category("FOOD")

    private suspend fun TestScope.save(
        vm: MonthlyPlanViewModel,
        schedule: IncomeSchedule,
    ): PlannedIncome {
        vm.onAddIncomeLine()
        advanceUntilIdle()
        vm.onSaveIncomeLine("Meal", 10_000L, schedule, oct)
        advanceUntilIdle()
        return incomeRepo.lines.last()
    }

    @Test
    fun save_monthlyWithEarmark_persistsIt() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            val saved = save(vm, IncomeSchedule(earmark = food))

            assertEquals(food, saved.earmark)
            assertEquals(
                food,
                vm
                    .content()
                    .incomeLines
                    .last()
                    .earmark,
            )
        }

    @Test
    fun save_withoutEarmark_isNone() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            assertNull(save(vm, IncomeSchedule()).earmark)
        }

    @Test
    fun save_yearlyAndOnce_dropTheEarmark() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            val yearly = save(vm, IncomeSchedule(frequency = IncomeFrequency.YEARLY, monthOfYear = 6, earmark = food))
            val once = save(vm, IncomeSchedule(frequency = IncomeFrequency.ONCE, yearMonth = oct, earmark = food))

            assertNull(yearly.earmark)
            assertNull(once.earmark)
        }

    @Test
    fun edit_prefillsEarmark_andResavingKeepsIt() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a").copy(earmark = Earmark.Group("g1")))
            val vm = viewModel()
            collect(vm)

            vm.onEditIncomeLine("a")
            advanceUntilIdle()
            val schedule = vm.content().editor!!.initialSchedule
            assertEquals(Earmark.Group("g1"), schedule.earmark)

            vm.onSaveIncomeLine("a", 100_000L, schedule, oct)
            advanceUntilIdle()
            assertEquals(Earmark.Group("g1"), incomeRepo.lines.single { it.id == "a" }.earmark)
        }

    @Test
    fun edit_clearingEarmark_removesIt() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a").copy(earmark = food))
            val vm = viewModel()
            collect(vm)
            vm.onEditIncomeLine("a")
            advanceUntilIdle()

            vm.onSaveIncomeLine("a", 100_000L, IncomeSchedule(earmark = null), oct)
            advanceUntilIdle()

            assertNull(incomeRepo.lines.single { it.id == "a" }.earmark)
        }

    @Test
    fun editor_offersActiveNamedCategories() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            vm.onAddIncomeLine()
            advanceUntilIdle()

            val options = vm.content().editor!!.earmarkOptions
            assertTrue(options.categories.isNotEmpty())
            assertTrue(options.categories.none { it.archived || it.isUnknown })
            assertTrue(options.groups.isEmpty())
        }

    @Test
    fun pots_emptyWithoutEarmarks_andFilledWithOne() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("salary", 100_000L))
            val vm = viewModel()
            collect(vm)
            assertNotNull(vm.content().pots)
            assertTrue(
                vm
                    .content()
                    .pots!!
                    .pots
                    .isEmpty(),
            )

            incomeRepo.savePlannedIncome(line("meal", 10_000L, order = 1).copy(earmark = food))
            advanceUntilIdle()

            val pots = vm.content().pots!!
            assertEquals(food, pots.pots.single().target)
            assertEquals(10_000L, pots.pots.single().expectedIncomeCents)
            assertEquals(100_000L, pots.base.expectedIncomeCents)
        }
}
