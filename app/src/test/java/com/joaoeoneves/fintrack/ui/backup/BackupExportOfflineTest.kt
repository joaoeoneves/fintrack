package com.joaoeoneves.fintrack.ui.backup

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlaceholderNames
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.data.backup.BackupRepository
import com.joaoeoneves.fintrack.data.backup.PlanBackupSource
import com.joaoeoneves.fintrack.data.backup.PlanSingletonSources
import com.joaoeoneves.fintrack.data.backup.ServerSync
import com.joaoeoneves.fintrack.data.backup.ServerUnreachableException
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** A signed-in export reads the server first: when that fails nothing is exported and the user is told why. */
class BackupExportOfflineTest : BackupViewModelTestBase() {
    private class ScriptedSync(
        private val result: Result<Unit>,
    ) : ServerSync {
        var calls = 0

        override suspend fun refresh(): Result<Unit> {
            calls++
            return result
        }
    }

    private val unreachable = Result.failure<Unit>(ServerUnreachableException(IllegalStateException("offline")))

    private fun repositoryWith(sync: ServerSync) =
        BackupRepository(
            FakeExpenseRepository(listOf(testExpense("e1", 500, Instant.parse("2026-03-10T12:00:00Z")))),
            FakeIncomeRepository(),
            PlanBackupSource(
                FakeBudgetRepository(),
                FakePlannedIncomeRepository(),
                FakeRecurringRepository(),
                FakeMonthSettingsRepository(),
                FakeEnvelopeRepository(),
                PlanSingletonSources(
                    FakeSalaryProfileRepository(),
                    FakeGoalRepository(),
                    FakeAutomationSettingsRepository(),
                    FakePlaceholderNames(),
                ),
            ),
            FakeCategoryRepository(),
            prefsStore,
            restorer,
            sync,
        )

    @Test
    fun buildBackup_failsWithServerUnreachable_whenTheRefreshFails() =
        runStoreTest {
            val result = repositoryWith(ScriptedSync(unreachable)).buildBackup(Instant.parse("2026-10-27T10:00:00Z"))

            assertTrue(result.exceptionOrNull() is ServerUnreachableException)
        }

    @Test
    fun buildBackup_refreshesOnce_andExportsWhenItSucceeds() =
        runStoreTest {
            val sync = ScriptedSync(Result.success(Unit))

            val document = repositoryWith(sync).buildBackup(Instant.parse("2026-10-27T10:00:00Z")).getOrThrow()

            assertEquals(1, sync.calls)
            assertEquals(listOf("e1"), document.expenses.map { it.id })
        }

    @Test
    fun export_whenTheServerIsUnreachable_showsTheOfflineMessage_andWritesNothing() =
        runStoreTest {
            val (file, uri) = newFileUri()
            val vm = viewModel(repository = repositoryWith(ScriptedSync(unreachable)))

            vm.onExportTargetSelected(uri)
            settle()

            assertEquals(
                BackupExportState.Error(UiText.StringResource(R.string.backup_error_export_offline)),
                vm.exportState.value,
            )
            assertEquals("", file.readText())
        }
}
