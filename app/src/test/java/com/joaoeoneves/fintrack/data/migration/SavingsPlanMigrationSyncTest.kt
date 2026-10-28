package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.data.backup.RestoreGate
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.usecase.PlanDataSources
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SavingsPlanMigrationSyncTest {
    private val invest = BuiltInCategories.INVESTMENTS.id
    private val categories = FakeCategoryRepository()
    private val budgets = FakeBudgetRepository(listOf(Budget(invest, 5_000)))
    private val envelopes = FakeEnvelopeRepository(listOf(Envelope("inv", "Invest", 0)), categories, budgets)
    private val recurring = FakeRecurringRepository(listOf(recurringItem("r1", name = "ETF", categoryId = invest)))
    private val goals = FakeGoalRepository()
    private val session = FakeSessionRepository(Session.Guest)
    private val gate = RestoreGate()
    private val clock = MutableClock(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC)

    private fun TestScope.sync(
        goalRepo: FakeGoalRepository = goals,
        recurringRepo: FakeRecurringRepository = recurring,
    ) = SavingsPlanMigrationSync(
        PlanDataSources(
            budgets,
            FakePlannedIncomeRepository(),
            FakeMonthSettingsRepository(),
            categories,
            envelopes,
            recurringRepo,
        ),
        goalRepo,
        session,
        clock,
        gate,
        backgroundScope,
    )

    private suspend fun fileInEnvelope() {
        categories.setGroup(invest, "inv")
    }

    private suspend fun TestScope.settle() {
        advanceTimeBy(10_000)
        runCurrent()
    }

    @Test
    fun waitsForTheDataToSettle_thenCreatesThePlan_once() =
        runTest {
            sync().start()

            advanceTimeBy(1_900)
            runCurrent()
            assertTrue(goals.goals.isEmpty())

            settle()

            val plan = goals.goals.single()
            assertEquals("plan_r1", plan.id)
            assertEquals("r1", plan.recurringId)
            assertEquals("ETF", plan.name)
            assertEquals(1, goals.saveCount)
        }

    @Test
    fun startingTwice_doesNotMigrateTwice() =
        runTest {
            val sync = sync()
            sync.start()
            sync.start()

            settle()

            assertEquals(1, goals.saveCount)
        }

    @Test
    fun migrated_isANoOpOnLaterEmissions() =
        runTest {
            sync().start()
            settle()
            recurring.saveRecurring(recurringItem("other", categoryId = BuiltInCategories.FOOD.id))
            settle()

            assertEquals(1, goals.saveCount)
        }

    @Test
    fun sessionSwitchingMidWait_writesNothing() =
        runTest {
            sync().start()
            advanceTimeBy(1_000)
            session.emit(Session.Loading)
            settle()

            assertTrue(goals.goals.isEmpty())
        }

    @Test
    fun signedOut_writesNothing() =
        runTest {
            session.emit(Session.SignedOut)
            sync().start()
            settle()

            assertTrue(goals.goals.isEmpty())
        }

    @Test
    fun savingOnlyEnvelope_isUnfiled_budgetKept_thenDeleted() =
        runTest {
            fileInEnvelope()
            sync().start()
            settle()

            assertTrue(envelopes.envelopes.isEmpty())
            assertEquals(1, envelopes.savedEdits.size)
            assertEquals(null, categories.docs.first { it.id == invest }.groupId)
            assertEquals(listOf(Budget(invest, 5_000)), budgets.observeBudgets().first())
        }

    @Test
    fun nothingToMigrate_writesNothing() =
        runTest {
            sync(recurringRepo = FakeRecurringRepository()).start()
            settle()

            assertTrue(goals.goals.isEmpty())
            assertTrue(envelopes.savedEdits.isEmpty())
            assertEquals(1, envelopes.envelopes.size)
        }

    @Test
    fun aFailingWrite_isNotRetriedInALoop_andDoesNotCrash() =
        runTest {
            val failing = FakeGoalRepository(nextSaveError = IllegalStateException("offline"))
            sync(goalRepo = failing).start()

            settle()

            assertTrue(failing.goals.isEmpty())
            assertEquals(0, failing.saveCount)
        }

    @Test
    fun whileRestoring_writesNothing_thenCreatesThePlanAfterRelease() =
        runTest {
            val release = CompletableDeferred<Unit>()
            backgroundScope.launch { gate.hold { release.await() } }
            runCurrent()
            sync().start()

            settle()
            assertTrue(goals.goals.isEmpty())

            // The restore brings another recurring item; the plan is built from what is there after release.
            recurring.saveRecurring(recurringItem("r2", name = "Bond", categoryId = invest))
            release.complete(Unit)
            settle()

            assertEquals(setOf("plan_r1", "plan_r2"), goals.goals.map { it.id }.toSet())
        }

    @Test
    fun aFailedAttempt_isRetriedAfterANewGeneration() =
        runTest {
            goals.nextSaveError = IllegalStateException("offline")
            sync().start()
            settle()
            assertTrue(goals.goals.isEmpty())

            gate.hold { }
            settle()

            assertEquals("plan_r1", goals.goals.single().id)
        }
}
