package com.joaoeoneves.fintrack.ui.migration

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class LocalDataMigrationViewModelFinishTest : LocalDataMigrationViewModelTestBase() {
    private suspend fun TestScope.done(): LocalDataMigrationViewModel {
        seedLocal()
        migrator.results += ok(3)
        val vm = viewModel()
        settle()
        vm.onAccept()
        settle()
        assertTrue(vm.uiState.value is MigrationUiState.Done)
        return vm
    }

    @Test
    fun removeLocal_clearsStoreKeepsBackupAndHides() =
        runStoreTest {
            val vm = done()

            vm.onRemoveLocal()
            settle()

            assertEquals(MigrationUiState.Hidden, vm.uiState.value)
            assertTrue(store.data.first().isEmpty)
            assertTrue(File(tmp.root, "guest-data.migrated.json").exists())
        }

    @Test
    fun removeLocal_doesNotReofferAfterwards() =
        runStoreTest {
            val vm = done()

            vm.onRemoveLocal()
            settle()

            assertEquals(MigrationUiState.Hidden, vm.uiState.value)
            assertNull(offerStore.handledRevision.first())
        }

    @Test
    fun removeLocal_failure_isErrorAndDataStays() =
        runStoreTest {
            val vm = done()
            val before = store.data.first()
            tmp.root.setWritable(false)
            try {
                val enforced = !runCatching { File(tmp.root, "p").createNewFile() }.getOrDefault(false)
                assumeTrue("directory permissions are not enforced", enforced)

                vm.onRemoveLocal()
                settle()

                val state = vm.uiState.value as MigrationUiState.Error
                assertText(R.string.migration_error_remove_failed, state.message)
                assertEquals(before, store.data.first())
            } finally {
                tmp.root.setWritable(true)
            }
        }

    @Test
    fun removeLocal_onlyWorksFromDone() =
        runStoreTest {
            val doc = seedLocal()
            val vm = viewModel()
            settle()

            vm.onRemoveLocal()
            settle()

            assertEquals(doc, store.data.first())
            assertTrue(vm.uiState.value is MigrationUiState.Offer)
        }

    @Test
    fun keepLocal_marksRevisionHandledHidesAndKeepsData() =
        runStoreTest {
            val vm = done()
            val doc = store.data.first()

            vm.onKeepLocal()
            settle()

            assertEquals(MigrationUiState.Hidden, vm.uiState.value)
            assertEquals(doc.revision, offerStore.handledRevision.first())
            assertEquals(doc, store.data.first())
            assertFalse(store.data.first() == LocalDataDocument())
        }

    @Test
    fun keepLocal_isNotReofferedByANewViewModel() =
        runStoreTest {
            val vm = done()
            vm.onKeepLocal()
            settle()

            val next = viewModel()
            settle()

            assertEquals(MigrationUiState.Hidden, next.uiState.value)
        }

    @Test
    fun dismissOnErrorOrPartialFailure_hidesWithoutMarkingHandled() =
        runStoreTest {
            seedLocal()
            migrator.results += failed(1, 4, IllegalStateException())
            val vm = viewModel()
            settle()
            vm.onAccept()
            settle()

            vm.onDismiss()
            settle()

            assertEquals(MigrationUiState.Hidden, vm.uiState.value)
            assertNull(offerStore.handledRevision.first())
        }

    @Test
    fun dismissedError_isOfferedAgainByANewViewModel() =
        runStoreTest {
            seedLocal()
            migrator.results += failed(0, 0, IllegalStateException())
            val vm = viewModel()
            settle()
            vm.onAccept()
            settle()
            vm.onDismiss()
            settle()

            val next = viewModel()
            settle()

            assertTrue(next.uiState.value is MigrationUiState.Offer)
        }

    @Test
    fun removeFailedError_isNotRetryable_andRetryIsNoOp() =
        runStoreTest {
            val vm = done()
            tmp.root.setWritable(false)
            try {
                val enforced = !runCatching { File(tmp.root, "p").createNewFile() }.getOrDefault(false)
                assumeTrue("directory permissions are not enforced", enforced)
                vm.onRemoveLocal()
                settle()
                val error = vm.uiState.value as MigrationUiState.Error
                assertFalse(error.retryable)

                vm.onRetry()
                settle()

                assertEquals(error, vm.uiState.value)
                assertEquals(1, migrator.received.size)
            } finally {
                tmp.root.setWritable(true)
            }
        }
}
