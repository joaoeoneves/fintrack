package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeLegacyNames
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.data.local.LocalSavingsGoal
import com.joaoeoneves.fintrack.data.local.toLocal
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BackupGoalsTest : BackupRepositoryTestBase() {
    private val at = Instant.parse(EXPORTED_AT)
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""

    private val goal =
        SavingsGoal(
            id = "g1",
            name = "Holiday",
            targetCents = 120_000,
            targetType = GoalTargetType.TOTAL,
            startDate = LocalDate.of(2026, 1, 1),
            targetDate = LocalDate.of(2027, 1, 1),
            initialCents = 5_000,
            linkedRecurringIds = setOf("r1"),
            linkedCategoryId = CategoryId("FOOD"),
            order = 2,
        )

    private fun localGoal(
        id: String = "g1",
        targetCents: Long = 100,
        startDate: String = "2026-01-01",
        targetDate: String? = null,
    ) = LocalSavingsGoal(id, "Goal $id", targetCents, startDate = startDate, targetDate = targetDate)

    @Test
    fun v8File_decodesUpgradedToV14_withNoGoals() {
        val text = """{$header,"schemaVersion":8}"""

        val result = BackupCodec.decode(text).success()

        assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
        assertEquals(19, result.document.schemaVersion)
        assertTrue(result.document.goals.isEmpty())
        assertEquals(0, result.document.counts().goals)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun v20File_isUnsupported() {
        assertEquals(
            BackupDecodeResult.UnsupportedVersion(20),
            BackupCodec.decode("""{$header,"schemaVersion":20}"""),
        )
    }

    @Test
    fun codecRoundTrip_keepsGoals_andCountsThem() {
        val doc = backupDoc().copy(goals = listOf(goal.toLocal(), localGoal("g2")))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(doc, result.document)
        assertEquals(2, result.document.counts().goals)
        assertEquals(2, result.document.counts().total)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun invalidGoals_areDropped_andCountedAsSkipped() {
        val doc =
            backupDoc().copy(
                goals =
                    listOf(
                        localGoal("ok"),
                        localGoal("zero", targetCents = -1),
                        localGoal("reversed", startDate = "2026-05-01", targetDate = "2026-04-30"),
                        localGoal("dup"),
                        localGoal("dup"),
                    ),
            )

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf("ok", "dup"), result.document.goals.map { it.id })
        assertEquals(3, result.skippedCount)
    }

    @Test
    fun noTargetPlan_withRecurringId_roundTripsThroughBackup() {
        val plan = goal.copy(id = "plan_r1", targetCents = 0, recurringId = "r1")
        val doc = backupDoc().copy(goals = listOf(plan.toLocal()))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf(plan.toLocal()), result.document.goals)
        assertEquals(
            "r1",
            result.document.goals
                .single()
                .recurringId,
        )
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun v14GoalWithoutRecurringId_decodesWithNull() {
        val v14 =
            """{$header,"schemaVersion":14,
            "goals":[{"id":"g1","name":"G","targetCents":100,"startDate":"2026-01-01"}]}"""

        val result = BackupCodec.decode(v14).success()

        assertEquals(19, result.document.schemaVersion)
        assertEquals(
            null,
            result.document.goals
                .single()
                .recurringId,
        )
    }

    @Test
    fun export_includesGoals() =
        runStoreTest {
            val doc = repository(goals = listOf(goal)).buildBackup(at).getOrThrow()

            assertEquals(listOf(goal.toLocal()), doc.goals)
            assertEquals(1, doc.counts().goals)
            assertEquals(BACKUP_SCHEMA_VERSION, doc.schemaVersion)
        }

    @Test
    fun export_withoutGoals_hasNone() =
        runStoreTest {
            assertTrue(
                repository()
                    .buildBackup(at)
                    .getOrThrow()
                    .goals
                    .isEmpty(),
            )
        }

    @Test
    fun exportThenRestoreAsGuest_roundTripsGoals_andClearsThemWhenAbsent() =
        runStoreTest {
            val exported = repository(goals = listOf(goal)).buildBackup(at).getOrThrow()
            val decoded = BackupCodec.decode(BackupCodec.encode(exported)).success().document

            repository().restoreAsGuest(decoded).getOrThrow()
            assertEquals(listOf(goal.toLocal()), localStore.data.first().goals)

            repository().restoreAsGuest(backupDoc(expenses = listOf(backupExpense()))).getOrThrow()
            assertTrue(
                localStore.data
                    .first()
                    .goals
                    .isEmpty(),
            )
        }

    @Test
    fun localHasData_trueWithOnlyAGoal() =
        runStoreTest {
            localStore.update { it.copy(goals = listOf(localGoal())) to Unit }

            assertTrue(repository().localHasData())
        }

    @Test
    fun export_goalRepositoryFailure_isFailure() =
        runStoreTest {
            val repo =
                BackupRepository(
                    FakeExpenseRepository(),
                    FakeIncomeRepository(),
                    PlanBackupSource(
                        FakeBudgetRepository(),
                        FakePlannedIncomeRepository(),
                        FakeRecurringRepository(),
                        FakeMonthSettingsRepository(),
                        FakeEnvelopeRepository(),
                        PlanSingletonSources(
                            FakeSalaryProfileRepository(),
                            FakeGoalRepository(nextObserveError = IllegalStateException("boom")),
                            FakeAutomationSettingsRepository(),
                            FakeLegacyNames(),
                        ),
                    ),
                    FakeCategoryRepository(),
                    prefsStore,
                    restorer,
                )

            assertTrue(repo.buildBackup(at).isFailure)
        }
}
