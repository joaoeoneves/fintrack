package com.joaoeoneves.fintrack.ui.expense.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.testutil.deleteTransactionFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The expense form's fields come back from the [SavedStateHandle] after process death. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditExpenseViewModelRestoreTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun snapshot(expenseId: String?) =
        ExpenseFormSnapshot(
            expenseId = expenseId,
            name = "Dinner",
            amountCents = 4_250L,
            categoryId = BuiltInCategories.SHOPPING.id.value,
            dateEpochMillis = 1_760_000_000_000L,
            note = "with friends",
            isRefund = true,
            paidWithMealCard = false,
            mealCardTouched = true,
            categoryTouched = true,
        )

    private fun form(saved: String?): ExpenseFormState {
        val repo = FakeExpenseRepository()
        val handle = SavedStateHandle(if (saved == null) emptyMap() else mapOf("expense_form" to saved))
        val vm = AddEditExpenseViewModel(repo, FakeCategoryRepository(), deleteTransactionFor(repo), handle)
        testDispatcher.scheduler.advanceUntilIdle()
        return (vm.uiState.value as AddEditUiState.Ready).form
    }

    private fun json(snapshot: ExpenseFormSnapshot) = Json.encodeToString(ExpenseFormSnapshot.serializer(), snapshot)

    @Test
    fun aSnapshotOfTheSameNewExpense_isRestored() =
        runTest(testDispatcher) {
            val form = form(json(snapshot(null)))

            assertEquals("Dinner", form.name)
            assertEquals(4_250L, form.amountCents)
            assertEquals(BuiltInCategories.SHOPPING.id, form.category.id)
            assertEquals(1_760_000_000_000L, form.date.toEpochMilli())
            assertEquals("with friends", form.note)
            assertEquals(true, form.isRefund)
        }

    @Test
    fun aSnapshotOfAnotherExpense_isNotRestored() =
        runTest(testDispatcher) {
            val form = form(json(snapshot("someone-else")))

            assertEquals("", form.name)
            assertEquals(0L, form.amountCents)
        }

    @Test
    fun anUnreadableSnapshot_isIgnored() =
        runTest(testDispatcher) {
            val form = form("{not json")

            assertEquals("", form.name)
            assertEquals(0L, form.amountCents)
        }
}
