package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.usecase.BulkKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Multi-select, bulk actions and refunded badges of [ActivityViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelSelectionTest : ActivityViewModelTestBase() {
    private val a = expense("a", 1_000L, category = BuiltInCategories.SHOPPING)
    private val b = expense("b", 2_000L, category = BuiltInCategories.SHOPPING)
    private val c = expense("c", 3_000L)
    private val salary = income("salary", 5_000L)

    private fun TestScope.started(fixture: Fixture): Fixture {
        content(fixture.vm)
        backgroundScope.launch { fixture.vm.selectionState.collect {} }
        advanceUntilIdle()
        return fixture
    }

    @Test
    fun longPress_selectsAnExpense_andShowsTheCount() =
        runTest(testDispatcher) {
            val vm = started(Fixture(listOf(a, b, c), listOf(salary))).vm

            vm.onLongPress("a")
            advanceUntilIdle()

            assertEquals(setOf("a"), vm.selectedIds.value)
            assertEquals(1, vm.selectionState.value?.count)
        }

    @Test
    fun longPress_onIncomeOrUnknown_doesNothing() =
        runTest(testDispatcher) {
            val vm = started(Fixture(listOf(a), listOf(salary))).vm

            vm.onLongPress("salary")
            vm.onLongPress("ghost")
            advanceUntilIdle()

            assertTrue(vm.selectedIds.value.isEmpty())
            assertNull(vm.selectionState.value)
        }

    @Test
    fun toggle_addsAndRemoves_andIsIgnoredWhenNotSelecting() =
        runTest(testDispatcher) {
            val vm = started(Fixture(listOf(a, b, c), listOf(salary))).vm

            vm.onToggleSelected("a")
            assertTrue(vm.selectedIds.value.isEmpty())

            vm.onLongPress("a")

            advanceUntilIdle()
            vm.onToggleSelected("b")
            vm.onToggleSelected("salary")
            advanceUntilIdle()
            assertEquals(setOf("a", "b"), vm.selectedIds.value)

            vm.onToggleSelected("a")
            advanceUntilIdle()
            assertEquals(setOf("b"), vm.selectedIds.value)
        }

    @Test
    fun clearSelection_emptiesIt() =
        runTest(testDispatcher) {
            val vm = started(Fixture(listOf(a, b), emptyList())).vm
            vm.onLongPress("a")
            advanceUntilIdle()

            vm.onClearSelection()
            advanceUntilIdle()

            assertTrue(vm.selectedIds.value.isEmpty())
            assertNull(vm.selectionState.value)
        }

    @Test
    fun periodChange_clearsSelection() =
        runTest(testDispatcher) {
            val vm = started(Fixture(listOf(a, b), emptyList())).vm
            vm.onLongPress("a")
            advanceUntilIdle()

            vm.periodActions.onPrevious()
            advanceUntilIdle()

            assertTrue(vm.selectedIds.value.isEmpty())
        }

    @Test
    fun selection_survivesRecreation() =
        runTest(testDispatcher) {
            val fixture = started(Fixture(listOf(a, b), emptyList()))
            fixture.vm.onLongPress("b")
            advanceUntilIdle()

            val restored = fixture.recreate()
            advanceUntilIdle()

            assertEquals(setOf("b"), restored.selectedIds.value)
        }

    @Test
    fun bulkCategory_changesOnlySelected_clearsSelection_andEmitsUndoableEvent() =
        runTest(testDispatcher) {
            val fixture = started(Fixture(listOf(a, b, c), emptyList()))
            val events = mutableListOf<BulkEvent>()
            backgroundScope.launch { fixture.vm.bulkUndoEvent.collect { events += it } }
            fixture.vm.onLongPress("a")
            advanceUntilIdle()
            fixture.vm.onToggleSelected("b")
            advanceUntilIdle()

            fixture.vm.onBulkCategory(BuiltInCategories.FOOD)
            advanceUntilIdle()

            val stored =
                fixture.expenseRepo
                    .getAllExpenses()
                    .getOrThrow()
                    .associateBy { it.id }
            assertEquals(BuiltInCategories.FOOD, stored.getValue("a").category)
            assertEquals(BuiltInCategories.FOOD, stored.getValue("b").category)
            assertEquals(c.category, stored.getValue("c").category)
            assertTrue(
                fixture.vm.selectedIds.value
                    .isEmpty(),
            )
            assertEquals(listOf(BulkKind.CATEGORY to 2), events.map { it.kind to it.count })

            fixture.vm.onBulkUndo(events.single().undo)
            advanceUntilIdle()
            val restored =
                fixture.expenseRepo
                    .getAllExpenses()
                    .getOrThrow()
                    .associateBy { it.id }
            assertEquals(BuiltInCategories.SHOPPING, restored.getValue("a").category)
        }

    @Test
    fun bulkMealCard_turnsOn_thenOffWhenAllAlreadyOn() =
        runTest(testDispatcher) {
            val fixture = started(Fixture(listOf(a, b), emptyList()))
            fixture.vm.onLongPress("a")
            advanceUntilIdle()
            fixture.vm.onToggleSelected("b")
            advanceUntilIdle()

            fixture.vm.onBulkMealCard()
            advanceUntilIdle()
            assertTrue(
                fixture.expenseRepo
                    .getAllExpenses()
                    .getOrThrow()
                    .all { it.paidWithMealCard },
            )

            fixture.vm.onLongPress("a")

            advanceUntilIdle()
            fixture.vm.onToggleSelected("b")
            advanceUntilIdle()
            assertTrue(
                fixture.vm.selectionState.value
                    ?.allOnMealCard == true,
            )
            fixture.vm.onBulkMealCard()
            advanceUntilIdle()
            assertTrue(
                fixture.expenseRepo
                    .getAllExpenses()
                    .getOrThrow()
                    .none { it.paidWithMealCard },
            )
        }

    @Test
    fun bulkDelete_removesSelection_andUndoRestores() =
        runTest(testDispatcher) {
            val fixture = started(Fixture(listOf(a, b, c), emptyList()))
            val events = mutableListOf<BulkEvent>()
            backgroundScope.launch { fixture.vm.bulkUndoEvent.collect { events += it } }
            fixture.vm.onLongPress("a")
            advanceUntilIdle()
            fixture.vm.onToggleSelected("c")
            advanceUntilIdle()

            fixture.vm.onBulkDelete()
            advanceUntilIdle()
            assertEquals(
                listOf("b"),
                fixture.expenseRepo
                    .getAllExpenses()
                    .getOrThrow()
                    .map { it.id },
            )
            assertEquals(BulkKind.DELETE, events.single().kind)

            fixture.vm.onBulkUndo(events.single().undo)
            advanceUntilIdle()
            assertEquals(
                setOf("a", "b", "c"),
                fixture.expenseRepo
                    .getAllExpenses()
                    .getOrThrow()
                    .map { it.id }
                    .toSet(),
            )
        }

    @Test
    fun bulkAction_withNothingSelected_isNoOp() =
        runTest(testDispatcher) {
            val fixture = started(Fixture(listOf(a), emptyList()))
            val events = mutableListOf<BulkEvent>()
            backgroundScope.launch { fixture.vm.bulkUndoEvent.collect { events += it } }

            fixture.vm.onBulkDelete()
            advanceUntilIdle()

            assertTrue(events.isEmpty())
            assertEquals(
                1,
                fixture.expenseRepo
                    .getAllExpenses()
                    .getOrThrow()
                    .size,
            )
        }

    @Test
    fun refundedPercent_reflectsLinkedRefunds_cappedAt100_andOmitsUnlinked() =
        runTest(testDispatcher) {
            val half = expense("half", 10_000L)
            val over = expense("over", 1_000L)
            val none = expense("none", 1_000L)
            val r1 = expense("r1", 5_000L, isRefund = true).copy(refundOf = "half")
            val r2 = expense("r2", 3_000L, isRefund = true).copy(refundOf = "over")
            val loose = expense("loose", 900L, isRefund = true)
            val vm = Fixture(listOf(half, over, none, r1, r2, loose)).vm

            val percent = content(vm).refundedPercent

            assertEquals(mapOf("half" to 50, "over" to 100), percent)
        }
}
