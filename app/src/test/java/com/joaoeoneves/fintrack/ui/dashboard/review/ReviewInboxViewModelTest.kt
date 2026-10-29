package com.joaoeoneves.fintrack.ui.dashboard.review

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.testutil.deleteTransactionFor
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReviewInboxViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val day = Instant.parse("2026-10-10T12:00:00Z")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun flagged(id: String) = testExpense(id, 1_000, day).copy(needsReview = true)

    private fun flaggedIncome(id: String) = testIncome(id, 2_000, day).copy(needsReview = true)

    private fun TestScope.vm(
        expenses: ExpenseRepository = FakeExpenseRepository(),
        incomes: FakeIncomeRepository = FakeIncomeRepository(),
    ): ReviewInboxViewModel {
        val model = ReviewInboxViewModel(expenses, incomes, deleteTransactionFor(expenses, incomes))
        backgroundScope.launch { model.uiState.collect {} }
        runCurrent()
        return model
    }

    @Test
    fun `hidden when nothing needs review`() =
        runTest(dispatcher) {
            val expenses = FakeExpenseRepository(listOf(testExpense("a", 100, day)))
            assertEquals(ReviewInboxUiState.Hidden, vm(expenses).uiState.value)
        }

    @Test
    fun `visible lists at most five items and reports the full total`() =
        runTest(dispatcher) {
            val expenses = FakeExpenseRepository((1..6).map { flagged("e$it") })
            val incomes = FakeIncomeRepository(listOf(flaggedIncome("i1")))
            val state = vm(expenses, incomes).uiState.value as ReviewInboxUiState.Visible
            assertEquals(REVIEW_INBOX_LIMIT, state.items.size)
            assertEquals(7, state.total)
        }

    @Test
    fun `a failing repository flow falls back to hidden`() =
        runTest(dispatcher) {
            val failing =
                object : ExpenseRepository by FakeExpenseRepository() {
                    override fun observeNeedingReview() = flow<List<Expense>> { error("boom") }
                }
            assertEquals(ReviewInboxUiState.Hidden, vm(failing).uiState.value)
        }

    @Test
    fun `confirm all marks every flagged record on both repositories and reports the count`() =
        runTest(dispatcher) {
            val expenses = FakeExpenseRepository((1..6).map { flagged("e$it") })
            val incomes = FakeIncomeRepository(listOf(flaggedIncome("i1")))
            val model = vm(expenses, incomes)

            model.onConfirmAll()
            advanceUntilIdle()

            assertTrue(expenses.getAllExpenses().getOrThrow().none { it.needsReview })
            assertTrue(incomes.getAllIncome().getOrThrow().none { it.needsReview })
            assertEquals(ReviewInboxEvent.Confirmed(7), model.eventFlow.first())
            assertEquals(ReviewInboxUiState.Hidden, model.uiState.value)
        }

    @Test
    fun `remove deletes through the use case and undo restores the item with its flag`() =
        runTest(dispatcher) {
            val expenses = FakeExpenseRepository(listOf(flagged("e1")))
            val model = vm(expenses)
            val item = (model.uiState.value as ReviewInboxUiState.Visible).items.single() as Expense

            model.onRemove(item)
            advanceUntilIdle()
            assertTrue(expenses.getAllExpenses().getOrThrow().isEmpty())
            val removed = model.eventFlow.first() as ReviewInboxEvent.Removed
            assertEquals(item, removed.deleted.transaction)

            model.onUndoRemove(removed.deleted)
            advanceUntilIdle()
            val restored = expenses.getAllExpenses().getOrThrow().single()
            assertTrue(restored.needsReview)
            assertEquals(item.name, restored.name)
        }

    @Test
    fun `removing an income deletes it from the income repository`() =
        runTest(dispatcher) {
            val incomes = FakeIncomeRepository(listOf(flaggedIncome("i1")))
            val model = vm(incomes = incomes)
            val item = (model.uiState.value as ReviewInboxUiState.Visible).items.single() as Income
            model.onRemove(item)
            advanceUntilIdle()
            assertTrue(incomes.getAllIncome().getOrThrow().isEmpty())
        }
}
