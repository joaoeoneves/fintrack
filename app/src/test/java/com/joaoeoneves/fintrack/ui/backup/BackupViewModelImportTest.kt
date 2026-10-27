package com.joaoeoneves.fintrack.ui.backup

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.backup.BackupCounts
import com.joaoeoneves.fintrack.data.backup.BackupPreferences
import com.joaoeoneves.fintrack.data.backup.backupCategory
import com.joaoeoneves.fintrack.data.backup.backupDoc
import com.joaoeoneves.fintrack.data.backup.backupExpense
import com.joaoeoneves.fintrack.data.backup.backupIncome
import com.joaoeoneves.fintrack.data.local.LocalBudget
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupViewModelImportTest : BackupViewModelTestBase() {
    private val doc =
        backupDoc(
            expenses = listOf(backupExpense("e1", categoryId = "gym"), backupExpense("bad", amountCents = 0)),
            income = listOf(backupIncome()),
            budgets = listOf(LocalBudget("FOOD", 100)),
            categories = listOf(backupCategory("gym")),
            preferences = BackupPreferences(theme = "LIGHT"),
        )

    private fun error(vm: BackupViewModel) = (vm.importState.value as BackupImportState.Error).message

    @Test
    fun select_validFile_confirmWithCountsAndSkipped() =
        runStoreTest {
            val vm = viewModel()

            vm.onImportFileSelected(fileWith(encode(doc)))
            settle()

            assertEquals(
                BackupImportState.Confirm(BackupCounts(1, 1, 1, 1, 0), skippedCount = 1, localHasData = false),
                vm.importState.value,
            )
        }

    @Test
    fun select_whenLocalHasData_flagsReplacement() =
        runStoreTest {
            localStore.seedCategory("old")
            val vm = viewModel()

            vm.onImportFileSelected(fileWith(encode(doc)))
            settle()

            assertTrue((vm.importState.value as BackupImportState.Confirm).localHasData)
        }

    @Test
    fun confirm_replacesStoreWithFileContents_andEmitsDone() =
        runStoreTest {
            localStore.seedCategory("old")
            val vm = viewModel()
            vm.onImportFileSelected(fileWith(encode(doc)))
            settle()

            vm.onConfirmImport()
            settle()

            assertEquals(BackupImportState.Done(BackupCounts(1, 1, 1, 1, 0)), vm.importState.value)
            val stored = localStore.data.first()
            assertEquals(listOf("e1"), stored.expenses.map { it.id })
            assertEquals(listOf("gym"), stored.categories.map { it.id })
            assertEquals(ThemePreference.LIGHT, prefsStore.data.first().theme)
        }

    @Test
    fun confirm_notGuest_emitsRestoreFailed_storeUntouched() =
        runStoreTest {
            localStore.seedCategory("old")
            val vm = viewModel()
            vm.onImportFileSelected(fileWith(encode(doc)))
            settle()
            sessions.emit(Session.SignedOut)

            vm.onConfirmImport()
            settle()

            assertError(R.string.backup_error_restore_failed, error(vm))
            assertEquals(
                listOf("old"),
                localStore.data
                    .first()
                    .categories
                    .map { it.id },
            )
        }

    @Test
    fun confirm_withoutConfirmState_isNoOp() =
        runStoreTest {
            val vm = viewModel()

            vm.onConfirmImport()
            settle()

            assertEquals(BackupImportState.Idle, vm.importState.value)
            assertEquals(false, repository().localHasData())
        }

    @Test
    fun confirm_twice_secondIsNoOp() =
        runStoreTest {
            val vm = viewModel()
            vm.onImportFileSelected(fileWith(encode(doc)))
            settle()
            vm.onConfirmImport()
            settle()
            localStore.seedCategory("after")

            vm.onConfirmImport()
            settle()

            assertTrue(vm.importState.value is BackupImportState.Done)
            assertTrue(
                localStore.data
                    .first()
                    .categories
                    .any { it.id == "after" },
            )
        }

    @Test
    fun dismiss_leavesStoreUntouched_andCannotBeConfirmedLater() =
        runStoreTest {
            localStore.seedCategory("old")
            val vm = viewModel()
            vm.onImportFileSelected(fileWith(encode(doc)))
            settle()

            vm.onDismissImport()
            vm.onConfirmImport()
            settle()

            assertEquals(BackupImportState.Idle, vm.importState.value)
            assertEquals(
                listOf("old"),
                localStore.data
                    .first()
                    .categories
                    .map { it.id },
            )
        }

    @Test
    fun select_afterError_newValidFileReplacesIt() =
        runStoreTest {
            val vm = viewModel()
            vm.onImportFileSelected(fileWith("nope", "a.json"))
            settle()

            vm.onImportFileSelected(fileWith(encode(doc), "b.json"))
            settle()

            assertTrue(vm.importState.value is BackupImportState.Confirm)
        }
}
