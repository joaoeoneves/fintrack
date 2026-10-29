package com.joaoeoneves.fintrack.ui.plan.salary.setup

import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.domain.model.MANAGED_DUODECIMOS
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SPREAD_FULL_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.salaryBreakdownOf
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryForm
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryProfileUiState
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryProfileViewModel
import com.joaoeoneves.fintrack.ui.plan.salary.toResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saving from the wizard: the profile written, the duplicate-line prompt and failures. */
@OptIn(ExperimentalCoroutinesApi::class)
class SalarySetupViewModelSaveTest : SalarySetupViewModelTestBase() {
    private val manual = PlannedIncome("m1", "Side gig", 5_000L, 0)

    private suspend fun kotlinx.coroutines.test.TestScope.filled(
        form: SalaryForm,
        extraLines: List<PlannedIncome> = emptyList(),
    ): Pair<SalarySetupViewModel, MutableList<SalarySetupEvent>> {
        extraLines.forEach { income.savePlannedIncome(it) }
        val vm = viewModel()
        val events = started(vm)
        vm.onFormChange(form)
        advanceUntilIdle()
        return vm to events
    }

    @Test
    fun save_writesTheProfileAndLines_thenDone() =
        runTest(dispatcher) {
            val (vm, events) = filled(SalaryForm(base = 100_000L, spreadPpm = SPREAD_FULL_PPM))

            vm.onSave(labels)
            advanceUntilIdle()

            assertEquals(100_000L, profiles.profile?.baseGrossCents)
            assertEquals(listOf(MANAGED_NET_SALARY, MANAGED_DUODECIMOS), income.lines.map { it.id })
            assertEquals(listOf<SalarySetupEvent>(SalarySetupEvent.Done), events)
            assertFalse(vm.uiState.value.saving)
            assertTrue(vm.duplicates.value.isEmpty())
        }

    @Test
    fun save_withoutAmountOrWithInvalidField_doesNothing() =
        runTest(dispatcher) {
            val (empty, emptyEvents) = filled(SalaryForm())
            empty.onSave(labels)
            advanceUntilIdle()
            assertNull(profiles.profile)

            empty.onFormChange(SalaryForm(base = 100_000L, ss = "x"))
            advanceUntilIdle()
            empty.onSave(labels)
            advanceUntilIdle()

            assertNull(profiles.profile)
            assertTrue(income.lines.isEmpty())
            assertTrue(emptyEvents.isEmpty())
        }

    @Test
    fun save_failure_emitsFailed_withoutDuplicatesOrDone() =
        runTest(dispatcher) {
            val (vm, events) = filled(SalaryForm(base = 100_000L), extraLines = listOf(manual))
            profiles.nextSaveError = IllegalStateException("boom")

            vm.onSave(labels)
            advanceUntilIdle()

            assertEquals(listOf<SalarySetupEvent>(SalarySetupEvent.Failed), events)
            assertTrue(vm.duplicates.value.isEmpty())
            assertFalse(vm.uiState.value.saving)
            assertEquals(100_000L, vm.uiState.value.form.base)
        }

    @Test
    fun save_withManualMonthlyLines_offersThem_insteadOfFinishing() =
        runTest(dispatcher) {
            val (vm, events) = filled(SalaryForm(base = 100_000L), extraLines = listOf(manual))

            vm.onSave(labels)
            advanceUntilIdle()

            assertEquals(listOf("m1"), vm.duplicates.value.map { it.id })
            assertTrue(events.isEmpty())
        }

    @Test
    fun removeDuplicates_deletesTheTickedOnes_thenDone() =
        runTest(dispatcher) {
            val other = manual.copy(id = "m2", label = "Rent share")
            val (vm, events) = filled(SalaryForm(base = 100_000L), extraLines = listOf(manual, other))
            vm.onSave(labels)
            advanceUntilIdle()

            vm.onRemoveDuplicates(setOf("m1"))
            advanceUntilIdle()

            assertFalse(income.lines.any { it.id == "m1" })
            assertTrue(income.lines.any { it.id == "m2" })
            assertTrue(vm.duplicates.value.isEmpty())
            assertEquals(listOf<SalarySetupEvent>(SalarySetupEvent.Done), events)
        }

    @Test
    fun removeDuplicates_failure_emitsFailed() =
        runTest(dispatcher) {
            val (vm, events) = filled(SalaryForm(base = 100_000L), extraLines = listOf(manual))
            vm.onSave(labels)
            advanceUntilIdle()
            income.nextDeleteError = IllegalStateException("boom")

            vm.onRemoveDuplicates(setOf("m1"))
            advanceUntilIdle()

            assertEquals(listOf<SalarySetupEvent>(SalarySetupEvent.Failed), events)
            assertTrue(vm.duplicates.value.isEmpty())
        }

    @Test
    fun keepDuplicates_keepsEveryLine_thenDone() =
        runTest(dispatcher) {
            val (vm, events) = filled(SalaryForm(base = 100_000L), extraLines = listOf(manual))
            vm.onSave(labels)
            advanceUntilIdle()

            vm.onKeepDuplicates()
            advanceUntilIdle()

            assertTrue(income.lines.any { it.id == "m1" })
            assertTrue(vm.duplicates.value.isEmpty())
            assertEquals(listOf<SalarySetupEvent>(SalarySetupEvent.Done), events)
        }

    @Test
    fun netTwelfths_savesNullLumps() =
        runTest(dispatcher) {
            val (vm, _) =
                filled(
                    netForm(),
                )
            vm.onNetLumpsChange(false)
            advanceUntilIdle()

            vm.onSave(labels)
            advanceUntilIdle()

            val profile = profiles.profile
            assertEquals(SalaryInputMode.NET, profile?.inputMode)
            assertEquals(90_000L, profile?.netMonthlyCents)
            assertNull(profile?.netHolidayLumpCents)
            assertNull(profile?.netChristmasLumpCents)
        }

    @Test
    fun netLumpSums_areKept() =
        runTest(dispatcher) {
            val (vm, _) =
                filled(
                    netForm(),
                )
            vm.onNetLumpsChange(true)
            advanceUntilIdle()

            vm.onSave(labels)
            advanceUntilIdle()

            assertEquals(50_000L, profiles.profile?.netHolidayLumpCents)
            assertEquals(60_000L, profiles.profile?.netChristmasLumpCents)
        }

    @Test
    fun meal_producesTheDailyRate_andBlankLeavesItUnset() =
        runTest(dispatcher) {
            val (withMeal, _) = filled(SalaryForm(base = 100_000L, meal = 960L))
            withMeal.onSave(labels)
            advanceUntilIdle()
            assertEquals(960L, profiles.profile?.mealRatePerDayCents)

            val otherProfiles = FakeSalaryProfileRepository()
            val noMeal = viewModel(profileRepo = otherProfiles, incomeRepo = FakePlannedIncomeRepository())
            started(noMeal)
            noMeal.onFormChange(SalaryForm(base = 100_000L))
            advanceUntilIdle()
            noMeal.onSave(labels)
            advanceUntilIdle()
            assertNull(otherProfiles.profile?.mealRatePerDayCents)
        }

    @Test
    fun grossWizardInput_matchesTheFullForm_forAnEquivalentSalaryForm() =
        runTest(dispatcher) {
            val form =
                SalaryForm(
                    base = 250_000L,
                    spreadPpm = SPREAD_FULL_PPM,
                    irsBase = "15",
                    meal = 900L,
                    subsidyMonths = listOf(6, 12),
                )
            val (wizard, _) = filled(form)
            val fullProfiles = FakeSalaryProfileRepository()
            val fullIncome = FakePlannedIncomeRepository()
            val full =
                SalaryProfileViewModel(
                    fullProfiles,
                    com.joaoeoneves.fintrack.domain.usecase
                        .ManageSalaryProfile(fullProfiles, fullIncome),
                    fullIncome,
                    clock,
                    idGenerator = { "id1" },
                )
            backgroundScope.launch { full.uiState.collect {} }
            full.start('.')
            advanceUntilIdle()
            full.onFormChange(form)
            advanceUntilIdle()

            wizard.onSave(labels)
            full.onSave(labels)
            advanceUntilIdle()

            assertNotNull(profiles.profile)
            assertEquals(fullProfiles.profile, profiles.profile)
            assertEquals(form.toResult().profile, profiles.profile)
            assertEquals(fullIncome.lines, income.lines)
            assertEquals(
                (full.uiState.value as SalaryProfileUiState.Content).breakdown,
                wizard.uiState.value.breakdown,
            )
            assertEquals(salaryBreakdownOf(form.toResult().profile), wizard.uiState.value.breakdown)
        }
}
