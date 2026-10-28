package com.joaoeoneves.fintrack.ui.expense.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.testutil.deleteTransactionFor
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.util.Locale

/**
 * [AddEditExpenseViewModel] in-flight save guarding (`isSaving`) and locale-aware amount
 * prefill/parsing. Split out of [AddEditExpenseViewModelTest] to keep that class bounded; uses
 * Robolectric for the same `toRoute` / `Bundle` reason documented there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditExpenseViewModelSavingTest {
    private val testDispatcher = StandardTestDispatcher()

    private val existing =
        Expense(
            id = "e1",
            name = "Groceries",
            amountCents = 1_250L,
            category = BuiltInCategories.SHOPPING,
            date = Instant.parse("2026-02-01T10:00:00Z"),
            note = null,
        )

    private val originalLocale: Locale = Locale.getDefault()
    private val originalFormatLocale: Locale = Locale.getDefault(Locale.Category.FORMAT)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(originalLocale)
        Locale.setDefault(Locale.Category.FORMAT, originalFormatLocale)
    }

    /** Edit-mode prefill follows the default FORMAT locale; `setDefault(Locale)` also sets it. */
    private fun useLocale(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    private fun viewModel(
        repository: ExpenseRepository,
        expenseId: String? = null,
    ) = AddEditExpenseViewModel(
        repository,
        FakeCategoryRepository(),
        deleteTransactionFor(repository),
        SavedStateHandle(if (expenseId != null) mapOf("expenseId" to expenseId) else emptyMap()),
    )

    private fun AddEditExpenseViewModel.form() = (uiState.value as AddEditUiState.Ready).form

    private fun TestScope.countSavedEvents(viewModel: AddEditExpenseViewModel): () -> Int {
        var count = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.savedEvent.collect { count++ } }
        return { count }
    }

    // ---- re-entrancy guard / isSaving ----

    @Test
    fun backToBackOnSave_whileRepoSuspended_callsAddExactlyOnce() =
        runTest(testDispatcher) {
            val repo = GatedExpenseRepository()
            val vm = viewModel(repo)
            val savedCount = countSavedEvents(vm)
            advanceUntilIdle()
            vm.onNameChanged("Coffee")
            vm.onAmountChanged(350L)

            vm.onSave()
            vm.onSave()
            advanceUntilIdle()

            assertEquals(1, repo.addCalls)
            assertTrue(vm.form().isSaving)
            assertEquals(0, savedCount())

            repo.gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, repo.addCalls)
            assertEquals(1, savedCount())
            assertEquals(
                1,
                repo.delegate
                    .getAllExpenses()
                    .getOrThrow()
                    .size,
            )
        }

    @Test
    fun onSave_setsIsSavingSynchronously_beforeRepoCallDispatches() =
        runTest(testDispatcher) {
            val repo = GatedExpenseRepository()
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onNameChanged("Coffee")
            vm.onAmountChanged(350L)
            assertFalse(vm.form().isSaving)

            vm.onSave()

            assertTrue(vm.form().isSaving)
            assertEquals(0, repo.addCalls)
        }

    @Test
    fun onSave_success_keepsIsSavingTrue_andFurtherSavesAreIgnored() =
        runTest(testDispatcher) {
            val repo = GatedExpenseRepository().apply { gate.complete(Unit) }
            val vm = viewModel(repo)
            val savedCount = countSavedEvents(vm)
            advanceUntilIdle()
            vm.onNameChanged("Coffee")
            vm.onAmountChanged(350L)

            vm.onSave()
            advanceUntilIdle()
            vm.onSave()
            advanceUntilIdle()

            assertTrue(vm.form().isSaving)
            assertEquals(1, repo.addCalls)
            assertEquals(1, savedCount())
        }

    @Test
    fun onSave_failure_resetsIsSaving_setsSaveError_andRetryCallsRepoAgain() =
        runTest(testDispatcher) {
            val repo = GatedExpenseRepository(nextFailure = IllegalStateException("offline"))
            val vm = viewModel(repo)
            val savedCount = countSavedEvents(vm)
            advanceUntilIdle()
            vm.onNameChanged("Coffee")
            vm.onAmountChanged(350L)

            vm.onSave()
            advanceUntilIdle()
            assertTrue(vm.form().isSaving)
            repo.gate.complete(Unit)
            advanceUntilIdle()

            assertFalse(vm.form().isSaving)
            assertEquals(UiText.StringResource(R.string.error_failed_save_expense), vm.form().saveError)
            assertEquals(0, savedCount())

            vm.onSave()
            advanceUntilIdle()

            assertEquals(2, repo.addCalls)
            assertEquals(1, savedCount())
            assertEquals(
                1,
                repo.delegate
                    .getAllExpenses()
                    .getOrThrow()
                    .size,
            )
        }

    @Test
    fun onSave_validationFailure_doesNotSetIsSaving_orCallRepo() =
        runTest(testDispatcher) {
            val repo = GatedExpenseRepository()
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onNameChanged("Coffee")
            vm.onAmountChanged(0L)

            vm.onSave()
            advanceUntilIdle()

            assertFalse(vm.form().isSaving)
            assertNotNull(vm.form().amountError)
            assertEquals(0, repo.addCalls)
        }

    @Test
    fun editMode_backToBackOnSave_callsUpdateExactlyOnce() =
        runTest(testDispatcher) {
            val repo = GatedExpenseRepository(FakeExpenseRepository(listOf(existing)))
            val vm = viewModel(repo, expenseId = "e1")
            advanceUntilIdle()

            vm.onSave()
            vm.onSave()
            repo.gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, repo.updateCalls)
            assertEquals(0, repo.addCalls)
        }

    // ---- locale-aware prefill & parsing ----

    @Test
    fun editPrefill_ptPtLocale_usesStoredCentsExactly() =
        runTest(testDispatcher) {
            useLocale("pt-PT")
            val vm = viewModel(FakeExpenseRepository(listOf(existing)), "e1")
            advanceUntilIdle()

            assertEquals(1250L, vm.form().amountCents)
        }

    @Test
    fun editPrefill_enUsLocale_usesStoredCentsExactly() =
        runTest(testDispatcher) {
            useLocale("en-US")
            val vm = viewModel(FakeExpenseRepository(listOf(existing)), "e1")
            advanceUntilIdle()

            assertEquals(1250L, vm.form().amountCents)
        }

    @Test
    fun editPrefill_ignoresFormatLocale_usesStoredCents() =
        runTest(testDispatcher) {
            Locale.setDefault(Locale.US)
            Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("pt-PT"))
            val vm = viewModel(FakeExpenseRepository(listOf(existing)), "e1")
            advanceUntilIdle()

            assertEquals(1250L, vm.form().amountCents)
        }

    @Test
    fun editPrefill_ptPt_savedUnchanged_roundTripsToSameCents() =
        runTest(testDispatcher) {
            useLocale("pt-PT")
            val repo = FakeExpenseRepository(listOf(existing))
            val vm = viewModel(repo, "e1")
            advanceUntilIdle()

            vm.onSave()
            advanceUntilIdle()

            assertNull(vm.form().saveError)
            assertEquals(1_250L, repo.getExpense("e1").getOrThrow()?.amountCents)
        }

    @Test
    fun addMode_typedCents_stores1250Cents() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onNameChanged("Lunch")
            vm.onAmountChanged(1_250L)

            vm.onSave()
            advanceUntilIdle()

            assertEquals(listOf(1_250L), repo.getAllExpenses().getOrThrow().map { it.amountCents })
        }

    /**
     * Delegates to a [FakeExpenseRepository] but suspends [addExpense]/[updateExpense] on [gate]
     * (so a save can be observed in flight) and counts calls. [nextFailure], if set, is returned
     * once (after the gate) instead of delegating.
     */
    private class GatedExpenseRepository(
        val delegate: FakeExpenseRepository = FakeExpenseRepository(),
        private var nextFailure: Throwable? = null,
    ) : ExpenseRepository by delegate {
        val gate = CompletableDeferred<Unit>()
        var addCalls = 0
            private set
        var updateCalls = 0
            private set

        override suspend fun addExpense(expense: Expense): Result<String> {
            addCalls++
            gate.await()
            return consumeFailure() ?: delegate.addExpense(expense)
        }

        override suspend fun updateExpense(expense: Expense): Result<Unit> {
            updateCalls++
            gate.await()
            return consumeFailure() ?: delegate.updateExpense(expense)
        }

        private fun <T> consumeFailure(): Result<T>? =
            nextFailure?.let { error ->
                nextFailure = null
                Result.failure(error)
            }
    }
}
