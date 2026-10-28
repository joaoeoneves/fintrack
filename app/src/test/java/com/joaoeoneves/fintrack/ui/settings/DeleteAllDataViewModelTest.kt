package com.joaoeoneves.fintrack.ui.settings

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.preferences.StartChecklistState
import com.joaoeoneves.fintrack.data.preferences.StartChecklistStore
import com.joaoeoneves.fintrack.domain.repository.DataResetRepository
import com.joaoeoneves.fintrack.testutil.InMemoryPreferencesDataStore
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeDataResetRepository : DataResetRepository {
    var calls = 0
        private set
    var gate: CompletableDeferred<Unit>? = null
    var results = ArrayDeque<Result<Unit>>()

    override suspend fun deleteAllData(): Result<Unit> {
        calls++
        gate?.await()
        return results.removeFirstOrNull() ?: Result.success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DeleteAllDataViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeDataResetRepository()
    private val checklistStore = StartChecklistStore(InMemoryPreferencesDataStore())
    private val failed = DeleteAllDataState.Failed(UiText.StringResource(R.string.delete_all_data_error))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = DeleteAllDataViewModel(repo, checklistStore)

    @Test
    fun initialState_isIdle() {
        assertEquals(DeleteAllDataState.Idle, vm().state.value)
    }

    @Test
    fun requestDelete_movesIdleToConfirming_withoutDeleting() =
        runTest(dispatcher) {
            val vm = vm()
            vm.onRequestDelete()
            advanceUntilIdle()
            assertEquals(DeleteAllDataState.Confirming, vm.state.value)
            assertEquals(0, repo.calls)
        }

    @Test
    fun dismiss_fromConfirming_returnsToIdle() {
        val vm = vm()
        vm.onRequestDelete()
        vm.onDismiss()
        assertEquals(DeleteAllDataState.Idle, vm.state.value)
    }

    @Test
    fun confirmWithoutRequest_isIgnored() =
        runTest(dispatcher) {
            val vm = vm()
            vm.onConfirmDelete()
            advanceUntilIdle()
            assertEquals(DeleteAllDataState.Idle, vm.state.value)
            assertEquals(0, repo.calls)
        }

    @Test
    fun confirm_success_goesDeletingThenIdle_andEmitsDeletedOnce() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>().also { repo.gate = it }
            val vm = vm()
            val events = mutableListOf<Unit>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.deleted.collect { events += it } }
            vm.onRequestDelete()
            vm.onConfirmDelete()
            assertEquals(DeleteAllDataState.Deleting, vm.state.value)
            advanceUntilIdle()
            assertEquals(1, repo.calls)
            assertEquals(DeleteAllDataState.Deleting, vm.state.value)
            assertTrue(events.isEmpty())

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(DeleteAllDataState.Idle, vm.state.value)
            assertEquals(1, events.size)
            // Further interaction does not re-emit.
            vm.onConfirmDelete()
            vm.onDismiss()
            advanceUntilIdle()
            assertEquals(1, events.size)
            assertEquals(1, repo.calls)
            job.cancel()
        }

    @Test
    fun confirm_failure_goesToFailed_withoutEmitting() =
        runTest(dispatcher) {
            repo.results.add(Result.failure(java.io.IOException("offline")))
            val vm = vm()
            val events = mutableListOf<Unit>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.deleted.collect { events += it } }
            vm.onRequestDelete()
            vm.onConfirmDelete()
            advanceUntilIdle()

            assertEquals(failed, vm.state.value)
            assertTrue(events.isEmpty())
            job.cancel()
        }

    @Test
    fun failed_dismiss_returnsToIdle_andRequestStartsOver() =
        runTest(dispatcher) {
            repo.results.add(Result.failure(IllegalStateException("x")))
            val vm = vm()
            vm.onRequestDelete()
            vm.onConfirmDelete()
            advanceUntilIdle()
            vm.onDismiss()
            assertEquals(DeleteAllDataState.Idle, vm.state.value)
            vm.onRequestDelete()
            assertEquals(DeleteAllDataState.Confirming, vm.state.value)
        }

    @Test
    fun failed_retry_runsAgain_andSuccessEmitsDeleted() =
        runTest(dispatcher) {
            repo.results.add(Result.failure(IllegalStateException("x")))
            val vm = vm()
            val events = mutableListOf<Unit>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.deleted.collect { events += it } }
            vm.onRequestDelete()
            vm.onConfirmDelete()
            advanceUntilIdle()
            assertEquals(failed, vm.state.value)

            vm.onConfirmDelete()
            assertEquals(DeleteAllDataState.Deleting, vm.state.value)
            advanceUntilIdle()

            assertEquals(2, repo.calls)
            assertEquals(DeleteAllDataState.Idle, vm.state.value)
            assertEquals(1, events.size)
            job.cancel()
        }

    @Test
    fun failed_retryFailsAgain_staysFailed() =
        runTest(dispatcher) {
            repo.results.add(Result.failure(IllegalStateException("1")))
            repo.results.add(Result.failure(IllegalStateException("2")))
            val vm = vm()
            vm.onRequestDelete()
            vm.onConfirmDelete()
            advanceUntilIdle()
            vm.onConfirmDelete()
            advanceUntilIdle()
            assertEquals(failed, vm.state.value)
            assertEquals(2, repo.calls)
        }

    @Test
    fun dismissAndRequest_ignoredWhileDeleting_andDoubleConfirmRunsOnce() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>().also { repo.gate = it }
            val vm = vm()
            vm.onRequestDelete()
            vm.onConfirmDelete()
            advanceUntilIdle()

            vm.onDismiss()
            vm.onRequestDelete()
            vm.onConfirmDelete()
            advanceUntilIdle()

            assertEquals(DeleteAllDataState.Deleting, vm.state.value)
            assertEquals(1, repo.calls)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(DeleteAllDataState.Idle, vm.state.value)
            assertEquals(1, repo.calls)
        }

    @Test
    fun confirm_success_clearsTheStartChecklistDecision() =
        runTest(dispatcher) {
            checklistStore.set(StartChecklistState.DISMISSED)
            val vm = vm()
            vm.onRequestDelete()
            vm.onConfirmDelete()
            advanceUntilIdle()

            assertNull(checklistStore.state.first())
        }

    @Test
    fun confirm_failure_keepsTheStartChecklistDecision() =
        runTest(dispatcher) {
            checklistStore.set(StartChecklistState.DISMISSED)
            repo.results.add(Result.failure(java.io.IOException("offline")))
            val vm = vm()
            vm.onRequestDelete()
            vm.onConfirmDelete()
            advanceUntilIdle()

            assertEquals(StartChecklistState.DISMISSED, checklistStore.state.first())
        }
}
