package com.joaoeoneves.fintrack.ui.plan.salary

import android.app.Application
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.domain.model.AmountVersion
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.SPREAD_FULL_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.repository.PlanSettingsRepository
import com.joaoeoneves.fintrack.domain.usecase.ManageSalaryProfile
import com.joaoeoneves.fintrack.testutil.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

/** The advanced editor's "Valid from" month: default, visibility, shift hint and what a raise writes. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SalaryProfileViewModelEffectiveFromTest {
    private val dispatcher = StandardTestDispatcher()
    private val profiles = FakeSalaryProfileRepository()
    private val income = FakePlannedIncomeRepository()
    private val clock = MutableClock(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    private val labels = SalaryLineLabels()

    private class Shift(
        private val value: Boolean?,
    ) : PlanSettingsRepository {
        override fun observeSalaryPaysNextMonth(): Flow<Boolean?> = flowOf(value)

        override suspend fun setSalaryPaysNextMonth(enabled: Boolean) = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.started(shift: Boolean? = null): SalaryProfileViewModel {
        val vm =
            SalaryProfileViewModel(
                profiles,
                ManageSalaryProfile(profiles, income),
                income,
                clock,
                idGenerator = { "id1" },
                planSettings = Shift(shift),
            )
        backgroundScope.launch { vm.uiState.collect {} }
        vm.start('.')
        advanceUntilIdle()
        return vm
    }

    private fun SalaryProfileViewModel.content() = uiState.value as SalaryProfileUiState.Content

    @Test
    fun default_isTheCurrentMonth_andShownForANewProfile() =
        runTest(dispatcher) {
            val vm = started()
            assertEquals(YearMonth.of(2026, 10), vm.content().effectiveFrom)
            assertTrue(vm.content().showEffectiveFrom)
        }

    @Test
    fun shiftSource_isThePreviousMonthWhenOnOrUnset_nullWhenOff() =
        runTest(dispatcher) {
            assertEquals(YearMonth.of(2026, 9), started(null).content().shiftSource)
            assertEquals(YearMonth.of(2026, 9), started(true).content().shiftSource)
            assertNull(started(false).content().shiftSource)
        }

    @Test
    fun savedProfile_unchanged_hidesTheRow_untilTheFormChanges() =
        runTest(dispatcher) {
            val first = started()
            first.onFormChange(SalaryForm(base = 100_000L, spreadPpm = SPREAD_FULL_PPM))
            first.onSave(labels)
            advanceUntilIdle()

            val vm = started()
            assertFalse(vm.content().showEffectiveFrom)
            vm.onFormChange(vm.content().form.copy(base = 120_000L))
            advanceUntilIdle()
            assertTrue(vm.content().showEffectiveFrom)
        }

    @Test
    fun raise_isVersionedFromTheChosenMonth() =
        runTest(dispatcher) {
            val first = started()
            first.onFormChange(SalaryForm(base = 100_000L, spreadPpm = SPREAD_FULL_PPM))
            first.onEffectiveFromChange(YearMonth.of(2026, 1))
            first.onSave(labels)
            advanceUntilIdle()
            val before = income.lines.single { it.id == MANAGED_NET_SALARY }
            assertEquals(YearMonth.of(2026, 1), before.startMonth)

            val vm = started()
            vm.onFormChange(vm.content().form.copy(base = 120_000L))
            vm.onEffectiveFromChange(YearMonth.of(2027, 1))
            vm.onSave(labels)
            advanceUntilIdle()

            val after = income.lines.single { it.id == MANAGED_NET_SALARY }
            assertEquals(YearMonth.of(2026, 1), after.startMonth)
            assertEquals(YearMonth.of(2027, 1), after.versions.last().from)
            assertEquals(AmountVersion(YearMonth.of(2026, 1), before.amountCents), after.versions.first())
            assertTrue(after.amountCents > before.amountCents)
        }
}
