package com.joaoeoneves.fintrack.ui.migration

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.local.LocalIncome
import com.joaoeoneves.fintrack.data.migration.MigrationOfflineException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataMigrationViewModelRunTest : LocalDataMigrationViewModelTestBase() {
    private suspend fun TestScope.offered(): LocalDataMigrationViewModel {
        seedLocal()
        return viewModel().also { settle() }
    }

    @Test
    fun accept_success_endsInDoneWithSkipped_andPassesLocalDocument() =
        runStoreTest {
            val doc = seedLocal()
            migrator.results += ok(written = 3, skipped = 1)
            val vm = viewModel()
            settle()

            vm.onAccept()
            settle()

            assertEquals(MigrationUiState.Done(written = 3, skipped = 1), vm.uiState.value)
            assertEquals(listOf(doc), migrator.received)
        }

    @Test
    fun accept_showsRunningWithProgressWhileMigrating() =
        runStoreTest {
            val gate = CompletableDeferred<Unit>()
            migrator.gate = gate
            migrator.progress = listOf(0 to 10, 4 to 10)
            migrator.results += ok(10)
            val vm = offered()

            vm.onAccept()
            assertEquals(MigrationUiState.Running(0, 0), vm.uiState.value)
            settle()
            assertEquals(MigrationUiState.Running(4, 10), vm.uiState.value)

            gate.complete(Unit)
            settle()
            assertEquals(MigrationUiState.Done(10, 0), vm.uiState.value)
        }

    @Test
    fun acceptWhenNotOffered_isIgnored() =
        runStoreTest {
            val vm = viewModel()
            settle()

            vm.onAccept()
            settle()

            assertEquals(MigrationUiState.Hidden, vm.uiState.value)
            assertTrue(migrator.received.isEmpty())
        }

    @Test
    fun failureWithNothingPlanned_isRetryableError() =
        runStoreTest {
            migrator.results += failed(0, 0, MigrationOfflineException("down"))
            val vm = offered()

            vm.onAccept()
            settle()

            val state = vm.uiState.value as MigrationUiState.Error
            assertText(R.string.migration_error_offline, state.message)
            assertTrue(state.retryable)
        }

    @Test
    fun retryFromRetryableError_runsAgainAndEndsInDone() =
        runStoreTest {
            migrator.results += failed(0, 0, MigrationOfflineException("down"))
            migrator.results += ok(written = 3, skipped = 1)
            val vm = offered()
            vm.onAccept()
            settle()

            vm.onRetry()
            assertTrue(vm.uiState.value is MigrationUiState.Running)
            settle()

            assertEquals(MigrationUiState.Done(written = 3, skipped = 1), vm.uiState.value)
            assertEquals(2, migrator.received.size)
        }

    @Test
    fun failureAfterPlanning_isPartialFailureWithGenericMessage_neverRawText() =
        runStoreTest {
            migrator.results += failed(2, 5, IllegalStateException("secret internal detail"))
            val vm = offered()

            vm.onAccept()
            settle()

            val state = vm.uiState.value as MigrationUiState.PartialFailure
            assertEquals(2, state.done)
            assertEquals(5, state.total)
            assertText(R.string.migration_error_failed, state.message)
        }

    @Test
    fun offlinePartialFailure_usesOfflineMessage() =
        runStoreTest {
            migrator.results += failed(1, 5, MigrationOfflineException("timeout"))
            val vm = offered()

            vm.onAccept()
            settle()

            val state = vm.uiState.value as MigrationUiState.PartialFailure
            assertText(R.string.migration_error_offline, state.message)
        }

    @Test
    fun retry_afterPartialFailure_countsAreCumulative() =
        runStoreTest {
            migrator.results += failed(2, 5, IllegalStateException())
            migrator.results += ok(written = 3, total = 3, skipped = 1)
            val vm = offered()

            vm.onAccept()
            settle()
            vm.onRetry()
            settle()

            assertEquals(MigrationUiState.Done(written = 5, skipped = 1), vm.uiState.value)
            assertEquals(2, migrator.received.size)
        }

    @Test
    fun retry_runningProgressIncludesCarriedWrites() =
        runStoreTest {
            migrator.results += failed(2, 5, IllegalStateException())
            val vm = offered()
            vm.onAccept()
            settle()

            val gate = CompletableDeferred<Unit>()
            migrator.gate = gate
            migrator.progress = listOf(1 to 3)
            migrator.results += ok(3)
            vm.onRetry()
            assertEquals(MigrationUiState.Running(2, 2), vm.uiState.value)
            settle()

            assertEquals(MigrationUiState.Running(3, 5), vm.uiState.value)
            gate.complete(Unit)
        }

    @Test
    fun secondPartialFailure_reportsCumulativeDoneAndTotal() =
        runStoreTest {
            migrator.results += failed(2, 5, IllegalStateException())
            migrator.results += failed(1, 3, IllegalStateException())
            val vm = offered()

            vm.onAccept()
            settle()
            vm.onRetry()
            settle()

            val state = vm.uiState.value as MigrationUiState.PartialFailure
            assertEquals(3, state.done)
            assertEquals(5, state.total)
        }

    @Test
    fun freshAccept_afterDismissingPartialFailure_startsFromZero() =
        runStoreTest {
            migrator.results += failed(2, 5, IllegalStateException())
            migrator.results += ok(4)
            val vm = offered()
            vm.onAccept()
            settle()
            vm.onDismiss()
            settle()
            assertEquals(MigrationUiState.Hidden, vm.uiState.value)

            store.update { it.copy(income = it.income + LocalIncome("i9", "Bonus", 5, "2026-03-11T12:00:00Z")) to Unit }
            settle()
            vm.onAccept()
            settle()

            assertEquals(MigrationUiState.Done(written = 4, skipped = 0), vm.uiState.value)
            assertNull(offerStore.handledRevision.first())
        }

    @Test
    fun localDataChangingWhileRunning_doesNotDisturbTheRun() =
        runStoreTest {
            val gate = CompletableDeferred<Unit>()
            migrator.gate = gate
            migrator.results += ok(2)
            val vm = offered()
            vm.onAccept()
            settle()

            store.update { it.copy(expenses = emptyList(), income = emptyList()) to Unit }
            settle()
            assertTrue(vm.uiState.value is MigrationUiState.Running)

            gate.complete(Unit)
            settle()
            assertEquals(MigrationUiState.Done(2, 0), vm.uiState.value)
        }
}
