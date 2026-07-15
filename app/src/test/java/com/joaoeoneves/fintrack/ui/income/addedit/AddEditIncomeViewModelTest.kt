package com.joaoeoneves.fintrack.ui.income.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Unit tests for [AddEditIncomeViewModel], mirroring
 * [com.joaoeoneves.fintrack.ui.expense.addedit.AddEditExpenseViewModelTest]'s coverage/conventions.
 *
 * Like `AddEditExpenseViewModel`, the constructor eagerly calls
 * `savedStateHandle.toRoute<AddEditIncome>()`; any *present* value in the [SavedStateHandle] (e.g.
 * a non-null `incomeId` for edit mode) requires a real (shadowed) `android.os.Bundle` to decode,
 * hence [RobolectricTestRunner].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditIncomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun addModeViewModel(repository: IncomeRepository) = AddEditIncomeViewModel(repository, SavedStateHandle())

    private fun editModeViewModel(
        repository: IncomeRepository,
        incomeId: String,
    ) = AddEditIncomeViewModel(repository, SavedStateHandle(mapOf("incomeId" to incomeId)))

    // ---- add mode: initial state ----

    @Test
    fun addMode_initialState_isReadyWithBlankForm_notEditMode() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            val viewModel = addModeViewModel(repo)

            advanceUntilIdle()

            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertFalse(state.form.isEditMode)
            assertEquals("", state.form.source)
            assertEquals("", state.form.amountText)
            assertEquals("", state.form.note)
            assertNull(state.form.sourceError)
            assertNull(state.form.amountError)
            assertNull(state.form.incomeId)
        }

    @Test
    fun addMode_initialState_defaultsDateToNow() =
        runTest(testDispatcher) {
            val before = Instant.now()
            val repo = FakeIncomeRepository()
            val viewModel = addModeViewModel(repo)

            advanceUntilIdle()
            val after = Instant.now()

            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertFalse(state.form.date.isBefore(before))
            assertFalse(state.form.date.isAfter(after))
        }

    // ---- validation ----

    @Test
    fun onSave_blankSource_setsSourceError_doesNotCallRepository() =
        runTest(testDispatcher) {
            var addCalled = false
            val repo =
                TrackingRepository(onAdd = {
                    addCalled = true
                    Result.success("id")
                })
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()

            viewModel.onAmountChanged("12.50")
            viewModel.onSave()
            advanceUntilIdle()

            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertEquals("Source is required", state.form.sourceError)
            assertFalse(addCalled)
        }

    @Test
    fun onSave_invalidAmount_setsAmountError_doesNotCallRepository() =
        runTest(testDispatcher) {
            var addCalled = false
            val repo =
                TrackingRepository(onAdd = {
                    addCalled = true
                    Result.success("id")
                })
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()

            viewModel.onSourceChanged("Paycheck")
            viewModel.onAmountChanged("abc")
            viewModel.onSave()
            advanceUntilIdle()

            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertEquals("Enter a valid amount", state.form.amountError)
            assertFalse(addCalled)
        }

    @Test
    fun onSave_zeroAmount_setsAmountError() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()

            viewModel.onSourceChanged("Paycheck")
            viewModel.onAmountChanged("0")
            viewModel.onSave()
            advanceUntilIdle()

            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertEquals("Enter a valid amount", state.form.amountError)
        }

    @Test
    fun onSave_blankSourceAndInvalidAmount_setsBothErrors() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()

            viewModel.onSave()
            advanceUntilIdle()

            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertEquals("Source is required", state.form.sourceError)
            assertEquals("Enter a valid amount", state.form.amountError)
        }

    @Test
    fun onSourceChanged_clearsPriorSourceError() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()
            viewModel.onSave() // triggers sourceError
            advanceUntilIdle()
            assertNotNull((viewModel.uiState.value as AddEditIncomeUiState.Ready).form.sourceError)

            viewModel.onSourceChanged("Paycheck")

            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertNull(state.form.sourceError)
            assertEquals("Paycheck", state.form.source)
        }

    @Test
    fun onAmountChanged_clearsPriorAmountError() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()
            viewModel.onSourceChanged("Paycheck")
            viewModel.onSave() // triggers amountError (blank amount)
            advanceUntilIdle()
            assertNotNull((viewModel.uiState.value as AddEditIncomeUiState.Ready).form.amountError)

            viewModel.onAmountChanged("5.00")

            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertNull(state.form.amountError)
            assertEquals("5.00", state.form.amountText)
        }

    // ---- form field updates ----

    @Test
    fun onDateSelected_updatesDate() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()
            val newDate = Instant.parse("2020-01-01T00:00:00Z")

            viewModel.onDateSelected(newDate)

            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertEquals(newDate, state.form.date)
        }

    @Test
    fun onNoteChanged_updatesNote() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()

            viewModel.onNoteChanged("Year-end bonus")

            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertEquals("Year-end bonus", state.form.note)
        }

    // ---- successful save (add mode) ----

    @Test
    fun onSave_success_addsIncomeWithTrimmedFields_andFiresSavedEvent() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()
            val date = Instant.parse("2026-05-01T10:00:00Z")

            val events = mutableListOf<Unit>()
            val collectJob = launch { viewModel.savedEvent.collect { events.add(it) } }

            viewModel.onSourceChanged("  Freelance  ")
            viewModel.onAmountChanged("49.995") // rounds HALF_UP to 5000 cents
            viewModel.onDateSelected(date)
            viewModel.onNoteChanged("  contract work  ")
            viewModel.onSave()
            advanceUntilIdle()

            assertEquals(1, events.size)
            val stored =
                repo.getIncome(
                    repo
                        .observeIncome(date.minusSeconds(1), date.plusSeconds(1))
                        .first()
                        .single()
                        .id,
                )
            assertNotNull(stored)
            assertEquals("Freelance", stored!!.source)
            assertEquals(5_000L, stored.amountCents)
            assertEquals(date, stored.date)
            assertEquals("contract work", stored.note)

            collectJob.cancel()
        }

    @Test
    fun onSave_success_blankNote_isStoredAsNull() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()

            viewModel.onSourceChanged("Paycheck")
            viewModel.onAmountChanged("3000.00")
            viewModel.onNoteChanged("   ")
            viewModel.onSave()
            advanceUntilIdle()

            val all = repo.observeIncome(Instant.EPOCH, Instant.now().plusSeconds(60)).first()
            assertEquals(1, all.size)
            assertNull(all.single().note)
        }

    // ---- edit mode: initial state ----

    @Test
    fun editMode_existingIncome_prefillsFormAndSetsIsEditMode() =
        runTest(testDispatcher) {
            val existing =
                Income(
                    id = "i1",
                    source = "Salary",
                    amountCents = 500_000L,
                    date = Instant.parse("2026-02-01T00:00:00Z"),
                    note = "Monthly salary",
                )
            val repo = FakeIncomeRepository(listOf(existing))
            val viewModel = editModeViewModel(repo, "i1")

            advanceUntilIdle()

            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertTrue(state.form.isEditMode)
            assertEquals("i1", state.form.incomeId)
            assertEquals("Salary", state.form.source)
            assertEquals("5000.00", state.form.amountText)
            assertEquals(existing.date, state.form.date)
            assertEquals("Monthly salary", state.form.note)
        }

    @Test
    fun editMode_incomeNotFound_isReadyEditModeWithBlankForm() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            val viewModel = editModeViewModel(repo, "does-not-exist")

            advanceUntilIdle()

            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertTrue(state.form.isEditMode)
            assertEquals("does-not-exist", state.form.incomeId)
            assertEquals("", state.form.source)
            assertEquals("", state.form.amountText)
        }

    // ---- edit mode: save ----

    @Test
    fun onSave_editMode_success_callsUpdateIncomeWithSameId_andFiresSavedEvent() =
        runTest(testDispatcher) {
            val existing =
                Income(
                    id = "i1",
                    source = "Salary",
                    amountCents = 500_000L,
                    date = Instant.parse("2026-02-01T00:00:00Z"),
                    note = "Monthly salary",
                )
            val repo = FakeIncomeRepository(listOf(existing))
            val viewModel = editModeViewModel(repo, "i1")
            advanceUntilIdle()

            val events = mutableListOf<Unit>()
            val collectJob = launch { viewModel.savedEvent.collect { events.add(it) } }

            viewModel.onSourceChanged("Salary (raise)")
            viewModel.onAmountChanged("5500.00")
            viewModel.onSave()
            advanceUntilIdle()

            assertEquals(1, events.size)
            val updated = repo.getIncome("i1")
            assertNotNull(updated)
            assertEquals("i1", updated!!.id)
            assertEquals("Salary (raise)", updated.source)
            assertEquals(550_000L, updated.amountCents)
            // repository should still only contain the one (updated, not duplicated) income entry
            val all = repo.observeIncome(Instant.EPOCH, Instant.now().plusSeconds(60)).first()
            assertEquals(1, all.size)

            collectJob.cancel()
        }

    @Test
    fun onSave_editModeFailure_setsSaveErrorFromUpdateIncome() =
        runTest(testDispatcher) {
            val repo =
                TrackingRepository(
                    onAdd = { Result.success("unused") },
                    onUpdate = { Result.failure(NoSuchElementException("Income with id i1 not found")) },
                    getIncomeResult =
                        Income(
                            id = "i1",
                            source = "Salary",
                            amountCents = 500_000L,
                            date = Instant.now(),
                            note = null,
                        ),
                )
            val viewModel = editModeViewModel(repo, "i1")
            advanceUntilIdle()

            viewModel.onSave()
            advanceUntilIdle()

            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertEquals("Income with id i1 not found", state.form.saveError)
        }

    @Test
    fun onSave_editMode_idNoLongerExists_failsGracefully_doesNotFireSavedEvent() =
        runTest(testDispatcher) {
            // The income existed at load time but is deleted by the time Save is pressed (e.g. by
            // another client) -- FakeIncomeRepository.updateIncome fails when the id is missing.
            val existing =
                Income(
                    id = "i1",
                    source = "Salary",
                    amountCents = 500_000L,
                    date = Instant.now(),
                    note = null,
                )
            val repo = FakeIncomeRepository(listOf(existing))
            val viewModel = editModeViewModel(repo, "i1")
            advanceUntilIdle()
            repo.deleteIncome("i1")

            val events = mutableListOf<Unit>()
            val collectJob = launch { viewModel.savedEvent.collect { events.add(it) } }

            viewModel.onSourceChanged("Salary (raise)")
            viewModel.onSave()
            advanceUntilIdle()

            assertTrue(events.isEmpty())
            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertNotNull(state.form.saveError)

            collectJob.cancel()
        }

    // ---- saveError: onSave failure surfaces an error, doesn't fire savedEvent, and is cleared by
    // any subsequent field edit ----

    @Test
    fun onSave_repositoryFailure_setsSaveErrorToExceptionMessage_noSavedEvent() =
        runTest(testDispatcher) {
            val repo = TrackingRepository(onAdd = { Result.failure(IllegalStateException("boom")) })
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()

            val events = mutableListOf<Unit>()
            val collectJob = launch { viewModel.savedEvent.collect { events.add(it) } }

            viewModel.onSourceChanged("Paycheck")
            viewModel.onAmountChanged("3.00")
            viewModel.onSave()
            advanceUntilIdle()

            assertTrue(events.isEmpty())
            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertEquals("boom", state.form.saveError)
            assertNull(state.form.sourceError)
            assertNull(state.form.amountError)

            collectJob.cancel()
        }

    @Test
    fun onSave_repositoryFailure_withNullMessage_fallsBackToDefaultSaveError() =
        runTest(testDispatcher) {
            val repo = TrackingRepository(onAdd = { Result.failure(IllegalStateException()) })
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()

            viewModel.onSourceChanged("Paycheck")
            viewModel.onAmountChanged("3.00")
            viewModel.onSave()
            advanceUntilIdle()

            val state = viewModel.uiState.value as AddEditIncomeUiState.Ready
            assertEquals("Failed to save income", state.form.saveError)
        }

    @Test
    fun onSourceChanged_afterSaveError_clearsSaveError() =
        runTest(testDispatcher) {
            val repo = TrackingRepository(onAdd = { Result.failure(IllegalStateException("boom")) })
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()
            viewModel.onSourceChanged("Paycheck")
            viewModel.onAmountChanged("3.00")
            viewModel.onSave()
            advanceUntilIdle()
            assertNotNull((viewModel.uiState.value as AddEditIncomeUiState.Ready).form.saveError)

            viewModel.onSourceChanged("Paycheck 2")

            assertNull((viewModel.uiState.value as AddEditIncomeUiState.Ready).form.saveError)
        }

    @Test
    fun onAmountChanged_afterSaveError_clearsSaveError() =
        runTest(testDispatcher) {
            val repo = TrackingRepository(onAdd = { Result.failure(IllegalStateException("boom")) })
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()
            viewModel.onSourceChanged("Paycheck")
            viewModel.onAmountChanged("3.00")
            viewModel.onSave()
            advanceUntilIdle()
            assertNotNull((viewModel.uiState.value as AddEditIncomeUiState.Ready).form.saveError)

            viewModel.onAmountChanged("4.00")

            assertNull((viewModel.uiState.value as AddEditIncomeUiState.Ready).form.saveError)
        }

    @Test
    fun onDateSelected_afterSaveError_clearsSaveError() =
        runTest(testDispatcher) {
            val repo = TrackingRepository(onAdd = { Result.failure(IllegalStateException("boom")) })
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()
            viewModel.onSourceChanged("Paycheck")
            viewModel.onAmountChanged("3.00")
            viewModel.onSave()
            advanceUntilIdle()
            assertNotNull((viewModel.uiState.value as AddEditIncomeUiState.Ready).form.saveError)

            viewModel.onDateSelected(Instant.parse("2020-01-01T00:00:00Z"))

            assertNull((viewModel.uiState.value as AddEditIncomeUiState.Ready).form.saveError)
        }

    @Test
    fun onNoteChanged_afterSaveError_clearsSaveError() =
        runTest(testDispatcher) {
            val repo = TrackingRepository(onAdd = { Result.failure(IllegalStateException("boom")) })
            val viewModel = addModeViewModel(repo)
            advanceUntilIdle()
            viewModel.onSourceChanged("Paycheck")
            viewModel.onAmountChanged("3.00")
            viewModel.onSave()
            advanceUntilIdle()
            assertNotNull((viewModel.uiState.value as AddEditIncomeUiState.Ready).form.saveError)

            viewModel.onNoteChanged("a note")

            assertNull((viewModel.uiState.value as AddEditIncomeUiState.Ready).form.saveError)
        }

    /**
     * Minimal local [IncomeRepository] fake whose [addIncome]/[updateIncome] results are
     * controllable, used to assert whether the repository was invoked and/or to force a failure
     * result -- [FakeIncomeRepository] always succeeds on a present id and can't express an
     * arbitrary failure message.
     */
    private class TrackingRepository(
        private val onAdd: () -> Result<String>,
        private val onUpdate: () -> Result<Unit> = { Result.success(Unit) },
        private val getIncomeResult: Income? = null,
    ) : IncomeRepository {
        override fun observeIncome(
            startInclusive: Instant,
            endExclusive: Instant,
        ): Flow<List<Income>> = throw UnsupportedOperationException("unused")

        override suspend fun addIncome(income: Income): Result<String> = onAdd()

        override suspend fun updateIncome(income: Income): Result<Unit> = onUpdate()

        override suspend fun deleteIncome(id: String): Result<Unit> = throw UnsupportedOperationException("unused")

        override suspend fun getIncome(id: String): Income? = getIncomeResult

        override suspend fun getAllIncome(): Result<List<Income>> = throw UnsupportedOperationException("unused")

        override suspend fun addIncomeList(income: List<Income>): Result<Int> = throw UnsupportedOperationException()
    }
}
