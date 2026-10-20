package com.joaoeoneves.fintrack.ui.income.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
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
 * [AddEditIncomeViewModel] in-flight save guarding (`isSaving`) and locale-aware amount
 * prefill/parsing. Split out of [AddEditIncomeViewModelTest] to keep that class bounded; uses
 * Robolectric for the same `toRoute` / `Bundle` reason documented there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditIncomeViewModelSavingTest {
    private val testDispatcher = StandardTestDispatcher()

    private val existing =
        Income(
            id = "i1",
            source = "Salary",
            amountCents = 1_250L,
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
        repository: IncomeRepository,
        incomeId: String? = null,
    ) = AddEditIncomeViewModel(
        repository,
        SavedStateHandle(if (incomeId != null) mapOf("incomeId" to incomeId) else emptyMap()),
    )

    private fun AddEditIncomeViewModel.form() = (uiState.value as AddEditIncomeUiState.Ready).form

    private fun TestScope.countSavedEvents(viewModel: AddEditIncomeViewModel): () -> Int {
        var count = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.savedEvent.collect { count++ } }
        return { count }
    }

    // ---- re-entrancy guard / isSaving ----

    @Test
    fun backToBackOnSave_whileRepoSuspended_callsAddExactlyOnce() =
        runTest(testDispatcher) {
            val repo = GatedIncomeRepository()
            val vm = viewModel(repo)
            val savedCount = countSavedEvents(vm)
            advanceUntilIdle()
            vm.onSourceChanged("Coffee")
            vm.onAmountChanged("3.50")

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
                    .getAllIncome()
                    .getOrThrow()
                    .size,
            )
        }

    @Test
    fun onSave_setsIsSavingSynchronously_beforeRepoCallDispatches() =
        runTest(testDispatcher) {
            val repo = GatedIncomeRepository()
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onSourceChanged("Coffee")
            vm.onAmountChanged("3.50")
            assertFalse(vm.form().isSaving)

            vm.onSave()

            assertTrue(vm.form().isSaving)
            assertEquals(0, repo.addCalls)
        }

    @Test
    fun onSave_success_keepsIsSavingTrue_andFurtherSavesAreIgnored() =
        runTest(testDispatcher) {
            val repo = GatedIncomeRepository().apply { gate.complete(Unit) }
            val vm = viewModel(repo)
            val savedCount = countSavedEvents(vm)
            advanceUntilIdle()
            vm.onSourceChanged("Coffee")
            vm.onAmountChanged("3.50")

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
            val repo = GatedIncomeRepository(nextFailure = IllegalStateException("offline"))
            val vm = viewModel(repo)
            val savedCount = countSavedEvents(vm)
            advanceUntilIdle()
            vm.onSourceChanged("Coffee")
            vm.onAmountChanged("3.50")

            vm.onSave()
            advanceUntilIdle()
            assertTrue(vm.form().isSaving)
            repo.gate.complete(Unit)
            advanceUntilIdle()

            assertFalse(vm.form().isSaving)
            assertEquals(UiText.Dynamic("offline"), vm.form().saveError)
            assertEquals(0, savedCount())

            vm.onSave()
            advanceUntilIdle()

            assertEquals(2, repo.addCalls)
            assertEquals(1, savedCount())
            assertEquals(
                1,
                repo.delegate
                    .getAllIncome()
                    .getOrThrow()
                    .size,
            )
        }

    @Test
    fun onSave_validationFailure_doesNotSetIsSaving_orCallRepo() =
        runTest(testDispatcher) {
            val repo = GatedIncomeRepository()
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onSourceChanged("Coffee")
            vm.onAmountChanged("1.234,50")

            vm.onSave()
            advanceUntilIdle()

            assertFalse(vm.form().isSaving)
            assertNotNull(vm.form().amountError)
            assertEquals(0, repo.addCalls)
        }

    @Test
    fun editMode_backToBackOnSave_callsUpdateExactlyOnce() =
        runTest(testDispatcher) {
            val repo = GatedIncomeRepository(FakeIncomeRepository(listOf(existing)))
            val vm = viewModel(repo, incomeId = "i1")
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
    fun editPrefill_ptPtLocale_usesCommaSeparator() =
        runTest(testDispatcher) {
            useLocale("pt-PT")
            val vm = viewModel(FakeIncomeRepository(listOf(existing)), "i1")
            advanceUntilIdle()

            assertEquals("12,50", vm.form().amountText)
        }

    @Test
    fun editPrefill_enUsLocale_usesDotSeparator() =
        runTest(testDispatcher) {
            useLocale("en-US")
            val vm = viewModel(FakeIncomeRepository(listOf(existing)), "i1")
            advanceUntilIdle()

            assertEquals("12.50", vm.form().amountText)
        }

    @Test
    fun editPrefill_followsFormatLocale_notDisplayLocale() =
        runTest(testDispatcher) {
            Locale.setDefault(Locale.US)
            Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("pt-PT"))
            val vm = viewModel(FakeIncomeRepository(listOf(existing)), "i1")
            advanceUntilIdle()

            assertEquals("12,50", vm.form().amountText)
        }

    @Test
    fun editPrefill_ptPt_savedUnchanged_roundTripsToSameCents() =
        runTest(testDispatcher) {
            useLocale("pt-PT")
            val repo = FakeIncomeRepository(listOf(existing))
            val vm = viewModel(repo, "i1")
            advanceUntilIdle()

            vm.onSave()
            advanceUntilIdle()

            assertNull(vm.form().saveError)
            assertEquals(1_250L, repo.getIncome("i1").getOrThrow()?.amountCents)
        }

    @Test
    fun addMode_savingCommaAmount_stores1250Cents() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onSourceChanged("Lunch")
            vm.onAmountChanged("12,50")

            vm.onSave()
            advanceUntilIdle()

            assertEquals(listOf(1_250L), repo.getAllIncome().getOrThrow().map { it.amountCents })
        }

    /**
     * Delegates to a [FakeIncomeRepository] but suspends [addIncome]/[updateIncome] on [gate]
     * (so a save can be observed in flight) and counts calls. [nextFailure], if set, is returned
     * once (after the gate) instead of delegating.
     */
    private class GatedIncomeRepository(
        val delegate: FakeIncomeRepository = FakeIncomeRepository(),
        private var nextFailure: Throwable? = null,
    ) : IncomeRepository by delegate {
        val gate = CompletableDeferred<Unit>()
        var addCalls = 0
            private set
        var updateCalls = 0
            private set

        override suspend fun addIncome(income: Income): Result<String> {
            addCalls++
            gate.await()
            return consumeFailure() ?: delegate.addIncome(income)
        }

        override suspend fun updateIncome(income: Income): Result<Unit> {
            updateCalls++
            gate.await()
            return consumeFailure() ?: delegate.updateIncome(income)
        }

        private fun <T> consumeFailure(): Result<T>? =
            nextFailure?.let { error ->
                nextFailure = null
                Result.failure(error)
            }
    }
}
