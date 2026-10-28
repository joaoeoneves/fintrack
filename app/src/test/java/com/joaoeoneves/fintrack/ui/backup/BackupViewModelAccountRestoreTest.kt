package com.joaoeoneves.fintrack.ui.backup

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.backup.BackupCounts
import com.joaoeoneves.fintrack.data.backup.backupDoc
import com.joaoeoneves.fintrack.data.backup.backupExpense
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The signed-in restore flow of [BackupViewModel]: warning, export first, picker, confirm. The account restorer is
 * final and unmockable here, so it is a real one with nobody signed in: it can only end in `Failed`. The `Success`,
 * `Offline` and `Partial` outcomes (and retry) need a fake account restorer and are not covered.
 */
class BackupViewModelAccountRestoreTest : BackupViewModelTestBase() {
    private val doc = backupDoc(expenses = listOf(backupExpense("e1")))

    private suspend fun BackupViewModel.pickEvents(scope: TestScope): MutableList<Unit> {
        val events = mutableListOf<Unit>()
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { pickFile.toList(events) }
        return events
    }

    private suspend fun TestScope.stagedAccountRestore(): BackupViewModel {
        sessions.emit(Session.SignedIn(AuthUser("u", null, null, null)))
        val vm = viewModel()
        vm.onRestoreRequested(signedIn = true)
        vm.onContinueFromWarning()
        vm.onImportFileSelected(fileWith(encode(doc)))
        settle()
        return vm
    }

    @Test
    fun signedIn_restoreRequest_opensTheWarning_notThePicker() =
        runStoreTest {
            val vm = viewModel()
            val picks = vm.pickEvents(this)

            vm.onRestoreRequested(signedIn = true)
            settle()

            assertEquals(BackupImportState.AccountWarning, vm.importState.value)
            assertTrue(picks.isEmpty())
        }

    @Test
    fun guest_restoreRequest_opensThePickerDirectly() =
        runStoreTest {
            val vm = viewModel()
            val picks = vm.pickEvents(this)

            vm.onRestoreRequested(signedIn = false)
            settle()

            assertEquals(BackupImportState.Idle, vm.importState.value)
            assertEquals(1, picks.size)
        }

    @Test
    fun continueFromWarning_closesTheWarning_andOpensThePicker() =
        runStoreTest {
            val vm = viewModel()
            val picks = vm.pickEvents(this)
            vm.onRestoreRequested(signedIn = true)

            vm.onContinueFromWarning()
            settle()

            assertEquals(BackupImportState.Idle, vm.importState.value)
            assertEquals(1, picks.size)
        }

    @Test
    fun dismissWarning_goesIdle_withoutPicker() =
        runStoreTest {
            val vm = viewModel()
            val picks = vm.pickEvents(this)
            vm.onRestoreRequested(signedIn = true)

            vm.onDismissImport()
            settle()

            assertEquals(BackupImportState.Idle, vm.importState.value)
            assertTrue(picks.isEmpty())
        }

    @Test
    fun exportFirst_closesTheWarning_thenReturnsToItAfterTheExportDialogIsDismissed() =
        runStoreTest {
            val vm = viewModel()
            vm.onRestoreRequested(signedIn = true)
            val (file, uri) = newFileUri()

            vm.onExportFirst()
            assertEquals(BackupImportState.Idle, vm.importState.value)
            vm.onExportTargetSelected(uri)
            settle()
            assertTrue(vm.exportState.value is BackupExportState.Done)
            assertTrue(file.length() > 0)
            // The export result dialog is still up: the warning waits.
            assertEquals(BackupImportState.Idle, vm.importState.value)

            vm.onDismissExport()

            assertEquals(BackupExportState.Idle, vm.exportState.value)
            assertEquals(BackupImportState.AccountWarning, vm.importState.value)
        }

    @Test
    fun exportFirst_cancelledSaveDialog_returnsToTheWarning() =
        runStoreTest {
            val vm = viewModel()
            vm.onRestoreRequested(signedIn = true)
            vm.onExportFirst()

            vm.onExportCancelled()

            assertEquals(BackupImportState.AccountWarning, vm.importState.value)
        }

    @Test
    fun exportFirst_failedExport_returnsToTheWarningAfterDismiss() =
        runStoreTest {
            val vm = viewModel(repository = failingRepository())
            vm.onRestoreRequested(signedIn = true)
            vm.onExportFirst()
            val (_, uri) = newFileUri()

            vm.onExportTargetSelected(uri)
            settle()
            assertTrue(vm.exportState.value is BackupExportState.Error)
            vm.onDismissExport()

            assertEquals(BackupImportState.AccountWarning, vm.importState.value)
        }

    @Test
    fun exportFirst_returnsToTheWarningOnlyOnce() =
        runStoreTest {
            val vm = viewModel()
            vm.onRestoreRequested(signedIn = true)
            vm.onExportFirst()
            vm.onExportCancelled()
            vm.onDismissImport()

            vm.onExportCancelled()
            vm.onDismissExport()

            assertEquals(BackupImportState.Idle, vm.importState.value)
        }

    @Test
    fun plainExport_neverOpensTheWarning() =
        runStoreTest {
            val vm = viewModel()
            val (_, uri) = newFileUri()

            vm.onExportTargetSelected(uri)
            settle()
            vm.onDismissExport()
            vm.onExportCancelled()

            assertEquals(BackupImportState.Idle, vm.importState.value)
        }

    @Test
    fun signedIn_validFile_confirmsAsAnAccountRestore_thatReplacesData() =
        runStoreTest {
            val vm = stagedAccountRestore()

            assertEquals(
                BackupImportState.Confirm(
                    BackupCounts(1, 0, 0, 0, 0, 0),
                    skippedCount = 0,
                    localHasData = true,
                    account = true,
                ),
                vm.importState.value,
            )
        }

    @Test
    fun guest_validFile_confirmIsNotAnAccountRestore() =
        runStoreTest {
            val vm = viewModel()
            vm.onRestoreRequested(signedIn = false)
            vm.onImportFileSelected(fileWith(encode(doc)))
            settle()

            assertEquals(false, (vm.importState.value as BackupImportState.Confirm).account)
        }

    @Test
    fun signedIn_confirm_showsRestoring_thenTheFailureWhenTheAccountCannotBeReached() =
        runStoreTest {
            val vm = stagedAccountRestore()
            val states = mutableListOf<BackupImportState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.importState.toList(states) }

            vm.onConfirmImport()
            settle()

            assertEquals(BackupImportState.Restoring(), states[states.size - 2])
            val last = vm.importState.value as BackupImportState.Error
            assertError(R.string.backup_error_restore_failed, last.message)
        }

    @Test
    fun signedIn_failedRestore_dropsTheFile_soNothingCanBeRetried() =
        runStoreTest {
            val vm = stagedAccountRestore()
            vm.onConfirmImport()
            settle()

            vm.onConfirmImport()
            vm.onRetryRestore()
            settle()

            assertTrue(vm.importState.value is BackupImportState.Error)
            vm.onDismissImport()
            assertEquals(BackupImportState.Idle, vm.importState.value)
        }

    @Test
    fun retry_withoutAPartialRestore_isANoOp() =
        runStoreTest {
            val vm = stagedAccountRestore()

            vm.onRetryRestore()
            settle()

            assertTrue(vm.importState.value is BackupImportState.Confirm)
        }

    @Test
    fun signedIn_failedRestore_leavesTheGuestStoreUntouched() =
        runStoreTest {
            localStore.seedCategory("guest-data")
            val vm = stagedAccountRestore()

            vm.onConfirmImport()
            settle()

            assertEquals(
                listOf("guest-data"),
                localStore.data
                    .first()
                    .categories
                    .map { it.id },
            )
        }

    @Test
    fun restoringState_defaultsToUnknownTotal() {
        assertEquals(BackupImportState.Restoring(0, 0), BackupImportState.Restoring())
        assertTrue(BackupImportState.Restoring(3, 10) != BackupImportState.Restoring(4, 10))
    }
}
