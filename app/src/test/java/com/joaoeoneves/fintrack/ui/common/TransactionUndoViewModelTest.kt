package com.joaoeoneves.fintrack.ui.common

import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionUndoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val expenses = FakeExpenseRepository()
    private val incomes = FakeIncomeRepository()
    private val date = Instant.parse("2026-10-10T10:00:00Z")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun restore_expense_readdsUnderSameId() =
        runTest(dispatcher) {
            val expense = testExpense("e1", 500L, date, isRefund = true)

            TransactionUndoViewModel(expenses, incomes).restore(expense)
            advanceUntilIdle()

            assertEquals(expense, expenses.getExpense("e1").getOrThrow())
        }

    @Test
    fun restore_income_readdsUnderSameId() =
        runTest(dispatcher) {
            val income = testIncome("i1", 90_000L, date)

            TransactionUndoViewModel(expenses, incomes).restore(income)
            advanceUntilIdle()

            assertEquals(income, incomes.getIncome("i1").getOrThrow())
        }
}
