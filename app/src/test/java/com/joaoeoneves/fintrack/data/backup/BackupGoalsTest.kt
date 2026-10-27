package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryGroupRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
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
    fun v8File_decodesUpgradedToV9_withNoGoals() {
        val text = """{$header,"schemaVersion":8}"""

        val result = BackupCodec.decode(text).success()

        assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
        assertEquals(9, result.document.schemaVersion)
        assertTrue(result.document.goals.isEmpty())
        assertEquals(0, result.document.counts().goals)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun v10File_isUnsupported() {
        assertEquals(
            BackupDecodeResult.UnsupportedVersion(10),
            BackupCodec.decode("""{$header,"schemaVersion":10}"""),
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
                        localGoal("zero", targetCents = 0),
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
                        FakeCategoryGroupRepository(),
                        PlanSingletonSources(
                            FakeSalaryProfileRepository(),
                            FakeGoalRepository(nextObserveError = IllegalStateException("boom")),
                        ),
                    ),
                    FakeCategoryRepository(),
                    prefsStore,
                    restorer,
                )

            assertTrue(repo.buildBackup(at).isFailure)
        }
}
