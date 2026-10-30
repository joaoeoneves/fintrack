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

/** [AddEditExpenseViewModel] 5.7 features: refund link, split across categories, duplicate. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditExpenseViewModelLinkSplitTest {
    private val testDispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val original =
        Expense(
            id = "o1",
            name = "Headphones",
            amountCents = 10_000L,
            category = BuiltInCategories.SHOPPING,
            date = Instant.parse("2026-10-01T10:00:00Z"),
            note = "gift",
            paidWithMealCard = true,
            needsReview = true,
            splitId = "s1",
        )

    private fun viewModel(
        repo: FakeExpenseRepository,
        vararg route: Pair<String, Any?>,
    ) = AddEditExpenseViewModel(
        repo,
        FakeCategoryRepository(),
        deleteTransactionFor(repo),
        SavedStateHandle(mapOf(*route)),
        clock = clock,
        idFactory = { "split42" },
    )

    private fun AddEditExpenseViewModel.form() = (uiState.value as AddEditUiState.Ready).form

    // ---- refund link ----

    @Test
    fun recordRefundRoute_prefillsLinkedRefund_withEmptyAmount() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(original)), "refundOf" to "o1")
            advanceUntilIdle()

            val form = vm.form()
            assertTrue(form.isRefund)
            assertEquals("o1", form.refundOf)
            assertEquals("Headphones", form.refundOfLabel)
            assertEquals("Headphones", form.name)
            assertEquals(0L, form.amountCents)
        }

    @Test
    fun onRefundOfSelected_linksAndUnlinks_andSaveStoresLink() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(original))
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onRefundChanged(true)
            vm.onNameChanged("Return")
            vm.onAmountChanged(2_500L)

            vm.onRefundOfSelected(original)
            assertEquals("o1", vm.form().refundOf)
            vm.onRefundOfSelected(null)
            assertNull(vm.form().refundOf)
            assertNull(vm.form().refundOfLabel)
            vm.onRefundOfSelected(original)
            vm.onSave()
            advanceUntilIdle()

            val saved = repo.getAllExpenses().getOrThrow().single { it.name == "Return" }
            assertEquals("o1", saved.refundOf)
        }

    @Test
    fun refundOff_clearsLink_andNothingLinkedIsSaved() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(original))
            val vm = viewModel(repo, "refundOf" to "o1")
            advanceUntilIdle()

            vm.onRefundChanged(false)
            assertNull(vm.form().refundOf)
            vm.onAmountChanged(500L)
            vm.onSave()
            advanceUntilIdle()

            val saved = repo.getAllExpenses().getOrThrow().single { it.id != "o1" }
            assertFalse(saved.isRefund)
            assertNull(saved.refundOf)
        }

    @Test
    fun candidates_excludeRefunds_andTheEditedExpense() =
        runTest(testDispatcher) {
            val refund = original.copy(id = "r1", isRefund = true, refundOf = "o1")
            val other = original.copy(id = "o2", name = "Other", splitId = null)
            val vm = viewModel(FakeExpenseRepository(listOf(original, refund, other)), "expenseId" to "o2")
            advanceUntilIdle()

            assertEquals(listOf("o1"), vm.form().refundCandidates.map { it.id })
        }

    @Test
    fun editOriginal_showsRefundStatus_onlyWhenARefundIsLinked() =
        runTest(testDispatcher) {
            val unlinked = viewModel(FakeExpenseRepository(listOf(original)), "expenseId" to "o1")
            advanceUntilIdle()
            assertNull(unlinked.form().refundStatus)

            val refund = original.copy(id = "r1", amountCents = 2_500L, isRefund = true, refundOf = "o1")
            val linked = viewModel(FakeExpenseRepository(listOf(original, refund)), "expenseId" to "o1")
            advanceUntilIdle()
            val status = linked.form().refundStatus
            assertNotNull(status)
            assertEquals(2_500L, status?.refundedCents)
            assertEquals(25, status?.percent)
        }

    @Test
    fun editLinkedRefund_labelsTheOriginal_andNoStatusOnRefunds() =
        runTest(testDispatcher) {
            val refund = original.copy(id = "r1", amountCents = 2_500L, isRefund = true, refundOf = "o1")
            val vm = viewModel(FakeExpenseRepository(listOf(original, refund)), "expenseId" to "r1")
            advanceUntilIdle()

            assertEquals("Headphones", vm.form().refundOfLabel)
            assertNull(vm.form().refundStatus)
        }

    // ---- split ----

    @Test
    fun splitOn_startsTwoParts_firstHasCurrentCategoryAndAmount() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository())
            advanceUntilIdle()
            vm.onCategorySelected(BuiltInCategories.FOOD)
            vm.onAmountChanged(3_000L)

            vm.onSplitEnabledChanged(true)

            val form = vm.form()
            assertTrue(form.isSplitting)
            val expected = listOf(SplitPartForm(BuiltInCategories.FOOD, 3_000L), SplitPartForm(null, 0L))
            assertEquals(expected, form.splitParts)
            assertEquals(0L, form.splitRemainingCents)
        }

    @Test
    fun splitParts_addCapsAtTen_removeKeepsTwo() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository())
            advanceUntilIdle()
            vm.onSplitEnabledChanged(true)

            repeat(12) { vm.onSplitPartAdded() }
            assertEquals(10, vm.form().splitParts.size)
            repeat(12) { vm.onSplitPartRemoved(0) }
            assertEquals(2, vm.form().splitParts.size)
        }

    @Test
    fun splitSave_withMismatchedSum_isRejectedWithSplitError() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onNameChanged("Market")
            vm.onAmountChanged(3_000L)
            vm.onSplitEnabledChanged(true)
            vm.onSplitPartCategoryChanged(1, BuiltInCategories.SHOPPING)
            vm.onSplitPartAmountChanged(1, 500L)

            vm.onSave()
            advanceUntilIdle()

            assertNotNull(vm.form().splitError)
            assertTrue(repo.getAllExpenses().getOrThrow().isEmpty())
        }

    @Test
    fun splitSave_withUnpickedCategory_isRejected() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onNameChanged("Market")
            vm.onAmountChanged(3_000L)
            vm.onSplitEnabledChanged(true)
            vm.onSplitPartAmountChanged(0, 1_000L)
            vm.onSplitPartAmountChanged(1, 2_000L)

            vm.onSave()
            advanceUntilIdle()

            assertNotNull(vm.form().splitError)
            assertTrue(repo.getAllExpenses().getOrThrow().isEmpty())
        }

    @Test
    fun splitSave_validParts_createSiblingsSharingTheSplitId() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.onNameChanged("Market")
            vm.onAmountChanged(3_000L)
            vm.onCategorySelected(BuiltInCategories.FOOD)
            vm.onSplitEnabledChanged(true)
            vm.onSplitPartAmountChanged(0, 1_800L)
            vm.onSplitPartCategoryChanged(1, BuiltInCategories.SHOPPING)
            vm.onSplitPartAmountChanged(1, 1_200L)

            vm.onSave()
            advanceUntilIdle()

            val saved = repo.getAllExpenses().getOrThrow()
            assertEquals(2, saved.size)
            assertEquals(3_000L, saved.sumOf { it.amountCents })
            assertEquals(setOf("split42"), saved.map { it.splitId }.toSet())
            assertEquals(setOf("Market"), saved.map { it.name }.toSet())
            assertEquals(
                setOf(BuiltInCategories.FOOD to 1_800L, BuiltInCategories.SHOPPING to 1_200L),
                saved.map { it.category to it.amountCents }.toSet(),
            )
        }

    @Test
    fun refundOn_clearsSplit() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository())
            advanceUntilIdle()
            vm.onSplitEnabledChanged(true)

            vm.onRefundChanged(true)

            assertFalse(vm.form().splitEnabled)
            assertTrue(vm.form().splitParts.isEmpty())
        }

    @Test
    fun editMode_neverSplits() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(original)), "expenseId" to "o1")
            advanceUntilIdle()
            vm.onSplitEnabledChanged(true)

            assertFalse(vm.form().isSplitting)
            assertEquals("s1", vm.form().splitId)
        }

    // ---- duplicate ----

    @Test
    fun duplicateRoute_copiesFields_datesToday_dropsLinksAndReview() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(original)), "duplicateOf" to "o1")
            advanceUntilIdle()

            val form = vm.form()
            assertFalse(form.isEditMode)
            assertNull(form.expenseId)
            assertEquals("Headphones", form.name)
            assertEquals(10_000L, form.amountCents)
            assertEquals("gift", form.note)
            assertTrue(form.paidWithMealCard)
            assertTrue(form.categoryTouched)
            assertTrue(form.mealCardTouched)
            assertNull(form.splitId)
            assertNull(form.refundOf)
            assertEquals(now, form.date)
        }

    @Test
    fun duplicateRoute_savesANewExpense_leavingTheSourceUntouched() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(original))
            val vm = viewModel(repo, "duplicateOf" to "o1")
            advanceUntilIdle()

            vm.onSave()
            advanceUntilIdle()

            val all = repo.getAllExpenses().getOrThrow()
            assertEquals(2, all.size)
            val copy = all.single { it.id != "o1" }
            assertNull(copy.splitId)
            assertFalse(copy.needsReview)
            assertEquals(original, all.single { it.id == "o1" })
        }

    @Test
    fun duplicateRoute_missingSource_givesEmptyForm() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(), "duplicateOf" to "ghost")
            advanceUntilIdle()

            assertEquals("", vm.form().name)
            assertEquals(0L, vm.form().amountCents)
        }
}
