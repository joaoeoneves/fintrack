package ptech.joaoe.agenticusage.ui.expense.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import java.time.Instant
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ptech.joaoe.agenticusage.data.FakeExpenseRepository
import ptech.joaoe.agenticusage.domain.model.Expense
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory
import ptech.joaoe.agenticusage.domain.repository.ExpenseRepository

/**
 * Unit tests for [AddEditExpenseViewModel].
 *
 * [AddEditExpenseViewModel]'s constructor eagerly calls
 * `savedStateHandle.toRoute<AddEditExpense>()`. On a plain JVM unit test (no Robolectric), any
 * *present* value in the [SavedStateHandle] -- e.g. a non-null `expenseId` for edit mode --
 * causes `androidx.navigation.serialization.RouteDecoder` to internally call
 * `androidx.core.os.BundleKt.bundleOf`, which calls `android.os.Bundle.putCharSequence`. That
 * method is not stubbed by the Android Gradle Plugin's "mockable" android.jar used for JVM unit
 * tests, so it throws `RuntimeException: Method putCharSequence in android.os.Bundle not
 * mocked.` (confirmed empirically; see this class's history / QA report).
 *
 * Running under [RobolectricTestRunner] provides a real (shadowed) `android.os.Bundle`
 * implementation, so `SavedStateHandle(mapOf("expenseId" to id))` now round-trips correctly
 * through `toRoute<AddEditExpense>()`, which unblocks testing edit mode (pre-fill from an
 * existing expense, and the not-found-id edge case) alongside add mode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditExpenseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun addModeViewModel(repository: ExpenseRepository) =
        AddEditExpenseViewModel(repository, SavedStateHandle())

    private fun editModeViewModel(repository: ExpenseRepository, expenseId: String) =
        AddEditExpenseViewModel(repository, SavedStateHandle(mapOf("expenseId" to expenseId)))

    // ---- add mode: initial state ----

    @Test
    fun addMode_initialState_isReadyWithBlankForm_notEditMode() = runTest(testDispatcher) {
        val repo = FakeExpenseRepository()
        val viewModel = addModeViewModel(repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertFalse(state.form.isEditMode)
        assertEquals("", state.form.name)
        assertEquals("", state.form.amountText)
        assertEquals(ExpenseCategory.SHOPPING, state.form.category)
        assertEquals("", state.form.note)
        assertNull(state.form.nameError)
        assertNull(state.form.amountError)
        assertNull(state.form.expenseId)
    }

    @Test
    fun addMode_initialState_defaultsDateToNow() = runTest(testDispatcher) {
        val before = Instant.now()
        val repo = FakeExpenseRepository()
        val viewModel = addModeViewModel(repo)

        advanceUntilIdle()
        val after = Instant.now()

        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertFalse(state.form.date.isBefore(before))
        assertFalse(state.form.date.isAfter(after))
    }

    // ---- validation ----

    @Test
    fun onSave_blankName_setsNameError_doesNotCallRepository() = runTest(testDispatcher) {
        var addCalled = false
        val repo = TrackingRepository(onAdd = { addCalled = true; Result.success("id") })
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()

        viewModel.onAmountChanged("12.50")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertEquals("Name is required", state.form.nameError)
        assertFalse(addCalled)
    }

    @Test
    fun onSave_invalidAmount_setsAmountError_doesNotCallRepository() = runTest(testDispatcher) {
        var addCalled = false
        val repo = TrackingRepository(onAdd = { addCalled = true; Result.success("id") })
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()

        viewModel.onNameChanged("Coffee")
        viewModel.onAmountChanged("abc")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertEquals("Enter a valid amount", state.form.amountError)
        assertFalse(addCalled)
    }

    @Test
    fun onSave_zeroAmount_setsAmountError() = runTest(testDispatcher) {
        val repo = FakeExpenseRepository()
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()

        viewModel.onNameChanged("Coffee")
        viewModel.onAmountChanged("0")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertEquals("Enter a valid amount", state.form.amountError)
    }

    @Test
    fun onSave_blankNameAndInvalidAmount_setsBothErrors() = runTest(testDispatcher) {
        val repo = FakeExpenseRepository()
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertEquals("Name is required", state.form.nameError)
        assertEquals("Enter a valid amount", state.form.amountError)
    }

    @Test
    fun onNameChanged_clearsPriorNameError() = runTest(testDispatcher) {
        val repo = FakeExpenseRepository()
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()
        viewModel.onSave() // triggers nameError
        advanceUntilIdle()
        assertNotNull((viewModel.uiState.value as AddEditUiState.Ready).form.nameError)

        viewModel.onNameChanged("Coffee")

        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertNull(state.form.nameError)
        assertEquals("Coffee", state.form.name)
    }

    @Test
    fun onAmountChanged_clearsPriorAmountError() = runTest(testDispatcher) {
        val repo = FakeExpenseRepository()
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()
        viewModel.onNameChanged("Coffee")
        viewModel.onSave() // triggers amountError (blank amount)
        advanceUntilIdle()
        assertNotNull((viewModel.uiState.value as AddEditUiState.Ready).form.amountError)

        viewModel.onAmountChanged("5.00")

        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertNull(state.form.amountError)
        assertEquals("5.00", state.form.amountText)
    }

    // ---- form field updates ----

    @Test
    fun onCategorySelected_updatesCategory() = runTest(testDispatcher) {
        val repo = FakeExpenseRepository()
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()

        viewModel.onCategorySelected(ExpenseCategory.RECURRING)

        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertEquals(ExpenseCategory.RECURRING, state.form.category)
    }

    @Test
    fun onDateSelected_updatesDate() = runTest(testDispatcher) {
        val repo = FakeExpenseRepository()
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()
        val newDate = Instant.parse("2020-01-01T00:00:00Z")

        viewModel.onDateSelected(newDate)

        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertEquals(newDate, state.form.date)
    }

    @Test
    fun onNoteChanged_updatesNote() = runTest(testDispatcher) {
        val repo = FakeExpenseRepository()
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()

        viewModel.onNoteChanged("Business lunch")

        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertEquals("Business lunch", state.form.note)
    }

    // ---- successful save (add mode) ----

    @Test
    fun onSave_success_addsExpenseWithTrimmedFields_andFiresSavedEvent() = runTest(testDispatcher) {
        val repo = FakeExpenseRepository()
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()
        val date = Instant.parse("2026-05-01T10:00:00Z")

        val events = mutableListOf<Unit>()
        val collectJob = launch { viewModel.savedEvent.collect { events.add(it) } }

        viewModel.onNameChanged("  Coffee  ")
        viewModel.onAmountChanged("4.995") // rounds HALF_UP to 500 cents
        viewModel.onCategorySelected(ExpenseCategory.RECURRING)
        viewModel.onDateSelected(date)
        viewModel.onNoteChanged("  with milk  ")
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(1, events.size)
        val stored = repo.getExpense(repo.observeExpenses(date.minusSeconds(1), date.plusSeconds(1)).first().single().id)
        assertNotNull(stored)
        assertEquals("Coffee", stored!!.name)
        assertEquals(500L, stored.amountCents)
        assertEquals(ExpenseCategory.RECURRING, stored.category)
        assertEquals(date, stored.date)
        assertEquals("with milk", stored.note)

        collectJob.cancel()
    }

    @Test
    fun onSave_success_blankNote_isStoredAsNull() = runTest(testDispatcher) {
        val repo = FakeExpenseRepository()
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()

        viewModel.onNameChanged("Coffee")
        viewModel.onAmountChanged("3.00")
        viewModel.onNoteChanged("   ")
        viewModel.onSave()
        advanceUntilIdle()

        val all = repo.observeExpenses(Instant.EPOCH, Instant.now().plusSeconds(60)).first()
        assertEquals(1, all.size)
        assertNull(all.single().note)
    }

    // ---- edit mode: initial state ----

    @Test
    fun editMode_existingExpense_prefillsFormAndSetsIsEditMode() = runTest(testDispatcher) {
        val existing = Expense(
            id = "e1",
            name = "Netflix",
            amountCents = 1_599L,
            category = ExpenseCategory.RECURRING,
            date = Instant.parse("2026-02-01T00:00:00Z"),
            note = "Monthly subscription"
        )
        val repo = FakeExpenseRepository(listOf(existing))
        val viewModel = editModeViewModel(repo, "e1")

        advanceUntilIdle()

        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertTrue(state.form.isEditMode)
        assertEquals("e1", state.form.expenseId)
        assertEquals("Netflix", state.form.name)
        assertEquals("15.99", state.form.amountText)
        assertEquals(ExpenseCategory.RECURRING, state.form.category)
        assertEquals(existing.date, state.form.date)
        assertEquals("Monthly subscription", state.form.note)
    }

    @Test
    fun editMode_expenseNotFound_isReadyEditModeWithBlankForm() = runTest(testDispatcher) {
        val repo = FakeExpenseRepository()
        val viewModel = editModeViewModel(repo, "does-not-exist")

        advanceUntilIdle()

        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertTrue(state.form.isEditMode)
        assertEquals("does-not-exist", state.form.expenseId)
        assertEquals("", state.form.name)
        assertEquals("", state.form.amountText)
    }

    // ---- edit mode: save ----

    @Test
    fun onSave_editMode_success_callsUpdateExpenseWithSameId_andFiresSavedEvent() = runTest(testDispatcher) {
        val existing = Expense(
            id = "e1",
            name = "Netflix",
            amountCents = 1_599L,
            category = ExpenseCategory.RECURRING,
            date = Instant.parse("2026-02-01T00:00:00Z"),
            note = "Monthly subscription"
        )
        val repo = FakeExpenseRepository(listOf(existing))
        val viewModel = editModeViewModel(repo, "e1")
        advanceUntilIdle()

        val events = mutableListOf<Unit>()
        val collectJob = launch { viewModel.savedEvent.collect { events.add(it) } }

        viewModel.onNameChanged("Netflix Premium")
        viewModel.onAmountChanged("19.99")
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(1, events.size)
        val updated = repo.getExpense("e1")
        assertNotNull(updated)
        assertEquals("e1", updated!!.id)
        assertEquals("Netflix Premium", updated.name)
        assertEquals(1_999L, updated.amountCents)
        // repository should still only contain the one (updated, not duplicated) expense
        val all = repo.observeExpenses(Instant.EPOCH, Instant.now().plusSeconds(60)).first()
        assertEquals(1, all.size)

        collectJob.cancel()
    }

    @Test
    fun onSave_editMode_idNoLongerExists_failsGracefully_doesNotFireSavedEvent() = runTest(testDispatcher) {
        // The expense existed at load time but is deleted by the time Save is pressed (e.g. by
        // another client) -- FakeExpenseRepository.updateExpense fails when the id is missing.
        val existing = Expense(
            id = "e1",
            name = "Netflix",
            amountCents = 1_599L,
            category = ExpenseCategory.RECURRING,
            date = Instant.now(),
            note = null
        )
        val repo = FakeExpenseRepository(listOf(existing))
        val viewModel = editModeViewModel(repo, "e1")
        advanceUntilIdle()
        repo.deleteExpense("e1")

        val events = mutableListOf<Unit>()
        val collectJob = launch { viewModel.savedEvent.collect { events.add(it) } }

        viewModel.onNameChanged("Netflix Premium")
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertNotNull(state.form.saveError)

        collectJob.cancel()
    }

    // ---- saveError fix: onSave failure surfaces an error, doesn't fire savedEvent, and is
    // cleared by any subsequent field edit ----

    @Test
    fun onSave_repositoryFailure_setsSaveErrorToExceptionMessage_noSavedEvent() = runTest(testDispatcher) {
        val repo = TrackingRepository(onAdd = { Result.failure(IllegalStateException("boom")) })
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()

        val events = mutableListOf<Unit>()
        val collectJob = launch { viewModel.savedEvent.collect { events.add(it) } }

        viewModel.onNameChanged("Coffee")
        viewModel.onAmountChanged("3.00")
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertEquals("boom", state.form.saveError)
        assertNull(state.form.nameError)
        assertNull(state.form.amountError)

        collectJob.cancel()
    }

    @Test
    fun onSave_repositoryFailure_withNullMessage_fallsBackToDefaultSaveError() = runTest(testDispatcher) {
        val repo = TrackingRepository(onAdd = { Result.failure(IllegalStateException()) })
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()

        viewModel.onNameChanged("Coffee")
        viewModel.onAmountChanged("3.00")
        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertEquals("Failed to save expense", state.form.saveError)
    }

    @Test
    fun onSave_editModeFailure_setsSaveErrorFromUpdateExpense() = runTest(testDispatcher) {
        val repo = TrackingRepository(
            onAdd = { Result.success("unused") },
            onUpdate = { Result.failure(NoSuchElementException("Expense with id e1 not found")) },
            getExpenseResult = Expense(
                id = "e1",
                name = "Netflix",
                amountCents = 1_599L,
                category = ExpenseCategory.RECURRING,
                date = Instant.now(),
                note = null
            )
        )
        val viewModel = editModeViewModel(repo, "e1")
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value as AddEditUiState.Ready
        assertEquals("Expense with id e1 not found", state.form.saveError)
    }

    @Test
    fun onNameChanged_afterSaveError_clearsSaveError() = runTest(testDispatcher) {
        val repo = TrackingRepository(onAdd = { Result.failure(IllegalStateException("boom")) })
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()
        viewModel.onNameChanged("Coffee")
        viewModel.onAmountChanged("3.00")
        viewModel.onSave()
        advanceUntilIdle()
        assertNotNull((viewModel.uiState.value as AddEditUiState.Ready).form.saveError)

        viewModel.onNameChanged("Coffee 2")

        assertNull((viewModel.uiState.value as AddEditUiState.Ready).form.saveError)
    }

    @Test
    fun onAmountChanged_afterSaveError_clearsSaveError() = runTest(testDispatcher) {
        val repo = TrackingRepository(onAdd = { Result.failure(IllegalStateException("boom")) })
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()
        viewModel.onNameChanged("Coffee")
        viewModel.onAmountChanged("3.00")
        viewModel.onSave()
        advanceUntilIdle()
        assertNotNull((viewModel.uiState.value as AddEditUiState.Ready).form.saveError)

        viewModel.onAmountChanged("4.00")

        assertNull((viewModel.uiState.value as AddEditUiState.Ready).form.saveError)
    }

    @Test
    fun onCategorySelected_afterSaveError_clearsSaveError() = runTest(testDispatcher) {
        val repo = TrackingRepository(onAdd = { Result.failure(IllegalStateException("boom")) })
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()
        viewModel.onNameChanged("Coffee")
        viewModel.onAmountChanged("3.00")
        viewModel.onSave()
        advanceUntilIdle()
        assertNotNull((viewModel.uiState.value as AddEditUiState.Ready).form.saveError)

        viewModel.onCategorySelected(ExpenseCategory.TRANSFER)

        assertNull((viewModel.uiState.value as AddEditUiState.Ready).form.saveError)
    }

    @Test
    fun onDateSelected_afterSaveError_clearsSaveError() = runTest(testDispatcher) {
        val repo = TrackingRepository(onAdd = { Result.failure(IllegalStateException("boom")) })
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()
        viewModel.onNameChanged("Coffee")
        viewModel.onAmountChanged("3.00")
        viewModel.onSave()
        advanceUntilIdle()
        assertNotNull((viewModel.uiState.value as AddEditUiState.Ready).form.saveError)

        viewModel.onDateSelected(Instant.parse("2020-01-01T00:00:00Z"))

        assertNull((viewModel.uiState.value as AddEditUiState.Ready).form.saveError)
    }

    @Test
    fun onNoteChanged_afterSaveError_clearsSaveError() = runTest(testDispatcher) {
        val repo = TrackingRepository(onAdd = { Result.failure(IllegalStateException("boom")) })
        val viewModel = addModeViewModel(repo)
        advanceUntilIdle()
        viewModel.onNameChanged("Coffee")
        viewModel.onAmountChanged("3.00")
        viewModel.onSave()
        advanceUntilIdle()
        assertNotNull((viewModel.uiState.value as AddEditUiState.Ready).form.saveError)

        viewModel.onNoteChanged("a note")

        assertNull((viewModel.uiState.value as AddEditUiState.Ready).form.saveError)
    }

    /**
     * Minimal local [ExpenseRepository] fake whose [addExpense]/[updateExpense] results are
     * controllable, used to assert whether the repository was invoked and/or to force a failure
     * result -- [FakeExpenseRepository] always succeeds on a present id and can't express an
     * arbitrary failure message.
     */
    private class TrackingRepository(
        private val onAdd: () -> Result<String>,
        private val onUpdate: () -> Result<Unit> = { Result.success(Unit) },
        private val getExpenseResult: Expense? = null
    ) : ExpenseRepository {
        override fun observeExpenses(startInclusive: Instant, endExclusive: Instant): Flow<List<Expense>> {
            throw UnsupportedOperationException("not used in these tests")
        }

        override suspend fun addExpense(expense: Expense): Result<String> = onAdd()

        override suspend fun updateExpense(expense: Expense): Result<Unit> = onUpdate()

        override suspend fun deleteExpense(id: String): Result<Unit> {
            throw UnsupportedOperationException("not used in these tests")
        }

        override suspend fun getExpense(id: String): Expense? = getExpenseResult

        override suspend fun getAllExpenses(): Result<List<Expense>> {
            throw UnsupportedOperationException("not used in these tests")
        }

        override suspend fun addExpenses(expenses: List<Expense>): Result<Int> {
            throw UnsupportedOperationException("not used in these tests")
        }
    }
}
