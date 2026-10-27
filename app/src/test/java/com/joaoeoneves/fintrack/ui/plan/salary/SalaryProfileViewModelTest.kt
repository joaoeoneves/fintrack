package com.joaoeoneves.fintrack.ui.plan.salary

import android.app.Application
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
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

    private fun viewModel() = SalaryProfileViewModel(profiles, ManageSalaryProfile(profiles, income), clock)

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

            assertEquals("1000.00", vm.content().form.base)
            assertEquals("10", vm.content().form.irsBase)
            assertTrue(vm.content().hasSavedProfile)
            assertTrue(vm.content().canSave)
            assertNotNull(vm.content().breakdown)
        }

    @Test
    fun formChange_updatesBreakdownAndCanSave() =
        runTest(dispatcher) {
            val vm = started(viewModel())

            vm.onFormChange(SalaryForm(base = "1000", irsBase = "10", irsSubsidies = "5"))
            advanceUntilIdle()

            assertTrue(vm.content().canSave)
            assertEquals(86_000L, vm.content().breakdown?.netMonthlyCents)
        }

    @Test
    fun invalidField_blocksSaveAndHidesBreakdown() =
        runTest(dispatcher) {
            val vm = started(viewModel())

            vm.onFormChange(SalaryForm(base = "1000", irsBase = "abc"))
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
            vm.onFormChange(SalaryForm(base = "1000", spreadPpm = SPREAD_FULL_PPM))
            advanceUntilIdle()

            vm.onSave(labels)
            advanceUntilIdle()

            assertEquals(100_000L, profiles.profile?.baseGrossCents)
            assertEquals(listOf(MANAGED_NET_SALARY), income.lines.map { it.id })
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
            vm.onFormChange(SalaryForm(base = "1000"))
            advanceUntilIdle()
            profiles.nextSaveError = IllegalStateException("boom")

            vm.onSave(labels)
            advanceUntilIdle()

            assertEquals(SalaryProfileEvent.Failed, vm.eventFlow.first())
            assertEquals("1000", vm.content().form.base)
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
            vm.onFormChange(SalaryForm(base = "5"))
            advanceUntilIdle()

            vm.start(',')
            advanceUntilIdle()

            assertEquals("5", vm.content().form.base)
        }

    @Test
    fun content_exposesTheClockMonth() =
        runTest(dispatcher) {
            val vm = started(viewModel())

            assertEquals(java.time.YearMonth.of(2026, 10), vm.content().month)
        }
}
