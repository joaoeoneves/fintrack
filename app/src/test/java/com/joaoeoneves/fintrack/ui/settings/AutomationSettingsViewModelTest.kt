package com.joaoeoneves.fintrack.ui.settings

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class AutomationSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeAutomationSettingsRepository()
    private val recurring = FakeRecurringRepository()
    private val clock = MutableClock(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.started(): AutomationSettingsViewModel {
        val vm = AutomationSettingsViewModel(settings, recurring, clock)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    @Test
    fun initialState_isOff_withNoRecurringIncome() =
        runTest(dispatcher) {
            val vm = started()

            assertEquals(AutomationUiState(paydayEnabled = false, hasRecurringIncome = false), vm.uiState.value)
        }

    @Test
    fun enabling_beforePayday_startsFromLastMonth() =
        runTest(dispatcher) {
            val vm = started()

            vm.onPaydayToggled(true)
            advanceUntilIdle()

            // Payday of October 2026 is Fri 30; on the 15th the cursor is September.
            assertEquals(AutomationSettings(true, YearMonth.of(2026, 9)), settings.settings)
            assertTrue(vm.uiState.value.paydayEnabled)
        }

    @Test
    fun enabling_onPayday_startsFromThisMonth_soItIsNotBackfilled() =
        runTest(dispatcher) {
            clock.now = Instant.parse("2026-10-30T09:00:00Z")
            val vm = started()

            vm.onPaydayToggled(true)
            advanceUntilIdle()

            assertEquals(YearMonth.of(2026, 10), settings.settings.paydayRecordedThrough)
        }

    @Test
    fun disabling_keepsTheCursor() =
        runTest(dispatcher) {
            val vm = started()
            vm.onPaydayToggled(true)
            advanceUntilIdle()

            vm.onPaydayToggled(false)
            advanceUntilIdle()

            assertEquals(AutomationSettings(false, YearMonth.of(2026, 9)), settings.settings)
            assertFalse(vm.uiState.value.paydayEnabled)
        }

    @Test
    fun reEnabling_resetsTheCursorToToday() =
        runTest(dispatcher) {
            settings.replaceSettings(AutomationSettings(false, YearMonth.of(2026, 3)))
            val vm = started()

            vm.onPaydayToggled(true)
            advanceUntilIdle()

            assertEquals(YearMonth.of(2026, 9), settings.settings.paydayRecordedThrough)
        }

    @Test
    fun failedSave_doesNotCrash_andStateStaysOff() =
        runTest(dispatcher) {
            settings.nextWriteError = IllegalStateException("offline")
            val vm = started()

            vm.onPaydayToggled(true)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.paydayEnabled)
        }

    @Test
    fun activeRecurringIncome_raisesTheHint() =
        runTest(dispatcher) {
            recurring.setItems(listOf(recurringItem("sal", type = RecurringType.INCOME)))

            assertTrue(started().uiState.value.hasRecurringIncome)
        }

    @Test
    fun inactiveIncome_andExpenses_doNotRaiseTheHint() =
        runTest(dispatcher) {
            recurring.setItems(
                listOf(
                    recurringItem("old", type = RecurringType.INCOME, active = false),
                    recurringItem("rent", type = RecurringType.EXPENSE),
                ),
            )

            assertFalse(started().uiState.value.hasRecurringIncome)
        }
}
