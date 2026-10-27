package com.joaoeoneves.fintrack.ui.settings.categories

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.usecase.CategoryMovePreview
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.userCategory
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** The "move items of an archived category to another" flow of [CategoriesViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CategoriesViewModelMoveTest : CategoriesViewModelTestBase() {
    private val source = BuiltInCategories.RECURRING
    private val gym = userCategory("custom-1", "Gym", order = 10)
    private val date = Instant.parse("2025-01-01T00:00:00Z")

    private suspend fun seedSourceItems() {
        expenses.addExpense(testExpense("e1", 500L, date, source))
        expenses.addExpense(testExpense("e2", 700L, date, source))
        recurring.saveRecurring(recurringItem("r1", categoryId = source.id))
        budgets.setBudget(source.id, 9_000L)
    }

    private fun CategoriesViewModel.moveDialog() = content().moveDialog

    @Test
    fun noDialogInitially() =
        runTest(testDispatcher) {
            assertNull(start(FakeCategoryRepository(listOf(gym))).moveDialog())
        }

    @Test
    fun request_onActiveCategory_isIgnored() =
        runTest(testDispatcher) {
            val vm = start(FakeCategoryRepository(listOf(gym)))

            vm.onMoveItemsRequested(gym.id)
            advanceUntilIdle()

            assertNull(vm.moveDialog())
        }

    @Test
    fun request_withItems_checksThenOffersActiveTargetsExcludingSource() =
        runTest(testDispatcher) {
            seedSourceItems()
            val vm = start(FakeCategoryRepository(listOf(gym)))

            vm.onMoveItemsRequested(source.id)
            advanceUntilIdle()

            val dialog = vm.moveDialog() as MoveDialogState.PickTarget
            assertEquals(source, dialog.source)
            assertEquals(
                listOf(
                    BuiltInCategories.TRANSFER,
                    BuiltInCategories.INVESTMENTS,
                    BuiltInCategories.SHOPPING,
                    BuiltInCategories.FOOD,
                    gym,
                ),
                dialog.targets,
            )
        }

    @Test
    fun request_nothingToMove_showsNothingToMove() =
        runTest(testDispatcher) {
            val vm = start(FakeCategoryRepository(listOf(gym)))

            vm.onMoveItemsRequested(source.id)
            advanceUntilIdle()

            assertEquals(MoveDialogState.NothingToMove(source), vm.moveDialog())
        }

    @Test
    fun request_budgetOnly_stillOffersTargets() =
        runTest(testDispatcher) {
            budgets.setBudget(source.id, 100L)
            val vm = start(FakeCategoryRepository(listOf(gym)))

            vm.onMoveItemsRequested(source.id)
            advanceUntilIdle()

            assertTrue(vm.moveDialog() is MoveDialogState.PickTarget)
        }

    @Test
    fun request_previewFails_showsCannotVerify() =
        runTest(testDispatcher) {
            expenses.nextCountExpensesError = IllegalStateException("offline")
            val vm = start(FakeCategoryRepository(listOf(gym)))

            vm.onMoveItemsRequested(source.id)
            advanceUntilIdle()

            assertEquals(MoveDialogState.CannotVerify(source), vm.moveDialog())
        }

    @Test
    fun pickTarget_showsConfirmWithCountsAndTargetBudgetFlag() =
        runTest(testDispatcher) {
            seedSourceItems()
            budgets.setBudget(gym.id, 1_000L)
            val vm = start(FakeCategoryRepository(listOf(gym)))
            vm.onMoveItemsRequested(source.id)
            advanceUntilIdle()

            vm.onMoveTargetPicked(gym)
            advanceUntilIdle()

            assertEquals(
                MoveDialogState.Confirm(
                    source,
                    gym,
                    CategoryMovePreview(2, 1, hasBudget = true, targetHasBudget = true),
                ),
                vm.moveDialog(),
            )
        }

    @Test
    fun pickTarget_withoutPickTargetState_isIgnored() =
        runTest(testDispatcher) {
            val vm = start(FakeCategoryRepository(listOf(gym)))

            vm.onMoveTargetPicked(gym)
            advanceUntilIdle()

            assertNull(vm.moveDialog())
        }

    @Test
    fun pickTarget_previewFails_showsCannotVerify() =
        runTest(testDispatcher) {
            seedSourceItems()
            val vm = start(FakeCategoryRepository(listOf(gym)))
            vm.onMoveItemsRequested(source.id)
            advanceUntilIdle()
            expenses.nextCountExpensesError = IllegalStateException("offline")

            vm.onMoveTargetPicked(gym)
            advanceUntilIdle()

            assertEquals(MoveDialogState.CannotVerify(source), vm.moveDialog())
        }

    @Test
    fun confirm_movesEverything_closesDialog_emitsDoneMessage() =
        runTest(testDispatcher) {
            seedSourceItems()
            val vm = start(FakeCategoryRepository(listOf(gym)))
            vm.onMoveItemsRequested(source.id)
            advanceUntilIdle()
            vm.onMoveTargetPicked(BuiltInCategories.FOOD)
            advanceUntilIdle()

            vm.onConfirmMove()
            advanceUntilIdle()

            assertNull(vm.moveDialog())
            assertEquals(listOf<UiText>(UiText.StringResource(R.string.category_move_done)), errors)
            assertEquals(0, expenses.countExpensesWithCategory(source.id).getOrThrow())
            assertEquals(BuiltInCategories.FOOD.id, recurring.items.single().categoryId)
            assertEquals(listOf(Budget(BuiltInCategories.FOOD.id, 9_000L)), budgets.observeBudgets().first())
        }

    @Test
    fun confirm_failure_closesDialog_emitsFailureMessage_andRetryFinishes() =
        runTest(testDispatcher) {
            seedSourceItems()
            recurring.nextMoveCategoryError = IllegalStateException("boom")
            val vm = start(FakeCategoryRepository(listOf(gym)))
            vm.onMoveItemsRequested(source.id)
            advanceUntilIdle()
            vm.onMoveTargetPicked(BuiltInCategories.FOOD)
            advanceUntilIdle()

            vm.onConfirmMove()
            advanceUntilIdle()

            assertNull(vm.moveDialog())
            assertEquals(listOf<UiText>(UiText.StringResource(R.string.error_category_move_failed)), errors)
            assertEquals(source.id, recurring.items.single().categoryId)

            // Retrying the whole flow completes the remaining steps.
            vm.onMoveItemsRequested(source.id)
            advanceUntilIdle()
            assertTrue(vm.moveDialog() is MoveDialogState.PickTarget)
            vm.onMoveTargetPicked(BuiltInCategories.FOOD)
            advanceUntilIdle()
            vm.onConfirmMove()
            advanceUntilIdle()
            assertEquals(BuiltInCategories.FOOD.id, recurring.items.single().categoryId)
            assertEquals(2, errors.size)
        }

    @Test
    fun confirm_withoutConfirmState_isIgnored() =
        runTest(testDispatcher) {
            seedSourceItems()
            val vm = start(FakeCategoryRepository(listOf(gym)))

            vm.onConfirmMove()
            advanceUntilIdle()

            assertEquals(2, expenses.countExpensesWithCategory(source.id).getOrThrow())
            assertTrue(errors.isEmpty())
        }

    @Test
    fun dismiss_closesTheDialogAtAnyStepBeforeMoving() =
        runTest(testDispatcher) {
            seedSourceItems()
            val vm = start(FakeCategoryRepository(listOf(gym)))
            vm.onMoveItemsRequested(source.id)
            advanceUntilIdle()
            vm.onMoveTargetPicked(gym)
            advanceUntilIdle()
            assertTrue(vm.moveDialog() is MoveDialogState.Confirm)

            vm.onDismissMove()
            advanceUntilIdle()

            assertNull(vm.moveDialog())
            assertEquals(2, expenses.countExpensesWithCategory(source.id).getOrThrow())
        }
}
