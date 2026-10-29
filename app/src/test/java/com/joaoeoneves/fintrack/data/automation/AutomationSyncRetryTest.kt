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
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import com.joaoeoneves.fintrack.domain.usecase.AutoPostRecurring
import com.joaoeoneves.fintrack.domain.usecase.HandleRecurringOccurrence
import com.joaoeoneves.fintrack.domain.usecase.RecordPayday
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * [AutomationSync]: a pass whose step throws is retried on the next foreground tick with a growing backoff, and a
 * pass that succeeded is not run again. (A step reports a rejected write as a Result, which is not a failed pass.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AutomationSyncRetryTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-20T10:00:00Z"), ZoneId.of("UTC"))
    private val income = FakeIncomeRepository()
    private val autoLine = PlannedIncome("p1", "Salary", 100_000L, 0, autoRecord = true)
    private val plan = FakePlannedIncomeRepository(listOf(autoLine))
    private val automation = FakeAutomationSettingsRepository(incomeRepo = income)
    private val repo = FakeRecurringRepository()
    private var reads = 0

    private fun TestScope.sync(failures: Int): AutomationSync {
        var remaining = failures
        val settings = FakeMonthSettingsRepository()
        val flaky =
            object : MonthSettingsRepository by settings {
                override fun observeMonthSettings() =
                    settings.observeMonthSettings().also {
                        reads++
                        if (remaining > 0) {
                            remaining--
                            error("boom")
                        }
                    }
            }
        return AutomationSync(
            FakeSessionRepository(initialSession = Session.Guest),
            AutoPostRecurring(
                repo,
                HandleRecurringOccurrence(repo, FakeCategoryRepository()),
                FakeExpenseRepository(),
                FakeCategoryRepository(),
            ),
            PaydayData(automation, plan, repo, RestoreGate()),
            RecordPayday(plan, flaky, income, automation, FakeSalaryLineLabelsProvider()),
            clock,
            backgroundScope,
        )
    }

    private suspend fun primeCursor() = automation.updateLineCursors(set = mapOf("p1" to YearMonth.of(2026, 1)))

    @Test
    fun aThrowingPass_isRetriedOnTheNextForegroundTick_andASuccessfulOneIsNotRerun() =
        runTest(UnconfinedTestDispatcher()) {
            primeCursor()
            val sync = sync(failures = 1)
            sync.start()
            advanceUntilIdle()
            assertTrue(automation.recorded.isEmpty())

            sync.onForeground()
            advanceTimeBy(BACKOFF_FIRST_RETRY_MS + 1)
            runCurrent()
            assertEquals(listOf("pay_p1_202602"), automation.recorded.flatMap { it.second }.map { it.id })
            val readsAfter = reads

            sync.onForeground()
            advanceTimeBy(BACKOFF_MAX_WAIT_MS)
            runCurrent()

            assertEquals(1, automation.recorded.size)
            assertEquals(readsAfter, reads)
        }

    @Test
    fun aRepeatedFailure_waitsLongerBeforeEachRetry() =
        runTest(UnconfinedTestDispatcher()) {
            primeCursor()
            val sync = sync(failures = Int.MAX_VALUE)
            sync.start()
            runCurrent()
            val first = reads
            assertTrue(first > 0)

            sync.onForeground()
            runCurrent()
            advanceTimeBy(BACKOFF_FIRST_RETRY_MS - 1)
            assertEquals(first, reads)

            advanceTimeBy(2)
            runCurrent()
            assertTrue(reads > first)
        }

    private companion object {
        // 5 s base shifted by the number of earlier attempts (1).
        const val BACKOFF_FIRST_RETRY_MS = 10_000L
        const val BACKOFF_MAX_WAIT_MS = 600_000L
    }
}
