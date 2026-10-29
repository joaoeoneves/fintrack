package com.joaoeoneves.fintrack.ui.income.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.usecase.DeletedTransaction
import com.joaoeoneves.fintrack.testutil.deleteTransactionFor
import com.joaoeoneves.fintrack.testutil.testIncome
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditIncomeViewModelDeleteTest {
    private val testDispatcher = StandardTestDispatcher()
    private val income = testIncome("e1", 1_250L, Instant.parse("2026-10-10T10:00:00Z"))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: IncomeRepository,
        id: String? = "e1",
    ) = AddEditIncomeViewModel(
        repository,
        deleteTransactionFor(incomes = repository),
        SavedStateHandle(if (id == null) emptyMap() else mapOf("incomeId" to id)),
        java.time.Clock.systemDefaultZone(),
    )

    private fun AddEditIncomeViewModel.form() = (uiState.value as AddEditIncomeUiState.Ready).form

    private fun failingDelete(delegate: IncomeRepository) =
        object : IncomeRepository by delegate {
            override suspend fun deleteIncome(id: String): Result<Unit> = boom()
        }

    @Test
    fun onDelete_success_removesExpense_andEmitsOriginal() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository(listOf(income))
            val vm = viewModel(repo)
            val emitted = mutableListOf<DeletedTransaction>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.deletedEvent.collect { emitted += it }
            }
            advanceUntilIdle()
            assertTrue(vm.form().canDelete)

            vm.onDelete()
            advanceUntilIdle()

            assertEquals(listOf(DeletedTransaction(income)), emitted)
            assertNull(repo.getIncome("e1").getOrThrow())
        }

    @Test
    fun onDelete_setsIsDeletingAndBusy_whileInFlight() =
        runTest(testDispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repo = FakeIncomeRepository(listOf(income))
            val slow =
                object : IncomeRepository by repo {
                    override suspend fun deleteIncome(id: String): Result<Unit> {
                        gate.await()
                        return repo.deleteIncome(id)
                    }
                }
            val vm = viewModel(slow)
            advanceUntilIdle()

            vm.onDelete()
            advanceUntilIdle()

            assertTrue(vm.form().isDeleting)
            assertTrue(vm.form().isBusy)
            gate.complete(Unit)
        }

    @Test
    fun onDelete_secondCallWhileDeleting_isIgnored() =
        runTest(testDispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repo = FakeIncomeRepository(listOf(income))
            var calls = 0
            val slow =
                object : IncomeRepository by repo {
                    override suspend fun deleteIncome(id: String): Result<Unit> {
                        calls++
                        gate.await()
                        return repo.deleteIncome(id)
                    }
                }
            val vm = viewModel(slow)
            val emitted = mutableListOf<DeletedTransaction>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.deletedEvent.collect { emitted += it }
            }
            advanceUntilIdle()

            vm.onDelete()
            vm.onDelete()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, calls)
            assertEquals(1, emitted.size)
        }

    @Test
    fun onDelete_failure_clearsIsDeleting_andSetsError_withoutEvent() =
        runTest(testDispatcher) {
            val vm = viewModel(failingDelete(FakeIncomeRepository(listOf(income))))
            val emitted = mutableListOf<DeletedTransaction>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.deletedEvent.collect { emitted += it }
            }
            advanceUntilIdle()

            vm.onDelete()
            advanceUntilIdle()

            assertFalse(vm.form().isDeleting)
            assertFalse(vm.form().isBusy)
            assertEquals(UiText.StringResource(R.string.error_failed_delete_income), vm.form().saveError)
            assertTrue(emitted.isEmpty())
        }

    @Test
    fun onDelete_afterFailure_canRetry() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository(listOf(income))
            var fail = true
            val flaky =
                object : IncomeRepository by repo {
                    override suspend fun deleteIncome(id: String) = if (fail) boom() else repo.deleteIncome(id)
                }
            val vm = viewModel(flaky)
            advanceUntilIdle()
            vm.onDelete()
            advanceUntilIdle()
            fail = false

            vm.onDelete()
            advanceUntilIdle()

            assertNull(repo.getIncome("e1").getOrThrow())
        }

    @Test
    fun onDelete_addMode_isNoOp_andCannotDelete() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository(listOf(income))
            val vm = viewModel(repo, id = null)
            advanceUntilIdle()

            vm.onDelete()
            advanceUntilIdle()

            assertFalse(vm.form().canDelete)
            assertFalse(vm.form().isDeleting)
            assertEquals(income, repo.getIncome("e1").getOrThrow())
        }

    @Test
    fun onDelete_beforeLoaded_isNoOp() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository(listOf(income))
            val vm = viewModel(repo)

            vm.onDelete()
            advanceUntilIdle()

            assertEquals(income, repo.getIncome("e1").getOrThrow())
        }

    @Test
    fun onDelete_unknownIdInEditMode_isNoOp() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository(listOf(income))
            val vm = viewModel(repo, id = "missing")
            advanceUntilIdle()

            vm.onDelete()
            advanceUntilIdle()

            assertEquals(income, repo.getIncome("e1").getOrThrow())
        }

    @Test
    fun onDelete_loadFailed_isNoOp() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository(listOf(income), nextGetIncomeError = IllegalStateException("x"))
            val vm = viewModel(repo)
            advanceUntilIdle()

            vm.onDelete()
            advanceUntilIdle()

            assertEquals(income, repo.getIncome("e1").getOrThrow())
        }

    @Test
    fun formState_isBusy_coversSavingAndDeleting() {
        assertFalse(IncomeFormState().isBusy)
        assertTrue(IncomeFormState(isSaving = true).isBusy)
        assertTrue(IncomeFormState(isDeleting = true).isBusy)
        assertFalse(IncomeFormState(isEditMode = true).canDelete)
        assertTrue(IncomeFormState(isEditMode = true, incomeId = "x").canDelete)
        assertFalse(IncomeFormState(incomeId = "x").canDelete)
    }
}

private fun boom(): Result<Unit> = Result.failure(IllegalStateException("x"))
