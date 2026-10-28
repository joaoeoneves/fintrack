package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.data.backup.RestoreGate
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.repository.AutomationSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.domain.usecase.PlanDataSources
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class IncomeMigrationSyncTest {
    private val log = mutableListOf<String>()
    private val rent =
        recurringItem("i1", type = RecurringType.INCOME, name = "Rent", autoPost = true)
            .copy(handledThrough = day(2026, 8, 5))
    private val salary = PlannedIncome("a", "Pay", 100_000, 0)
    private val recurring = FakeRecurringRepository(listOf(rent))
    private val plan = FakePlannedIncomeRepository(listOf(salary))
    private val automation =
        FakeAutomationSettingsRepository(
            AutomationSettings(legacyPaydayEnabled = true, legacyPaydayRecordedThrough = YearMonth.of(2026, 9)),
        )
    private val session = FakeSessionRepository(Session.Guest)
    private val gate = RestoreGate()
    private val clock = MutableClock(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC)

    private var failSaves = false
    private val loggedPlan =
        object : PlannedIncomeRepository by plan {
            override suspend fun savePlannedIncome(line: PlannedIncome): Result<String> {
                log += "save:${line.id}:${line.autoRecord}"
                if (failSaves) return Result.failure(IllegalStateException("offline"))
                return plan.savePlannedIncome(line)
            }
        }
    private val loggedRecurring =
        object : RecurringRepository by recurring {
            override suspend fun deleteRecurring(id: String): Result<Unit> {
                log += "delete:$id"
                return recurring.deleteRecurring(id)
            }
        }
    private val loggedAutomation =
        object : AutomationSettingsRepository by automation {
            override suspend fun updateLineCursors(
                set: Map<String, YearMonth>,
                remove: Set<String>,
                clearLegacyPayday: Boolean,
            ): Result<Unit> {
                log += if (clearLegacyPayday) "clearLegacy" else "cursors:${set.toSortedMap()}"
                return automation.updateLineCursors(set, remove, clearLegacyPayday)
            }
        }

    private fun TestScope.sync() =
        IncomeMigrationSync(
            PlanDataSources(
                FakeBudgetRepository(),
                loggedPlan,
                FakeMonthSettingsRepository(),
                FakeCategoryRepository(),
                FakeEnvelopeRepository(),
                loggedRecurring,
            ),
            loggedAutomation,
            session,
            clock,
            gate,
            backgroundScope,
        )

    private suspend fun TestScope.settle() {
        advanceTimeBy(10_000)
        runCurrent()
    }

    @Test
    fun writesInOrder_cursorsLinesAutoRecordLegacyClearThenRecurringDeletes() =
        runTest {
            sync().start()
            settle()

            assertEquals(
                listOf(
                    "cursors:{a=2026-09, rinc_i1=2026-08}",
                    "save:rinc_i1:true",
                    "save:a:true",
                    "clearLegacy",
                    "delete:i1",
                ),
                log,
            )
            assertTrue(recurring.items.isEmpty())
            assertEquals(setOf("a", "rinc_i1"), plan.lines.map { it.id }.toSet())
            assertTrue(plan.lines.all { it.autoRecord })
            assertEquals(false, automation.settings.legacyPaydayEnabled)
        }

    @Test
    fun migrated_secondEmissionWritesNothing() =
        runTest {
            sync().start()
            settle()
            val written = log.size

            recurring.saveRecurring(recurringItem("other"))
            settle()

            assertEquals(written, log.size)
        }

    @Test
    fun waitsForTheSettle_beforeWriting() =
        runTest {
            sync().start()

            advanceTimeBy(1_900)
            runCurrent()
            assertTrue(log.isEmpty())

            settle()
            assertTrue(log.isNotEmpty())
        }

    @Test
    fun sessionSwitchingMidWait_orSignedOut_writesNothing() =
        runTest {
            sync().start()
            advanceTimeBy(1_000)
            session.emit(Session.Loading)
            settle()
            assertTrue(log.isEmpty())

            session.emit(Session.SignedOut)
            settle()
            assertTrue(log.isEmpty())
        }

    @Test
    fun identicalFailedAttempt_isNotRetried() =
        runTest {
            automation.nextWriteError = IllegalStateException("offline")
            sync().start()
            settle()
            assertEquals(1, log.size)

            // Same migration again (an unrelated change re-emits): the failure is not retried.
            recurring.saveRecurring(recurringItem("other"))
            settle()

            assertEquals(1, log.size)
            assertEquals(1, recurring.items.count { it.id == "i1" })
        }

    @Test
    fun stopsAtTheFirstFailure_recurringItemKept() =
        runTest {
            failSaves = true
            sync().start()
            settle()

            // The cursors went through (so the migration is recomputed and retried once), but nothing after the
            // failing save is attempted: no second line, no legacy clear, no recurring delete.
            assertTrue(log.all { it.startsWith("cursors") || it == "save:rinc_i1:true" })
            assertTrue(log.none { it.startsWith("delete") || it == "clearLegacy" })
            assertTrue(recurring.items.any { it.id == "i1" })
        }

    @Test
    fun afterAFailedWrite_aLaterDifferentRecurringItem_isStillMigrated() =
        runTest {
            automation.nextWriteError = IllegalStateException("offline")
            sync().start()
            settle()
            assertEquals(1, log.size)

            val bonus =
                recurringItem("i2", type = RecurringType.INCOME, name = "Bonus", autoPost = true)
                    .copy(handledThrough = day(2026, 8, 5))
            recurring.saveRecurring(bonus)
            settle()

            assertTrue(log.size > 1)
            assertTrue(recurring.items.none { it.id == "i1" })
        }

    @Test
    fun whileRestoring_writesNothing_thenMigratesAfterRelease() =
        runTest {
            val release = CompletableDeferred<Unit>()
            backgroundScope.launch { gate.hold { release.await() } }
            runCurrent()
            sync().start()

            settle()
            assertTrue(log.isEmpty())
            assertEquals(1, recurring.items.size)

            release.complete(Unit)
            settle()

            assertTrue(log.isNotEmpty())
            assertTrue(recurring.items.isEmpty())
        }

    @Test
    fun aFailedAttempt_isRetriedAfterANewGeneration() =
        runTest {
            automation.nextWriteError = IllegalStateException("offline")
            sync().start()
            settle()
            assertEquals(1, log.size)

            gate.hold { }
            settle()

            assertTrue(log.size > 1)
            assertTrue(recurring.items.isEmpty())
        }
}
