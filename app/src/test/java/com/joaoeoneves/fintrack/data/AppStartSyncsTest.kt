package com.joaoeoneves.fintrack.data

import android.app.Application
import com.joaoeoneves.fintrack.data.automation.AutomationSync
import com.joaoeoneves.fintrack.data.automation.PaydayData
import com.joaoeoneves.fintrack.data.backup.RestoreGate
import com.joaoeoneves.fintrack.data.preferences.PreferenceField
import com.joaoeoneves.fintrack.data.preferences.PreferencesSync
import com.joaoeoneves.fintrack.data.salary.ManagedIncomeSync
import com.joaoeoneves.fintrack.domain.model.MANAGED_DUODECIMOS
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.salaryProfileLines
import com.joaoeoneves.fintrack.domain.usecase.AutoPostRecurring
import com.joaoeoneves.fintrack.domain.usecase.HandleRecurringOccurrence
import com.joaoeoneves.fintrack.domain.usecase.RecordPayday
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

/** [AppStartSyncs.startAll] starts every sync, and calling it again starts nothing twice. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppStartSyncsTest {
    private val clock = MutableClock(Instant.parse("2026-03-20T10:00:00Z"), ZoneOffset.UTC)
    private val profile = SalaryProfile(baseGrossCents = 100_000, irsBasePpm = 100_000)
    private val own = PlannedIncome("own", "Own", 123, order = 0)
    private val plan =
        FakePlannedIncomeRepository(
            listOf(own) + salaryProfileLines(profile, listOf(own)).filter { it.id != MANAGED_DUODECIMOS },
        )
    private val due = recurringItem("r1", handledThrough = day(2026, 2, 15))
    private val recurring = FakeRecurringRepository(listOf(due))
    private val expenses = FakeExpenseRepository()
    private val startedPreferences = mutableListOf<String>()
    private val preferences =
        object : PreferencesSync {
            override fun start() {
                startedPreferences += "start"
            }

            override fun push(
                field: PreferenceField,
                value: String,
            ) = Unit
        }

    private fun TestScope.syncs(): AppStartSyncs {
        val session = FakeSessionRepository(Session.Guest)
        val gate = RestoreGate()
        val automation = FakeAutomationSettingsRepository()
        return AppStartSyncs(
            preferences,
            ManagedIncomeSync(
                FakeSalaryProfileRepository(profile),
                plan,
                FakeIncomeRepository(),
                FakeSalaryLineLabelsProvider(),
                gate,
                backgroundScope,
            ),
            AutomationSync(
                session,
                AutoPostRecurring(
                    recurring,
                    HandleRecurringOccurrence(recurring, FakeCategoryRepository()),
                    expenses,
                    FakeCategoryRepository(),
                ),
                PaydayData(automation, plan, recurring, gate),
                RecordPayday(
                    plan,
                    FakeMonthSettingsRepository(),
                    FakeIncomeRepository(),
                    automation,
                    FakeSalaryLineLabelsProvider(),
                ),
                clock,
                backgroundScope,
            ),
        )
    }

    @Test
    fun startAll_startsPreferencesManagedIncomeAndAutomation() =
        runTest(UnconfinedTestDispatcher()) {
            syncs().startAll()
            advanceUntilIdle()

            assertEquals(listOf("start"), startedPreferences)
            assertTrue(plan.lines.any { it.id == MANAGED_DUODECIMOS })
            assertTrue(recurring.confirmedTransactions.isNotEmpty())
        }

    @Test
    fun startAll_calledTwice_doesNotRunTheSyncsTwice() =
        runTest(UnconfinedTestDispatcher()) {
            val syncs = syncs()
            syncs.startAll()
            syncs.startAll()
            advanceUntilIdle()

            assertTrue(plan.lines.any { it.id == MANAGED_DUODECIMOS })
            // Each sync guards its own start, so the due item is posted once, not twice.
            assertEquals(1, recurring.confirmedTransactions.size)
        }
}
