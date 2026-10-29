package com.joaoeoneves.fintrack.data.salary

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeSalaryLineLabelsProvider
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.data.backup.RestoreGate
import com.joaoeoneves.fintrack.domain.model.MANAGED_DUODECIMOS
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.asOf
import com.joaoeoneves.fintrack.domain.model.managedExtraKey
import com.joaoeoneves.fintrack.domain.model.salaryProfileLines
import com.joaoeoneves.fintrack.testutil.bonusExtra
import com.joaoeoneves.fintrack.testutil.flexExtra
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
import java.time.YearMonth

private const val LEGACY_PROFIT_SHARE = "profile_profit_share"

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ManagedIncomeSyncTest {
    private val profile = SalaryProfile(baseGrossCents = 100_000, irsBasePpm = 100_000, extras = listOf(bonusExtra()))
    private val own = PlannedIncome("own", "Own", 123, order = 0)
    private val full = salaryProfileLines(profile, listOf(own))

    private fun TestScope.sync(
        profileRepo: FakeSalaryProfileRepository,
        incomeRepo: FakePlannedIncomeRepository,
        gate: RestoreGate = RestoreGate(),
    ) = ManagedIncomeSync(
        profileRepo,
        incomeRepo,
        FakeIncomeRepository(),
        FakeSalaryLineLabelsProvider(),
        gate,
        backgroundScope,
    )

    @Test
    fun nullProfile_writesNothing() =
        runTest(UnconfinedTestDispatcher()) {
            val before = full + own
            val income = FakePlannedIncomeRepository(before)

            sync(FakeSalaryProfileRepository(null), income).start()
            advanceUntilIdle()

            assertEquals(before, income.lines)
        }

    @Test
    fun noManagedLines_writesNothing() =
        runTest(UnconfinedTestDispatcher()) {
            val income = FakePlannedIncomeRepository(listOf(own))

            sync(FakeSalaryProfileRepository(profile), income).start()
            advanceUntilIdle()

            assertEquals(listOf(own), income.lines)
        }

    @Test
    fun inSync_writesNothing() =
        runTest(UnconfinedTestDispatcher()) {
            val before = full + own
            val income = FakePlannedIncomeRepository(before)

            sync(FakeSalaryProfileRepository(profile), income).start()
            advanceUntilIdle()

            assertEquals(before, income.lines)
        }

    @Test
    fun staleLines_areFixed_andLegacyRemoved_once() =
        runTest(UnconfinedTestDispatcher()) {
            // Legacy layout: the salary line holds the whole net, no duodecimos line, a legacy profit share line.
            val legacySalary = full.single { it.id == MANAGED_NET_SALARY }.copy(amountCents = 86_000, label = "Mine")
            val legacyProfit = PlannedIncome(LEGACY_PROFIT_SHARE, "Profit", 5, 90, managedKey = LEGACY_PROFIT_SHARE)
            val stored =
                listOf(own, legacySalary, legacyProfit) +
                    full.filter { it.id !in setOf(MANAGED_NET_SALARY, MANAGED_DUODECIMOS) }
            val income = FakePlannedIncomeRepository(stored)
            val wantedDuo = full.single { it.id == MANAGED_DUODECIMOS }.amountCents

            sync(FakeSalaryProfileRepository(profile), income).start()
            advanceUntilIdle()

            val byId = income.lines.associateBy { it.id }
            assertEquals(79_000, byId.getValue(MANAGED_NET_SALARY).amountCents)
            assertEquals("Mine", byId.getValue(MANAGED_NET_SALARY).label)
            assertEquals(wantedDuo, byId.getValue(MANAGED_DUODECIMOS).amountCents)
            // A legacy line with history is ended (versioned to 0), not deleted.
            assertEquals(0L, byId.getValue(LEGACY_PROFIT_SHARE).asOf(YearMonth.of(2099, 1))?.amountCents)
            assertTrue("own" in byId)
            assertTrue(managedExtraKey("bonus", 5) in byId)
        }

    @Test
    fun legacyFlexAndBonusLines_areReplacedByTheExtraLines() =
        runTest(UnconfinedTestDispatcher()) {
            val legacyKeys = listOf("profile_flex_q1", "profile_flex_q2", "profile_bonus")
            val legacy = legacyKeys.mapIndexed { i, k -> PlannedIncome(k, "old", 5, 90 + i, managedKey = k) }
            val migrated = profile.copy(extras = listOf(flexExtra(), bonusExtra()))
            val income = FakePlannedIncomeRepository(listOf(own) + legacy)

            sync(FakeSalaryProfileRepository(migrated), income).start()
            advanceUntilIdle()

            val ids = income.lines.map { it.id }.toSet()
            // Legacy lines with history are ended (versioned to 0), not deleted.
            val byId = income.lines.associateBy { it.id }
            assertTrue(legacyKeys.all { byId.getValue(it).asOf(YearMonth.of(2099, 1))?.amountCents == 0L })
            assertTrue(managedExtraKey("bonus", 5) in ids)
            assertTrue(listOf(1, 4, 7, 10).all { managedExtraKey("flex", it) in ids })
            assertTrue("own" in ids)
        }

    @Test
    fun afterFixing_furtherChangesToUnrelatedLines_causeNoWrites() =
        runTest(UnconfinedTestDispatcher()) {
            val income = FakePlannedIncomeRepository(listOf(own) + full.filter { it.id != MANAGED_DUODECIMOS })
            sync(FakeSalaryProfileRepository(profile), income).start()
            advanceUntilIdle()
            val fixed = income.lines.toSet()

            income.savePlannedIncome(PlannedIncome("new", "New", 1, 0))
            advanceUntilIdle()

            assertEquals(fixed + PlannedIncome("new", "New", 1, 0), income.lines.toSet())
        }

    @Test
    fun sameFailingDiff_isNotRetried() =
        runTest(UnconfinedTestDispatcher()) {
            val income =
                FakePlannedIncomeRepository(
                    listOf(own) + full.filter { it.id != MANAGED_DUODECIMOS },
                    nextSaveError = IllegalStateException("boom"),
                )
            sync(FakeSalaryProfileRepository(profile), income).start()
            advanceUntilIdle()
            // The first write failed (error is consumed); nothing was stored.
            assertTrue(income.lines.none { it.id == MANAGED_DUODECIMOS })

            // An unrelated change re-emits the same data-derived diff: it must not be attempted again.
            income.savePlannedIncome(PlannedIncome("new", "New", 1, 0))
            advanceUntilIdle()

            assertTrue(income.lines.none { it.id == MANAGED_DUODECIMOS })
        }

    @Test
    fun afterAFailedWrite_aLaterDifferentProfile_isStillSynced() =
        runTest(UnconfinedTestDispatcher()) {
            val income =
                FakePlannedIncomeRepository(
                    listOf(own) + full.filter { it.id != MANAGED_DUODECIMOS },
                    nextSaveError = IllegalStateException("boom"),
                )
            val profiles = FakeSalaryProfileRepository(profile)
            sync(profiles, income).start()
            advanceUntilIdle()
            assertTrue(income.lines.none { it.id == MANAGED_DUODECIMOS })

            profiles.saveSalaryProfile(profile.copy(baseGrossCents = 120_000))
            advanceUntilIdle()

            assertTrue(income.lines.any { it.id == MANAGED_DUODECIMOS })
        }

    @Test
    fun startTwice_runsOneCollector() =
        runTest(UnconfinedTestDispatcher()) {
            val income = FakePlannedIncomeRepository(listOf(own) + full.filter { it.id != MANAGED_DUODECIMOS })
            val writes = mutableListOf<String>()
            val counting = RecordingIncome(income, writes)

            val sync =
                ManagedIncomeSync(
                    FakeSalaryProfileRepository(profile),
                    counting,
                    FakeIncomeRepository(),
                    FakeSalaryLineLabelsProvider(),
                    RestoreGate(),
                    backgroundScope,
                )
            sync.start()
            sync.start()
            advanceUntilIdle()

            assertEquals(listOf(MANAGED_DUODECIMOS), writes)
        }

    @Test
    fun whileRestoring_writesNothing_thenFixesTheRestoredLinesAfterRelease() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = RestoreGate()
            val income = FakePlannedIncomeRepository(listOf(own) + full.filter { it.id != MANAGED_DUODECIMOS })
            val release = CompletableDeferred<Unit>()
            backgroundScope.launch { gate.hold { release.await() } }

            sync(FakeSalaryProfileRepository(profile), income, gate).start()
            advanceUntilIdle()
            assertTrue(income.lines.none { it.id == MANAGED_DUODECIMOS })

            release.complete(Unit)
            advanceUntilIdle()

            assertTrue(income.lines.any { it.id == MANAGED_DUODECIMOS })
        }

    @Test
    fun aFailedDiff_isRetriedAfterANewGeneration() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = RestoreGate()
            val income =
                FakePlannedIncomeRepository(
                    listOf(own) + full.filter { it.id != MANAGED_DUODECIMOS },
                    nextSaveError = IllegalStateException("boom"),
                )
            sync(FakeSalaryProfileRepository(profile), income, gate).start()
            advanceUntilIdle()
            assertTrue(income.lines.none { it.id == MANAGED_DUODECIMOS })

            gate.hold { }
            advanceUntilIdle()

            assertTrue(income.lines.any { it.id == MANAGED_DUODECIMOS })
        }
}
