package com.joaoeoneves.fintrack.ui.expense.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.testutil.deleteTransactionFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

/** The "Record refund" form opened from Home's Awaiting refund card (route `refundOf` + prefill fields). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditExpenseViewModelRefundPrefillTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val original =
        Expense(
            id = "o1",
            name = "Dentist",
            amountCents = 10_000L,
            category = BuiltInCategories.FOOD,
            date = Instant.parse("2026-01-10T10:00:00Z"),
            note = null,
            awaitingRefund = true,
        )

    private fun viewModel(
        repo: FakeExpenseRepository,
        route: Map<String, Any?>,
    ) = AddEditExpenseViewModel(
        repo,
        FakeCategoryRepository(),
        deleteTransactionFor(repo),
        SavedStateHandle(route),
    )

    private val recordRoute =
        mapOf(
            "refundOf" to "o1",
            "prefillName" to "Dentist",
            "prefillAmountCents" to 7_500L,
            "prefillCategoryId" to BuiltInCategories.FOOD.id.value,
        )

    private fun AddEditExpenseViewModel.form() = (uiState.value as AddEditUiState.Ready).form

    @Test
    fun `the record-refund route opens a refund of the expense with the remaining amount`() =
        runTest(testDispatcher) {
            val before = Instant.now()
            val vm = viewModel(FakeExpenseRepository(listOf(original)), recordRoute)
            advanceUntilIdle()

            val form = vm.form()
            assertTrue(form.isRefund)
            assertEquals("o1", form.refundOf)
            assertEquals("Dentist", form.refundOfLabel)
            assertEquals("Dentist", form.name)
            assertEquals(7_500L, form.amountCents)
            assertEquals(BuiltInCategories.FOOD.id, form.category.id)
            assertTrue(Duration.between(before, form.date).abs() < Duration.ofMinutes(1))
            assertTrue(!form.isEditMode && form.expenseId == null)
        }

    @Test
    fun `nothing is created until the form is saved`() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(original))
            viewModel(repo, recordRoute)
            advanceUntilIdle()

            assertEquals(listOf(original), repo.getAllExpenses().getOrThrow())
        }

    @Test
    fun `saving stores one refund linked to the expense for the remaining amount`() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(original))
            val vm = viewModel(repo, recordRoute)
            advanceUntilIdle()

            vm.onSave()
            advanceUntilIdle()

            val saved = repo.getAllExpenses().getOrThrow().single { it.id != "o1" }
            assertTrue(saved.isRefund)
            assertEquals("o1", saved.refundOf)
            assertEquals(7_500L, saved.amountCents)
            assertEquals(BuiltInCategories.FOOD.id, saved.category.id)
        }

    @Test
    fun `without refundOf the same prefill is a plain expense`() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(original)), recordRoute - "refundOf")
            advanceUntilIdle()

            assertTrue(!vm.form().isRefund)
            assertEquals(null, vm.form().refundOf)
        }
}
