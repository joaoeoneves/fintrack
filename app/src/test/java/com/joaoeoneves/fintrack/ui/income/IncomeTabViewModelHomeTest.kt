package com.joaoeoneves.fintrack.ui.income

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.MonthFigures
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/** The Plan tab's tiles, set-up status and envelope cards, and the month seeded from the route. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class IncomeTabViewModelHomeTest : IncomeTabViewModelTestBase() {
    private suspend fun addEnvelope(
        id: String,
        amountCents: Long,
    ) {
        envelopeRepo.saveEnvelope(EnvelopeEdit(id, id, amountCents, null, emptySet(), emptyMap()))
    }

    @Test
    fun empty_tilesAreZero_configureIsEmpty() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            assertEquals(MonthFigures(0L, 0L, 0L, 0L), vm.content().figures)
            val configure = vm.content().configure
            assertEquals(0L, configure.incomeMonthlyCents)
            assertEquals(0, configure.incomeLineCount)
            assertEquals(0, configure.envelopeCount)
            assertEquals(0, configure.billCount)
            assertEquals(0L, configure.billsMonthlyCents)
            assertTrue(configure.categoryCount > 0)
            assertTrue(vm.content().envelopeCards.isEmpty())
        }

    @Test
    fun tiles_incomeAndFree_followThePlan() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("salary", 200_000L))
            addEnvelope("e1", 30_000L)
            val vm = viewModel()
            collect(vm)

            val content = vm.content()
            assertEquals(200_000L, content.figures.incomeCents)
            assertEquals(170_000L, content.figures.availableCents)
            assertEquals(30_000L, content.figures.envelopesCents)
            assertEquals(0L, content.figures.recurringCents)
        }

    @Test
    fun tiles_billsAreOnlyRecurringSourcedSpending() =
        runTest(testDispatcher) {
            recurringRepo.saveRecurring(recurringItem("rent", amountCents = 50_000L))
            budgetRepo.setBudget(BuiltInCategories.SHOPPING.id, 7_000L)
            val vm = viewModel()
            collect(vm)

            assertEquals(50_000L, vm.content().figures.recurringCents)
        }

    @Test
    fun tiles_savingIsThePlannedSaving() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("salary", 100_000L))
            budgetRepo.setBudget(BuiltInCategories.INVESTMENTS.id, 10_000L)
            val vm = viewModel()
            collect(vm)

            assertEquals(10_000L, vm.content().figures.savingCents)
            assertEquals(10_000L, vm.content().figures.savingCents)
        }

    @Test
    fun configure_billCount_excludesSavingsPlanOwnedItems() =
        runTest(testDispatcher) {
            recurringRepo.saveRecurring(recurringItem("bill", amountCents = 10_000L))
            recurringRepo.saveRecurring(recurringItem("own", amountCents = 50_000L))
            goalRepo.saveGoal(
                SavingsGoal(
                    "plan_own",
                    "Plan",
                    0,
                    GoalTargetType.ANNUAL,
                    LocalDate.of(2026, 1, 1),
                    recurringId = "own",
                ),
            )
            val vm = viewModel()
            collect(vm)

            assertEquals(1, vm.content().configure.billCount)
            assertEquals(10_000L, vm.content().configure.billsMonthlyCents)
        }

    @Test
    fun configure_countsActiveExpenseBills_yearlyAsMonthly() =
        runTest(testDispatcher) {
            recurringRepo.saveRecurring(recurringItem("a", amountCents = 10_000L))
            recurringRepo.saveRecurring(
                recurringItem("b", amountCents = 12_000L, frequency = RecurringFrequency.YEARLY),
            )
            recurringRepo.saveRecurring(recurringItem("off", amountCents = 99_000L, active = false))
            recurringRepo.saveRecurring(recurringItem("inc", type = RecurringType.INCOME, amountCents = 77_000L))
            val vm = viewModel()
            collect(vm)

            assertEquals(2, vm.content().configure.billCount)
            assertEquals(11_000L, vm.content().configure.billsMonthlyCents)
        }

    @Test
    fun configure_countsIncomeLinesAndMonthlyIncome_extrasExcluded() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a", 100_000L))
            incomeRepo.savePlannedIncome(line("b", 20_000L, order = 1))
            addEnvelope("e1", 5_000L)
            val vm = viewModel()
            collect(vm)

            val configure = vm.content().configure
            assertEquals(2, configure.incomeLineCount)
            assertEquals(120_000L, configure.incomeMonthlyCents)
            assertEquals(1, configure.envelopeCount)
        }

    @Test
    fun envelopeCards_mapEnvelopesThenUnenveloped() =
        runTest(testDispatcher) {
            addEnvelope("e1", 5_000L)
            budgetRepo.setBudget(BuiltInCategories.SHOPPING.id, 3_000L)
            val vm = viewModel()
            collect(vm)

            val cards = vm.content().envelopeCards
            assertEquals(listOf<String?>("e1", null), cards.map { it.id })
            assertTrue(cards.first() is EnvelopeCardUi.Budgeted)
            assertEquals(3_000L, (cards.last() as EnvelopeCardUi.Budgeted).amountCents)
        }

    @Test
    fun billsPlanned_unenvelopedCardIsBudgeted() =
        runTest(testDispatcher) {
            recurringRepo.saveRecurring(recurringItem("rent", amountCents = 50_000L))
            val vm = viewModel()
            collect(vm)

            val card = vm.content().envelopeCards.single()
            assertEquals(50_000L, (card as EnvelopeCardUi.Budgeted).amountCents)
        }

    @Test
    fun noRouteMonth_seedsCurrentMonth() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            assertEquals(Period.CurrentMonth, vm.content().period)
        }

    @Test
    fun routeMonth_seedsThatMonth() =
        runTest(testDispatcher) {
            val handle = SavedStateHandle(mapOf("month" to Period.Month(YearMonth.of(2026, 7)).encode()))
            val vm = viewModel(handle)
            collect(vm)

            assertEquals(Period.Month(YearMonth.of(2026, 7)), vm.content().period)
            assertTrue(vm.content().canGoToNextMonth)
        }
}
