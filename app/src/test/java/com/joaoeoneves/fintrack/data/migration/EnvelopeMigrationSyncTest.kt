package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Earmark
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.usecase.PlanDataSources
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EnvelopeMigrationSyncTest {
    private val gym = userCategory("gym", "Gym")
    private val categories = FakeCategoryRepository(listOf(gym))
    private val budgets = FakeBudgetRepository(listOf(Budget(gym.id, 5_000)))
    private val envelopes = FakeEnvelopeRepository(categories = categories, budgets = budgets)
    private val lines =
        FakePlannedIncomeRepository(listOf(PlannedIncome("meal", "Meal", 100, 0, earmark = Earmark.Category("gym"))))
    private val session = FakeSessionRepository(Session.Guest)

    private fun TestScope.sync() =
        EnvelopeMigrationSync(
            PlanDataSources(
                budgets,
                lines,
                FakeMonthSettingsRepository(),
                categories,
                envelopes,
                FakeRecurringRepository(),
            ),
            session,
            RuntimeEnvironment.getApplication(),
            backgroundScope,
        )

    @Test
    fun waitsForTheDataToSettle_thenAppliesOnce() =
        runTest {
            sync().start()

            advanceTimeBy(1_900)
            runCurrent()
            assertTrue(envelopes.savedEdits.isEmpty())

            advanceTimeBy(500)
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()

            val edit = envelopes.savedEdits.single()
            assertEquals("env_gym", edit.id)
            assertEquals("Gym", edit.name)
            assertEquals("meal", edit.fundedByLineId)
            assertNull(lines.lines.single().earmark)
        }

    @Test
    fun startingTwice_doesNotMigrateTwice() =
        runTest {
            val sync = sync()
            sync.start()
            sync.start()

            advanceTimeBy(10_000)
            runCurrent()

            assertEquals(1, envelopes.savedEdits.size)
        }

    @Test
    fun sessionSwitchingMidWait_writesNothing() =
        runTest {
            sync().start()
            advanceTimeBy(1_000)
            session.emit(Session.Loading)
            advanceTimeBy(5_000)
            runCurrent()

            assertTrue(envelopes.savedEdits.isEmpty())
            assertEquals(Earmark.Category("gym"), lines.lines.single().earmark)
        }

    @Test
    fun signedOut_writesNothing() =
        runTest {
            session.emit(Session.SignedOut)
            sync().start()
            advanceTimeBy(10_000)
            runCurrent()

            assertTrue(envelopes.savedEdits.isEmpty())
        }

    @Test
    fun nothingToMigrate_writesNothing() =
        runTest {
            val clean = FakePlannedIncomeRepository(listOf(PlannedIncome("salary", "Salary", 100, 0)))
            val sources =
                PlanDataSources(
                    FakeBudgetRepository(),
                    clean,
                    FakeMonthSettingsRepository(),
                    categories,
                    envelopes,
                    FakeRecurringRepository(),
                )
            EnvelopeMigrationSync(sources, session, RuntimeEnvironment.getApplication(), backgroundScope).start()

            advanceTimeBy(10_000)
            runCurrent()

            assertTrue(envelopes.savedEdits.isEmpty())
        }

    @Test
    fun aFailingWrite_isNotRetriedInALoop_andDoesNotCrash() =
        runTest {
            envelopes.nextWriteError = IllegalStateException("offline")
            sync().start()

            advanceTimeBy(10_000)
            runCurrent()

            assertTrue(envelopes.savedEdits.isEmpty())
            assertEquals(Earmark.Category("gym"), lines.lines.single().earmark)
        }
}
