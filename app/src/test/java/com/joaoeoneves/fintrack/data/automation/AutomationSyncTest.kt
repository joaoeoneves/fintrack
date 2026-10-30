package com.joaoeoneves.fintrack.data.automation

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSalaryLineLabelsProvider
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.data.backup.RestoreGate
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.usecase.AutoPostRecurring
import com.joaoeoneves.fintrack.domain.usecase.HandleRecurringOccurrence
import com.joaoeoneves.fintrack.domain.usecase.RecordPayday
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** [AutomationSync]: when a pass runs (session, foreground, data) and when it is not repeated. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AutomationSyncTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-20T10:00:00Z"), ZoneId.of("UTC"))

    private fun due() = recurringItem(id = "r1", handledThrough = day(2026, 2, 15))

    private val income = FakeIncomeRepository()
    private val plan = FakePlannedIncomeRepository(listOf(PlannedIncome("p1", "Salary", 100_000L, 0)))
    private val automation = FakeAutomationSettingsRepository(incomeRepo = income)

    private fun TestScope.sync(
        session: FakeSessionRepository,
        repo: FakeRecurringRepository,
        gate: RestoreGate = RestoreGate(),
    ) = AutomationSync(
        session,
        AutoPostRecurring(
            repo,
            HandleRecurringOccurrence(repo, FakeCategoryRepository()),
            FakeExpenseRepository(),
            FakeCategoryRepository(),
        ),
        PaydayData(automation, plan, repo, gate),
        RecordPayday(plan, FakeMonthSettingsRepository(), income, automation, FakeSalaryLineLabelsProvider()),
        clock,
        backgroundScope,
    )

    private val jan = YearMonth.of(2026, 1)

    // 5 s base shifted by the one earlier attempt.
    private val firstRetryBackoffMs = 10_000L

    private fun autoLine() = PlannedIncome("p1", "Salary", 100_000L, 0, autoRecord = true)

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
    fun aFailedAttempt_isRetriedOnForeground() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeRecurringRepository(listOf(due()), nextConfirmError = IllegalStateException("down"))
            val sync = sync(guest(), repo)
            sync.start()
            advanceUntilIdle()
            assertTrue(repo.confirmedTransactions.isEmpty())

            sync.onForeground()
            advanceTimeBy(firstRetryBackoffMs + 1)
            runCurrent()

            // The failure was one-shot and a failed pass is not remembered as attempted: the tick retries once the
            // backoff has passed, and posts.
            assertEquals(listOf("rec_r1_20260315"), repo.confirmedTransactions.map { it.id })
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

            // The first attempt healed Jan/Feb 15 into the expense store, so only Mar 15 is posted now.
            assertEquals(1, repo.confirmedTransactions.size)
        }

    @Test
    fun changedSkippedSet_triggersANewAttempt() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeRecurringRepository(listOf(due()), nextConfirmError = IllegalStateException("down"))
            sync(guest(), repo).start()
            advanceUntilIdle()
            assertTrue(repo.confirmedTransactions.isEmpty())

            // Same cursor, only `skipped` differs: a different key, so the pass runs again and now succeeds.
            repo.setItems(listOf(due().copy(skipped = setOf(day(2025, 12, 15)))))
            advanceUntilIdle()

            assertEquals(1, repo.confirmedTransactions.size)
        }

    @Test
    fun payday_notRun_whileNoLineRecordsItself() =
        runTest(UnconfinedTestDispatcher()) {
            val manual = FakeAutomationSettingsRepository(AutomationSettings(lineRecordedThrough = mapOf("p1" to jan)))
            val repo = FakeRecurringRepository()
            val plain =
                AutomationSync(
                    guest(),
                    AutoPostRecurring(
                        repo,
                        HandleRecurringOccurrence(repo, FakeCategoryRepository()),
                        FakeExpenseRepository(),
                        FakeCategoryRepository(),
                    ),
                    PaydayData(manual, plan, repo, RestoreGate()),
                    RecordPayday(plan, FakeMonthSettingsRepository(), income, manual, FakeSalaryLineLabelsProvider()),
                    clock,
                    backgroundScope,
                )

            plain.start()
            advanceUntilIdle()

            assertTrue(manual.recorded.isEmpty())
        }

    @Test
    fun payday_runsForAutoRecordLines_andNotRepeatedOnForeground() =
        runTest(UnconfinedTestDispatcher()) {
            plan.savePlannedIncome(autoLine())
            automation.updateLineCursors(set = mapOf("p1" to jan))
            val sync = sync(guest(), FakeRecurringRepository())

            sync.start()
            advanceUntilIdle()
            sync.onForeground()
            advanceUntilIdle()

            // February's payday (Fri 27th) has passed; March's (Tue 31st) has not.
            assertEquals(listOf(mapOf("p1" to YearMonth.of(2026, 2))), automation.recorded.map { it.first })
            assertEquals(
                listOf("pay_p1_202602"),
                automation.recorded
                    .single()
                    .second
                    .map { it.id },
            )
            assertEquals(YearMonth.of(2026, 2), automation.settings.lineRecordedThrough["p1"])
        }

    @Test
    fun payday_switchingALineOnWhileRunning_triggersAPass_thatOnlyCreatesTheCursor() =
        runTest(UnconfinedTestDispatcher()) {
            sync(guest(), FakeRecurringRepository()).start()
            advanceUntilIdle()
            assertTrue(automation.recorded.isEmpty())

            plan.savePlannedIncome(autoLine())
            advanceUntilIdle()

            assertTrue(automation.recorded.isEmpty())
            assertEquals(YearMonth.of(2026, 2), automation.settings.lineRecordedThrough["p1"])
        }

    @Test
    fun payday_changingALinesPayDay_triggersANewPass() =
        runTest(UnconfinedTestDispatcher()) {
            plan.savePlannedIncome(autoLine())
            automation.updateLineCursors(set = mapOf("p1" to YearMonth.of(2026, 2)))
            sync(guest(), FakeRecurringRepository()).start()
            advanceUntilIdle()
            assertTrue(automation.recorded.isEmpty())

            // Day 5 of March has passed on the 20th.
            plan.savePlannedIncome(autoLine().copy(payDay = 5))
            advanceUntilIdle()

            assertEquals(
                listOf("pay_p1_202603"),
                automation.recorded
                    .single()
                    .second
                    .map { it.id },
            )
        }

    @Test
    fun whileRestoring_postsNothing_thenRunsOnTheRestoredDataAfterRelease() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = RestoreGate()
            val repo = FakeRecurringRepository(listOf(due()))
            val release = CompletableDeferred<Unit>()
            backgroundScope.launch { gate.hold { release.await() } }

            sync(guest(), repo, gate).start()
            advanceUntilIdle()
            assertTrue(repo.confirmedTransactions.isEmpty())

            // The restore swaps in another due item; the pass looks at it only after the gate opens.
            repo.setItems(listOf(recurringItem(id = "r9", handledThrough = day(2026, 2, 15))))
            advanceUntilIdle()
            assertTrue(repo.confirmedTransactions.isEmpty())

            release.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf("rec_r9_20260315"), repo.confirmedTransactions.map { it.id })
        }

    @Test
    fun aFailedAttempt_isRetriedAfterANewGeneration() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = RestoreGate()
            val repo = FakeRecurringRepository(listOf(due()), nextConfirmError = IllegalStateException("down"))
            sync(guest(), repo, gate).start()
            advanceUntilIdle()
            assertTrue(repo.confirmedTransactions.isEmpty())

            gate.hold { }
            advanceUntilIdle()

            assertEquals(1, repo.confirmedTransactions.size)
        }
}
