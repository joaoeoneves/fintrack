package com.joaoeoneves.fintrack.data.automation

import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.usecase.AutoPostRecurring
import com.joaoeoneves.fintrack.domain.usecase.HandleRecurringOccurrence
import com.joaoeoneves.fintrack.domain.usecase.RecordPayday
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
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** [AutomationSync]: when a pass runs (session, foreground, data) and when it is not repeated. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutomationSyncTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-20T10:00:00Z"), ZoneId.of("UTC"))

    private fun due() = recurringItem(id = "r1", handledThrough = day(2026, 2, 15), autoPost = true)

    private val income = FakeIncomeRepository()
    private val plan = FakePlannedIncomeRepository(listOf(PlannedIncome("p1", "Salary", 100_000L, 0)))
    private val automation = FakeAutomationSettingsRepository(incomeRepo = income)

    private fun TestScope.sync(
        session: FakeSessionRepository,
        repo: FakeRecurringRepository,
    ) = AutomationSync(
        session,
        AutoPostRecurring(
            repo,
            HandleRecurringOccurrence(repo, FakeCategoryRepository()),
            FakeExpenseRepository(),
            FakeIncomeRepository(),
        ),
        PaydayData(automation, plan, repo),
        RecordPayday(plan, FakeMonthSettingsRepository(), income, automation),
        clock,
        backgroundScope,
    )

    private fun guest() = FakeSessionRepository(initialSession = Session.Guest)

    @Test
    fun guest_runsOnStart() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeRecurringRepository(listOf(due()))

            sync(guest(), repo).start()
            advanceUntilIdle()

            assertEquals(listOf("rec_r1_20260315"), repo.confirmedTransactions.map { it.id })
        }

    @Test
    fun signedOut_doesNothing_untilSessionChanges() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeRecurringRepository(listOf(due()))
            val session = FakeSessionRepository(initialSession = Session.SignedOut)

            sync(session, repo).start()
            advanceUntilIdle()
            assertTrue(repo.confirmedTransactions.isEmpty())

            session.emit(Session.Guest)
            advanceUntilIdle()
            assertEquals(1, repo.confirmedTransactions.size)
        }

    @Test
    fun onForeground_rerunsForNewlyDueWork() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeRecurringRepository(listOf(due()))
            val sync = sync(guest(), repo)
            sync.start()
            advanceUntilIdle()
            repo.nextConfirmError = null

            sync.onForeground()
            advanceUntilIdle()

            // Same attempt: nothing new, and no duplicates either.
            assertEquals(1, repo.confirmedTransactions.size)
        }

    @Test
    fun startTwice_keepsOneCollector() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeRecurringRepository(listOf(due()))
            val sync = sync(guest(), repo)

            sync.start()
            sync.start()
            advanceUntilIdle()

            assertEquals(1, repo.confirmedTransactions.size)
        }

    @Test
    fun identicalFailingAttempt_isNotRetried_onForeground() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeRecurringRepository(listOf(due()), nextConfirmError = IllegalStateException("down"))
            val sync = sync(guest(), repo)
            sync.start()
            advanceUntilIdle()
            assertTrue(repo.confirmedTransactions.isEmpty())

            sync.onForeground()
            advanceUntilIdle()

            // The failure was one-shot; a retry would have posted. Same inputs: not attempted again.
            assertTrue(repo.confirmedTransactions.isEmpty())
        }

    @Test
    fun changedRecurringData_triggersANewAttempt() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeRecurringRepository(listOf(due()), nextConfirmError = IllegalStateException("down"))
            sync(guest(), repo).start()
            advanceUntilIdle()
            assertTrue(repo.confirmedTransactions.isEmpty())

            repo.setItems(listOf(due().copy(handledThrough = day(2026, 1, 15))))
            advanceUntilIdle()

            assertEquals(2, repo.confirmedTransactions.size)
        }

    @Test
    fun payday_notRun_whileDisabled() =
        runTest(UnconfinedTestDispatcher()) {
            val disabled = FakeAutomationSettingsRepository(AutomationSettings(false, YearMonth.of(2026, 1)))
            val repo = FakeRecurringRepository()
            val plain =
                AutomationSync(
                    guest(),
                    AutoPostRecurring(
                        repo,
                        HandleRecurringOccurrence(repo, FakeCategoryRepository()),
                        FakeExpenseRepository(),
                        FakeIncomeRepository(),
                    ),
                    PaydayData(disabled, plan, repo),
                    RecordPayday(plan, FakeMonthSettingsRepository(), income, disabled),
                    clock,
                    backgroundScope,
                )

            plain.start()
            advanceUntilIdle()

            assertTrue(disabled.recorded.isEmpty())
            assertEquals(YearMonth.of(2026, 1), disabled.settings.paydayRecordedThrough)
        }

    @Test
    fun payday_runsWhenEnabled_andNotRepeatedOnForeground() =
        runTest(UnconfinedTestDispatcher()) {
            automation.setPaydayEnabled(true, YearMonth.of(2026, 1))
            val sync = sync(guest(), FakeRecurringRepository())

            sync.start()
            advanceUntilIdle()
            sync.onForeground()
            advanceUntilIdle()

            // February's payday (Fri 27th) has passed; March's (Tue 31st) has not.
            assertEquals(listOf(YearMonth.of(2026, 2)), automation.recorded.map { it.first })
            assertEquals(
                listOf("pay_p1_202602"),
                automation.recorded
                    .single()
                    .second
                    .map { it.id },
            )
            assertEquals(YearMonth.of(2026, 2), automation.settings.paydayRecordedThrough)
        }

    @Test
    fun payday_enablingWhileRunning_triggersAPass() =
        runTest(UnconfinedTestDispatcher()) {
            sync(guest(), FakeRecurringRepository()).start()
            advanceUntilIdle()
            assertTrue(automation.recorded.isEmpty())

            automation.setPaydayEnabled(true, YearMonth.of(2026, 1))
            advanceUntilIdle()

            assertEquals(1, automation.recorded.size)
        }
}
