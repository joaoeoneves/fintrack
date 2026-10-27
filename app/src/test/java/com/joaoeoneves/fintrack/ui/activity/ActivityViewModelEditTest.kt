package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.testutil.genericErrorText
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Delete/undo, error/retry and categories of [ActivityViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelEditTest : ActivityViewModelTestBase() {
    private val groceries = expense("groceries", 200L)
    private val salary = income("salary", 3_000L)

    @Test
    fun onDelete_expense_removesItAndEmitsUndoEvent() =
        runTest(testDispatcher) {
            val fixture = Fixture(listOf(groceries, expense("keep")), listOf(salary))
            val events = mutableListOf<Transaction>()
            backgroundScope.launch { fixture.vm.undoEvent.collect { events += it } }
            content(fixture.vm)

            fixture.vm.onDelete("groceries")
            advanceUntilIdle()

            assertNull(fixture.expenseRepo.getExpense("groceries").getOrThrow())
            assertEquals(listOf("keep", "salary"), current(fixture.vm).items.map { it.id }.sorted())
            assertEquals(listOf<Transaction>(groceries), events)
        }

    @Test
    fun onDelete_income_removesItAndEmitsUndoEvent() =
        runTest(testDispatcher) {
            val fixture = Fixture(listOf(groceries), listOf(salary))
            val events = mutableListOf<Transaction>()
            backgroundScope.launch { fixture.vm.undoEvent.collect { events += it } }
            content(fixture.vm)

            fixture.vm.onDelete("salary")
            advanceUntilIdle()

            assertNull(fixture.incomeRepo.getIncome("salary").getOrThrow())
            assertEquals(listOf("groceries"), current(fixture.vm).items.map { it.id })
            assertEquals(listOf<Transaction>(salary), events)
        }

    @Test
    fun onDelete_unknownId_isNoOp_andEmitsNothing() =
        runTest(testDispatcher) {
            val fixture = Fixture(listOf(groceries), listOf(salary))
            val events = mutableListOf<Transaction>()
            backgroundScope.launch { fixture.vm.undoEvent.collect { events += it } }
            content(fixture.vm)

            fixture.vm.onDelete("nope")
            advanceUntilIdle()

            assertEquals(2, current(fixture.vm).items.size)
            assertTrue(events.isEmpty())
        }

    @Test
    fun onDelete_beforeContentLoaded_isNoOp() =
        runTest(testDispatcher) {
            val fixture = Fixture(listOf(groceries))

            fixture.vm.onDelete("groceries")
            advanceUntilIdle()

            assertEquals(groceries, fixture.expenseRepo.getExpense("groceries").getOrThrow())
        }

    @Test
    fun deleteThenUndo_expense_roundTrips() =
        runTest(testDispatcher) {
            val fixture = Fixture(listOf(groceries, expense("keep")))
            val events = mutableListOf<Transaction>()
            backgroundScope.launch { fixture.vm.undoEvent.collect { events += it } }
            content(fixture.vm)
            fixture.vm.onDelete("groceries")
            advanceUntilIdle()

            fixture.vm.onUndoDelete(events.single())
            advanceUntilIdle()

            assertEquals(setOf("groceries", "keep"), current(fixture.vm).items.map { it.id }.toSet())
            assertEquals(groceries, fixture.expenseRepo.getExpense("groceries").getOrThrow())
        }

    @Test
    fun deleteThenUndo_income_roundTrips() =
        runTest(testDispatcher) {
            val fixture = Fixture(incomes = listOf(salary))
            val events = mutableListOf<Transaction>()
            backgroundScope.launch { fixture.vm.undoEvent.collect { events += it } }
            content(fixture.vm)
            fixture.vm.onDelete("salary")
            advanceUntilIdle()
            assertTrue(current(fixture.vm).items.isEmpty())

            fixture.vm.onUndoDelete(events.single())
            advanceUntilIdle()

            assertEquals(listOf<Transaction>(salary), current(fixture.vm).items)
        }

    @Test
    fun repositoryError_surfacesErrorState() =
        runTest(testDispatcher) {
            val fixture = Fixture(listOf(groceries))
            fixture.expenseRepo.delegate.nextObserveExpensesError = IllegalStateException("Firestore unavailable")
            backgroundScope.launch { fixture.vm.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(ActivityUiState.Error(genericErrorText), fixture.vm.uiState.value)
        }

    @Test
    fun incomeRepositoryError_surfacesErrorState() =
        runTest(testDispatcher) {
            val fixture = Fixture(incomes = listOf(salary))
            fixture.incomeRepo.delegate.nextObserveIncomeError = RuntimeException()
            backgroundScope.launch { fixture.vm.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(ActivityUiState.Error(genericErrorText), fixture.vm.uiState.value)
        }

    @Test
    fun onRetry_afterErrorCleared_returnsToContent_keepingFilters() =
        runTest(testDispatcher) {
            val fixture = Fixture(listOf(groceries), listOf(salary))
            fixture.expenseRepo.delegate.nextObserveExpensesError = IllegalStateException("down")
            fixture.vm.onTypeSelected(ActivityType.INCOME)
            backgroundScope.launch { fixture.vm.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(ActivityUiState.Error(genericErrorText), fixture.vm.uiState.value)

            fixture.expenseRepo.delegate.nextObserveExpensesError = null
            fixture.vm.onRetry()
            advanceUntilIdle()

            assertEquals(listOf("salary"), current(fixture.vm).items.map { it.id })
            assertEquals(ActivityType.INCOME, current(fixture.vm).filters.type)
        }

    @Test
    fun onRetry_whileStillFailing_staysInError() =
        runTest(testDispatcher) {
            val fixture = Fixture(listOf(groceries))
            fixture.expenseRepo.delegate.nextObserveExpensesError = IllegalStateException("down")
            backgroundScope.launch { fixture.vm.uiState.collect {} }
            advanceUntilIdle()

            fixture.vm.onRetry()
            advanceUntilIdle()

            assertEquals(ActivityUiState.Error(genericErrorText), fixture.vm.uiState.value)
        }

    @Test
    fun categories_initialValue_isBuiltIns() =
        runTest(testDispatcher) {
            val gym = userCategory("gym", "Gym")
            val fixture = Fixture(categories = FakeCategoryRepository(listOf(gym)))

            assertEquals(BuiltInCategories.all, fixture.vm.categories.value)
        }

    @Test
    fun categories_afterCollection_includeUserCategories() =
        runTest(testDispatcher) {
            val gym = userCategory("gym", "Gym")
            val fixture = Fixture(categories = FakeCategoryRepository(listOf(gym)))
            backgroundScope.launch { fixture.vm.categories.collect {} }
            advanceUntilIdle()

            assertTrue(gym in fixture.vm.categories.value)
        }

    @Test
    fun categories_repositoryError_fallsBackToBuiltIns() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository(nextObserveCategoriesError = IllegalStateException("boom"))
            val fixture = Fixture(categories = repo)
            backgroundScope.launch { fixture.vm.categories.collect {} }
            advanceUntilIdle()

            assertEquals(BuiltInCategories.all, fixture.vm.categories.value)
        }
}
