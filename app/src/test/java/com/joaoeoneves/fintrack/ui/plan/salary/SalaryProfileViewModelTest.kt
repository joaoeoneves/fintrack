package com.joaoeoneves.fintrack.ui.plan.salary

import android.app.Application
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.domain.model.Earmark
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.MANAGED_DUODECIMOS
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SPREAD_FULL_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.usecase.ManageSalaryProfile
import com.joaoeoneves.fintrack.testutil.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SalaryProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val profiles = FakeSalaryProfileRepository()
    private val income = FakePlannedIncomeRepository()
    private val clock = MutableClock(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    private val labels = SalaryLineLabels()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val manage get() = ManageSalaryProfile(profiles, income)

    private fun viewModel() = SalaryProfileViewModel(profiles, manage, income, clock, idGenerator = { "id1" })

    private fun TestScope.started(vm: SalaryProfileViewModel): SalaryProfileViewModel {
        backgroundScope.launch { vm.uiState.collect {} }
        vm.start('.')
        advanceUntilIdle()
        return vm
    }

    private fun SalaryProfileViewModel.content() = uiState.value as SalaryProfileUiState.Content

    @Test
    fun beforeStart_isLoading() =
        runTest(dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(SalaryProfileUiState.Loading, vm.uiState.value)
        }

    @Test
    fun start_withoutProfile_showsEmptyForm_cannotSave() =
        runTest(dispatcher) {
            val vm = started(viewModel())

            assertEquals(SalaryForm(), vm.content().form)
            assertFalse(vm.content().hasSavedProfile)
            assertFalse(vm.content().canSave)
            assertNull(vm.content().breakdown)
        }

    @Test
    fun start_withProfile_loadsItIntoTheForm() =
        runTest(dispatcher) {
            profiles.saveSalaryProfile(SalaryProfile(baseGrossCents = 100_000, irsBasePpm = 100_000))

            val vm = started(viewModel())

            assertEquals(100_000L, vm.content().form.base)
            assertEquals("10", vm.content().form.irsBase)
            assertTrue(vm.content().hasSavedProfile)
            assertTrue(vm.content().canSave)
            assertNotNull(vm.content().breakdown)
        }

    @Test
    fun formChange_updatesBreakdownAndCanSave() =
        runTest(dispatcher) {
            val vm = started(viewModel())

            vm.onFormChange(SalaryForm(base = 100_000L, irsBase = "10", irsSubsidies = "5"))
            advanceUntilIdle()

            assertTrue(vm.content().canSave)
            assertEquals(86_000L, vm.content().breakdown?.netMonthlyCents)
        }

    @Test
    fun invalidField_blocksSaveAndHidesBreakdown() =
        runTest(dispatcher) {
            val vm = started(viewModel())

            vm.onFormChange(SalaryForm(base = 100_000L, irsBase = "abc"))
            advanceUntilIdle()
            vm.onSave(labels)
            advanceUntilIdle()

            assertEquals(setOf(SalaryField.IRS_BASE), vm.content().invalid)
            assertFalse(vm.content().canSave)
            assertNull(vm.content().breakdown)
            assertNull(profiles.profile)
        }

    @Test
    fun save_writesProfileAndLines_andEmitsDone() =
        runTest(dispatcher) {
            val vm = started(viewModel())
            vm.onFormChange(SalaryForm(base = 100_000L, spreadPpm = SPREAD_FULL_PPM))
            advanceUntilIdle()

            vm.onSave(labels)
            advanceUntilIdle()

            assertEquals(100_000L, profiles.profile?.baseGrossCents)
            assertEquals(listOf(MANAGED_NET_SALARY, MANAGED_DUODECIMOS), income.lines.map { it.id })
            assertEquals(SalaryProfileEvent.Done, vm.eventFlow.first())
        }

    @Test
    fun save_withoutRequiredAmount_doesNothing() =
        runTest(dispatcher) {
            val vm = started(viewModel())

            vm.onSave(labels)
            advanceUntilIdle()

            assertNull(profiles.profile)
            assertTrue(income.lines.isEmpty())
        }

    @Test
    fun save_failure_emitsFailed_andKeepsTheForm() =
        runTest(dispatcher) {
            val vm = started(viewModel())
            vm.onFormChange(SalaryForm(base = 100_000L))
            advanceUntilIdle()
            profiles.nextSaveError = IllegalStateException("boom")

            vm.onSave(labels)
            advanceUntilIdle()

            assertEquals(SalaryProfileEvent.Failed, vm.eventFlow.first())
            assertEquals(100_000L, vm.content().form.base)
            assertTrue(vm.content().canSave)
        }

    @Test
    fun delete_removesProfileAndManagedLines() =
        runTest(dispatcher) {
            profiles.saveSalaryProfile(SalaryProfile(baseGrossCents = 100_000, subsidySpreadPpm = SPREAD_FULL_PPM))
            val vm = started(viewModel())
            vm.onSave(labels)
            advanceUntilIdle()
            vm.eventFlow.first()

            vm.onDelete()
            advanceUntilIdle()

            assertNull(profiles.profile)
            assertTrue(income.lines.isEmpty())
            assertEquals(SalaryProfileEvent.Done, vm.eventFlow.first())
        }

    @Test
    fun delete_failure_emitsFailed() =
        runTest(dispatcher) {
            profiles.saveSalaryProfile(SalaryProfile(baseGrossCents = 100_000))
            val vm = started(viewModel())
            profiles.nextDeleteError = IllegalStateException("boom")

            vm.onDelete()
            advanceUntilIdle()

            assertEquals(SalaryProfileEvent.Failed, vm.eventFlow.first())
            assertNotNull(profiles.profile)
        }

    @Test
    fun start_isOnlyAppliedOnce() =
        runTest(dispatcher) {
            val vm = started(viewModel())
            vm.onFormChange(SalaryForm(base = 500L))
            advanceUntilIdle()

            vm.start(',')
            advanceUntilIdle()

            assertEquals(500L, vm.content().form.base)
        }

    @Test
    fun content_exposesTheClockMonth() =
        runTest(dispatcher) {
            val vm = started(viewModel())

            assertEquals(java.time.YearMonth.of(2026, 10), vm.content().month)
        }

    private val manual = PlannedIncome("m1", "Side gig", 5_000, 0)

    private suspend fun TestScope.saveWith(vararg existing: PlannedIncome): SalaryProfileViewModel {
        existing.forEach { income.savePlannedIncome(it) }
        val vm = started(viewModel())
        vm.onFormChange(SalaryForm(base = 100_000L, spreadPpm = SPREAD_FULL_PPM))
        advanceUntilIdle()
        vm.onSave(labels)
        advanceUntilIdle()
        return vm
    }

    private suspend fun SalaryProfileViewModel.nextEventOrNull() = withTimeoutOrNull(1) { eventFlow.first() }

    @Test
    fun save_noManualMonthlyLines_emitsDone_noDuplicates() =
        runTest(dispatcher) {
            val vm = saveWith()

            assertTrue(vm.duplicates.value.isEmpty())
            assertEquals(SalaryProfileEvent.Done, vm.eventFlow.first())
        }

    @Test
    fun save_ignoresEarmarkedYearlyOnceAndManagedLines() =
        runTest(dispatcher) {
            val vm =
                saveWith(
                    manual.copy(id = "e", earmark = Earmark.Group("g")),
                    manual.copy(id = "y", frequency = IncomeFrequency.YEARLY, monthOfYear = 5),
                    manual.copy(id = "o", frequency = IncomeFrequency.ONCE, yearMonth = YearMonth.of(2026, 12)),
                )

            assertTrue(vm.duplicates.value.isEmpty())
            assertEquals(SalaryProfileEvent.Done, vm.eventFlow.first())
        }

    @Test
    fun save_withManualMonthlyLine_setsDuplicates_andHoldsBackDone() =
        runTest(dispatcher) {
            val vm = saveWith(manual)

            assertEquals(listOf("m1"), vm.duplicates.value.map { it.id })
            assertNull(vm.nextEventOrNull())
        }

    @Test
    fun removeDuplicates_deletesThem_thenDone() =
        runTest(dispatcher) {
            val vm = saveWith(manual, manual.copy(id = "m2"))

            vm.onRemoveDuplicates(setOf("m1"))
            advanceUntilIdle()

            assertFalse(income.lines.any { it.id == "m1" })
            assertTrue(income.lines.any { it.id == "m2" })
            assertTrue(vm.duplicates.value.isEmpty())
            assertEquals(SalaryProfileEvent.Done, vm.eventFlow.first())
        }

    @Test
    fun keepDuplicates_keepsLines_thenDone() =
        runTest(dispatcher) {
            val vm = saveWith(manual)

            vm.onKeepDuplicates()
            advanceUntilIdle()

            assertTrue(income.lines.any { it.id == "m1" })
            assertTrue(vm.duplicates.value.isEmpty())
            assertEquals(SalaryProfileEvent.Done, vm.eventFlow.first())
        }

    @Test
    fun removeDuplicates_deleteFailure_emitsFailed() =
        runTest(dispatcher) {
            val vm = saveWith(manual)
            income.nextDeleteError = IllegalStateException("boom")

            vm.onRemoveDuplicates(setOf("m1"))
            advanceUntilIdle()

            assertEquals(SalaryProfileEvent.Failed, vm.eventFlow.first())
            assertTrue(vm.duplicates.value.isEmpty())
        }
}
