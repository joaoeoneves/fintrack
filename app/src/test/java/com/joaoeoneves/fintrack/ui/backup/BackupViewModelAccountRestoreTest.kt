package com.joaoeoneves.fintrack.ui.backup

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.backup.AccountRestoreResult
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

/** The signed-in restore flow of [BackupViewModel], with a [FakeAccountRestore] standing in for the account. */
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
                    BackupCounts(1, 0, 0, 0, 0),
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

    private suspend fun TestScope.confirmed(vm: BackupViewModel): MutableList<BackupImportState> {
        val states = mutableListOf<BackupImportState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.importState.toList(states) }
        vm.onConfirmImport()
        settle()
        return states
    }

    @Test
    fun success_reportsProgress_thenDone_andDropsTheFile() =
        runStoreTest {
            account.progress = listOf(0 to 4, 4 to 4)
            val vm = stagedAccountRestore()

            val states = confirmed(vm)

            assertTrue(BackupImportState.Restoring() in states)
            assertTrue(BackupImportState.Restoring(4, 4) in states)
            assertEquals(BackupImportState.Done(BackupCounts(1, 0, 0, 0, 0)), vm.importState.value)
            assertEquals(listOf(doc), account.calls)
            vm.onConfirmImport()
            vm.onRetryRestore()
            settle()
            assertEquals(1, account.calls.size)
        }

    @Test
    fun offline_showsTheOfflineMessage_andDropsTheFile() =
        runStoreTest {
            account.answer(AccountRestoreResult.Offline)
            val vm = stagedAccountRestore()

            confirmed(vm)

            val error = vm.importState.value as BackupImportState.Error
            assertError(R.string.backup_error_restore_offline, error.message)
            vm.onRetryRestore()
            settle()
            assertEquals(1, account.calls.size)
        }

    @Test
    fun failed_showsTheFailureMessage_andDropsTheFile() =
        runStoreTest {
            account.answer(AccountRestoreResult.Failed(IllegalStateException("x")))
            val vm = stagedAccountRestore()

            confirmed(vm)

            val error = vm.importState.value as BackupImportState.Error
            assertError(R.string.backup_error_restore_failed, error.message)
            vm.onConfirmImport()
            settle()
            assertEquals(1, account.calls.size)
        }

    @Test
    fun partial_keepsThePendingFile_andRetryRestoresAgain() =
        runStoreTest {
            val partial = AccountRestoreResult.Partial(3, 10, IllegalStateException("x"))
            account.answer(partial, AccountRestoreResult.Success)
            val vm = stagedAccountRestore()

            confirmed(vm)

            assertEquals(BackupImportState.Partial(3, 10), vm.importState.value)
            vm.onRetryRestore()
            settle()

            assertEquals(BackupImportState.Done(BackupCounts(1, 0, 0, 0, 0)), vm.importState.value)
            assertEquals(listOf(doc, doc), account.calls)
        }

    @Test
    fun partial_retryCanPartiallyFailAgain_thenDismissDropsTheFile() =
        runStoreTest {
            account.answer(AccountRestoreResult.Partial(3, 10, IllegalStateException("x")))
            val vm = stagedAccountRestore()
            confirmed(vm)

            vm.onRetryRestore()
            settle()
            assertEquals(BackupImportState.Partial(3, 10), vm.importState.value)

            vm.onDismissImport()
            vm.onRetryRestore()
            settle()

            assertEquals(BackupImportState.Idle, vm.importState.value)
            assertEquals(2, account.calls.size)
        }

    @Test
    fun partial_cannotBeConfirmedAsANewRestore() =
        runStoreTest {
            account.answer(AccountRestoreResult.Partial(1, 2, IllegalStateException("x")))
            val vm = stagedAccountRestore()
            confirmed(vm)

            vm.onConfirmImport()
            settle()

            assertEquals(1, account.calls.size)
        }

    @Test
    fun retry_withoutAPartialRestore_isANoOp() =
        runStoreTest {
            val vm = stagedAccountRestore()

            vm.onRetryRestore()
            settle()

            assertTrue(vm.importState.value is BackupImportState.Confirm)
            assertTrue(account.calls.isEmpty())
        }

    @Test
    fun signedIn_restore_leavesTheGuestStoreUntouched() =
        runStoreTest {
            localStore.seedCategory("guest-data")
            val vm = stagedAccountRestore()

            confirmed(vm)

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
