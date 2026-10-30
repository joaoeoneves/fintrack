package com.joaoeoneves.fintrack.ui.expense.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** [AddEditExpenseViewModel] editing a stored split as a whole (5.9). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditExpenseViewModelSplitEditTest {
    private val testDispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun part(
        id: String,
        cents: Long,
        category: Category,
        splitId: String? = "s1",
    ) = Expense(
        id = id,
        name = "Groceries",
        amountCents = cents,
        category = category,
        date = Instant.parse("2026-10-01T10:00:00Z"),
        note = "weekly",
        splitId = splitId,
    )

    private val p1 = part("p1", 6_000L, BuiltInCategories.FOOD)
    private val p2 = part("p2", 4_000L, BuiltInCategories.SHOPPING)
    private val unrelated = part("x1", 900L, BuiltInCategories.FOOD, splitId = null)

    private fun viewModel(
        repo: FakeExpenseRepository,
        openId: String,
    ) = AddEditExpenseViewModel(
        repo,
        FakeCategoryRepository(),
        deleteTransactionFor(repo),
        SavedStateHandle(mapOf("expenseId" to openId)),
        clock = clock,
        idFactory = { "split42" },
    )

    private fun AddEditExpenseViewModel.form() = (uiState.value as AddEditUiState.Ready).form

    private suspend fun FakeExpenseRepository.all() = getAllExpenses().getOrThrow().associateBy { it.id }

    @Test
    fun openingAPartOfASplit_loadsEveryPart_withTheTotalAmount() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(p1, p2, unrelated)), "p1")
            advanceUntilIdle()

            val form = vm.form()
            assertTrue(form.isSplitting)
            assertEquals(10_000L, form.amountCents)
            assertEquals(
                listOf(
                    SplitPartForm(BuiltInCategories.FOOD, 6_000L, "p1"),
                    SplitPartForm(BuiltInCategories.SHOPPING, 4_000L, "p2"),
                ),
                form.splitParts.sortedBy { it.expenseId },
            )
        }

    @Test
    fun aSplitWithASinglePartInTheStore_opensAsAPlainExpense() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(p1, unrelated)), "p1")
            advanceUntilIdle()

            assertFalse(vm.form().isSplitting)
            assertEquals(6_000L, vm.form().amountCents)
        }

    @Test
    fun save_updatesChangedPartsAndSharedFields_addsNewPart_deletesRemovedOne() =
        runTest(testDispatcher) {
            val p3 = part("p3", 1_000L, BuiltInCategories.RECURRING)
            val repo = FakeExpenseRepository(listOf(p1, p2, p3))
            val vm = viewModel(repo, "p1")
            advanceUntilIdle()
            val form = vm.form()
            val i1 = form.splitParts.indexOfFirst { it.expenseId == "p1" }
            val i3 = form.splitParts.indexOfFirst { it.expenseId == "p3" }

            vm.onNameChanged("Supermarket")
            vm.onSplitPartAmountChanged(i1, 5_000L)
            vm.onSplitPartRemoved(i3)
            vm.onSplitPartAdded()
            vm.onSplitPartCategoryChanged(vm.form().splitParts.lastIndex, BuiltInCategories.INVESTMENTS)
            vm.onSplitPartAmountChanged(vm.form().splitParts.lastIndex, 2_000L)
            vm.onSave()
            advanceUntilIdle()

            val stored = repo.all()
            assertNull(stored["p3"])
            assertEquals(5_000L, stored.getValue("p1").amountCents)
            assertEquals(4_000L, stored.getValue("p2").amountCents)
            val added = stored.values.single { it.id !in setOf("p1", "p2") }
            assertEquals(BuiltInCategories.INVESTMENTS, added.category)
            assertEquals(2_000L, added.amountCents)
            assertTrue(stored.values.all { it.name == "Supermarket" && it.splitId == "s1" && it.note == "weekly" })
        }

    @Test
    fun save_withAnUnbalancedSum_isRejected_andNothingChanges() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(p1, p2))
            val vm = viewModel(repo, "p1")
            advanceUntilIdle()

            vm.onSplitPartAmountChanged(vm.form().splitParts.indexOfFirst { it.expenseId == "p1" }, 1L)
            vm.onSave()
            advanceUntilIdle()

            assertNotNull(vm.form().splitError)
            assertEquals(listOf(p1, p2), repo.getAllExpenses().getOrThrow())
        }

    @Test
    fun turningSplitOff_keepsTheOpenedPartAsPlain_andDeletesTheOthers() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(p1, p2, unrelated))
            val vm = viewModel(repo, "p1")
            advanceUntilIdle()

            vm.onSplitEnabledChanged(false)
            assertEquals(6_000L, vm.form().amountCents)
            assertEquals(BuiltInCategories.FOOD, vm.form().category)
            vm.onSave()
            advanceUntilIdle()

            val stored = repo.all()
            assertEquals(setOf("p1", "x1"), stored.keys)
            assertNull(stored.getValue("p1").splitId)
            assertEquals(6_000L, stored.getValue("p1").amountCents)
        }

    @Test
    fun reducingToOnePart_savesThatPartAsPlain() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(p1, p2))
            val vm = viewModel(repo, "p2")
            advanceUntilIdle()

            vm.onSplitPartRemoved(vm.form().splitParts.indexOfFirst { it.expenseId == "p1" })
            vm.onSplitEnabledChanged(false)
            vm.onSave()
            advanceUntilIdle()

            val stored = repo.all()
            assertEquals(setOf("p2"), stored.keys)
            assertNull(stored.getValue("p2").splitId)
            assertEquals(4_000L, stored.getValue("p2").amountCents)
        }

    @Test
    fun refundOnInAStoredSplit_isSplitOff_otherPartsDeleted_openedKeptAsRefund() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(p1, p2))
            val vm = viewModel(repo, "p1")
            advanceUntilIdle()

            vm.onRefundChanged(true)
            assertFalse(vm.form().isSplitting)
            vm.onSave()
            advanceUntilIdle()

            val stored = repo.all()
            assertEquals(setOf("p1"), stored.keys)
            assertTrue(stored.getValue("p1").isRefund)
            assertNull(stored.getValue("p1").splitId)
        }

    @Test
    fun deleteFromTheForm_deletesOnlyTheOpenedPart() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(p1, p2))
            val vm = viewModel(repo, "p1")
            advanceUntilIdle()

            vm.onDelete()
            advanceUntilIdle()

            assertEquals(setOf("p2"), repo.all().keys)
        }
}
