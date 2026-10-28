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
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** [AutomationSync]: a step that throws is logged and swallowed, and later different data is still processed. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AutomationSyncThrowingTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-20T10:00:00Z"), ZoneId.of("UTC"))
    private val income = FakeIncomeRepository()
    private val plan = FakePlannedIncomeRepository()
    private val automation = FakeAutomationSettingsRepository(incomeRepo = income)
    private val due = recurringItem(id = "r1", handledThrough = day(2026, 2, 15), autoPost = true)

    private fun TestScope.sync(repo: RecurringRepository) =
        AutomationSync(
            FakeSessionRepository(initialSession = Session.Guest),
            AutoPostRecurring(
                repo,
                HandleRecurringOccurrence(repo, FakeCategoryRepository()),
                FakeExpenseRepository(),
                FakeCategoryRepository(),
            ),
            PaydayData(automation, plan, repo, RestoreGate()),
            RecordPayday(plan, FakeMonthSettingsRepository(), income, automation, FakeSalaryLineLabelsProvider()),
            clock,
            backgroundScope,
        )

    @Test
    fun aThrowingStep_isSwallowed_andLaterDifferentDataIsStillPosted() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = FakeRecurringRepository(listOf(due))
            var throwNext = true
            val throwing =
                object : RecurringRepository by repo {
                    override suspend fun confirmOccurrence(
                        itemId: String,
                        occurrence: LocalDate,
                        transaction: Transaction,
                    ): Result<String> {
                        if (throwNext) {
                            throwNext = false
                            error("boom")
                        }
                        return repo.confirmOccurrence(itemId, occurrence, transaction)
                    }
                }
            sync(throwing).start()
            advanceUntilIdle()
            assertTrue(repo.confirmedTransactions.isEmpty())

            repo.setItems(listOf(due.copy(handledThrough = day(2026, 1, 15))))
            advanceUntilIdle()

            // The first attempt healed Jan/Feb 15 into the expense store, so only Mar 15 is posted now.
            assertEquals(1, repo.confirmedTransactions.size)
        }
}
