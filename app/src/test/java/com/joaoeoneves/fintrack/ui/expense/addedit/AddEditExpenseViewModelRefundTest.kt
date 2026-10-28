package com.joaoeoneves.fintrack.ui.expense.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MAX_NOTE_LENGTH
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * [AddEditExpenseViewModel] refund toggle (prefill, save, error clearing) and name/note length
 * limits. Split out of [AddEditExpenseViewModelTest] to keep that class bounded; Robolectric for
 * the `toRoute` / `Bundle` reason documented there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditExpenseViewModelRefundTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val existingRefund =
        Expense(
            id = "r1",
            name = "Shoes return",
            amountCents = 4_999L,
            category = BuiltInCategories.SHOPPING,
            date = Instant.parse("2026-02-01T10:00:00Z"),
            note = null,
            isRefund = true,
        )

    /** Fails every single-add, to drive the saveError path. */
    private class FailingAddRepository(
        private val delegate: FakeExpenseRepository = FakeExpenseRepository(),
    ) : ExpenseRepository by delegate {
        private val offline = IllegalStateException("offline")

        override suspend fun addExpense(expense: Expense): Result<String> = Result.failure(offline)
    }

    private fun viewModel(
        repository: ExpenseRepository,
        expenseId: String? = null,
    ) = AddEditExpenseViewModel(
        repository,
        FakeCategoryRepository(),
        SavedStateHandle(if (expenseId != null) mapOf("expenseId" to expenseId) else emptyMap()),
    )

    private fun AddEditExpenseViewModel.form() = (uiState.value as AddEditUiState.Ready).form

    // ---- refund toggle ----

    @Test
    fun addMode_isRefund_defaultsToFalse() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository())
            advanceUntilIdle()

            assertFalse(vm.form().isRefund)
        }

    @Test
    fun onRefundChanged_togglesFlag() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository())
            advanceUntilIdle()

            vm.onRefundChanged(true)
            assertTrue(vm.form().isRefund)
            vm.onRefundChanged(false)
            assertFalse(vm.form().isRefund)
        }

    @Test
    fun onRefundChanged_clearsSaveError() =
        runTest(testDispatcher) {
            val vm = viewModel(FailingAddRepository())
            advanceUntilIdle()
            vm.onNameChanged("Return")
            vm.onAmountChanged(1000L)
            vm.onSave()
            advanceUntilIdle()
            assertNotNull(vm.form().saveError)

            vm.onRefundChanged(true)

            assertNull(vm.form().saveError)
        }

    @Test
    fun onSave_addMode_refund_persistsIsRefundWithPositiveAmount() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onNameChanged("Shoes return")
            vm.onAmountChanged(4900L)
            vm.onRefundChanged(true)

            vm.onSave()
            advanceUntilIdle()

            val saved = repo.getAllExpenses().getOrThrow().single()
            assertTrue(saved.isRefund)
            assertEquals(4_900L, saved.amountCents)
        }

    @Test
    fun onSave_addMode_withoutToggle_isNotRefund() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onNameChanged("Coffee")
            vm.onAmountChanged(300L)

            vm.onSave()
            advanceUntilIdle()

            assertFalse(
                repo
                    .getAllExpenses()
                    .getOrThrow()
                    .single()
                    .isRefund,
            )
        }

    @Test
    fun editMode_prefillsIsRefund_fromExistingExpense() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(existingRefund)), expenseId = "r1")
            advanceUntilIdle()

            assertTrue(vm.form().isRefund)
            assertEquals(4999L, vm.form().amountCents)
        }

    @Test
    fun editMode_unTogglingRefund_savesAsRegularExpense() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(existingRefund))
            val vm = viewModel(repo, expenseId = "r1")
            advanceUntilIdle()

            vm.onRefundChanged(false)
            vm.onSave()
            advanceUntilIdle()

            val updated = repo.getExpense("r1").getOrThrow()
            assertEquals(false, updated?.isRefund)
            assertEquals(4_999L, updated?.amountCents)
        }

    // ---- length limits ----

    @Test
    fun onNameChanged_withinLimit_isAccepted_andAtLimitFurtherTypingIsRejected() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository())
            advanceUntilIdle()
            val atLimit = "a".repeat(MAX_NAME_LENGTH)

            vm.onNameChanged(atLimit)
            assertEquals(atLimit, vm.form().name)

            vm.onNameChanged(atLimit + "b")
            assertEquals(atLimit, vm.form().name)
        }

    @Test
    fun onNameChanged_pasteOverLimit_isTruncated() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository())
            advanceUntilIdle()

            vm.onNameChanged("x".repeat(MAX_NAME_LENGTH + 50))

            assertEquals("x".repeat(MAX_NAME_LENGTH), vm.form().name)
        }

    @Test
    fun onNoteChanged_pasteOverLimit_isTruncated_andAtLimitFurtherTypingIsRejected() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository())
            advanceUntilIdle()

            vm.onNoteChanged("n".repeat(MAX_NOTE_LENGTH + 1))
            assertEquals("n".repeat(MAX_NOTE_LENGTH), vm.form().note)

            vm.onNoteChanged("n".repeat(MAX_NOTE_LENGTH) + "m")
            assertEquals("n".repeat(MAX_NOTE_LENGTH), vm.form().note)
        }
}
