package com.joaoeoneves.fintrack.ui.income

import com.joaoeoneves.fintrack.ui.plan.IncomeSchedule
import com.joaoeoneves.fintrack.ui.plan.IncomeLineEditorState
import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.MAX_PLANNED_INCOME_LINES
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** The income-line editor flow (open, save, validate, delete) of [IncomeTabViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class IncomeTabViewModelEditorTest : IncomeTabViewModelTestBase() {
    private val month = YearMonth.of(2026, 10)

    @Test
    fun addIncomeLine_opensEmptyEditor() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            vm.onAddIncomeLine()
            advanceUntilIdle()

            assertEquals(IncomeLineEditorState(null, "", null), vm.content().editor)
        }

    @Test
    fun addIncomeLine_atTwentyLines_isNoOp() =
        runTest(testDispatcher) {
            repeat(MAX_PLANNED_INCOME_LINES) { incomeRepo.savePlannedIncome(line("l$it", order = it)) }
            val vm = viewModel()
            collect(vm)

            vm.onAddIncomeLine()
            advanceUntilIdle()

            assertNull(vm.content().editor)
        }

    @Test
    fun editIncomeLine_opensEditorPrefilled_andUnknownIdIsNoOp() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a", amountCents = 55_000L))
            val vm = viewModel()
            collect(vm)

            vm.onEditIncomeLine("missing")
            advanceUntilIdle()
            assertNull(vm.content().editor)

            vm.onEditIncomeLine("a")
            advanceUntilIdle()
            assertEquals(IncomeLineEditorState("a", "a", 55_000L), vm.content().editor)
        }

    @Test
    fun dismissEditor_closesIt() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            vm.onAddIncomeLine()
            advanceUntilIdle()

            vm.onDismissEditor()
            advanceUntilIdle()

            assertNull(vm.content().editor)
        }

    @Test
    fun saveWithoutEditor_isNoOp() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            vm.onSaveIncomeLine("Salary", 100L, IncomeSchedule(), month)
            advanceUntilIdle()

            assertEquals(emptyList<Any>(), incomeRepo.lines)
        }

    @Test
    fun saveNewLine_trimsLabel_getsOrderZeroThenMaxPlusOne_andClosesEditor() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            vm.onAddIncomeLine()
            advanceUntilIdle()
            vm.onSaveIncomeLine("  Salary  ", 150_000L, IncomeSchedule(), month)
            advanceUntilIdle()
            assertEquals(0, incomeRepo.lines.single().order)
            assertEquals("Salary", incomeRepo.lines.single().label)
            assertNull(vm.content().editor)

            vm.onAddIncomeLine()
            advanceUntilIdle()
            vm.onSaveIncomeLine("Meal", 20_000L, IncomeSchedule(), month)
            advanceUntilIdle()
            assertEquals(listOf(0, 1), incomeRepo.lines.map { it.order })
            assertEquals(listOf("Salary", "Meal"), incomeRepo.lines.map { it.label })
        }

    @Test
    fun saveExistingLine_keepsIdAndOrder_andUpdatesValues() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a", order = 3))
            val vm = viewModel()
            collect(vm)
            vm.onEditIncomeLine("a")
            advanceUntilIdle()

            vm.onSaveIncomeLine("Renamed", 42L, IncomeSchedule(), month)
            advanceUntilIdle()

            val saved = incomeRepo.lines.single()
            assertEquals("a", saved.id)
            assertEquals(3, saved.order)
            assertEquals("Renamed", saved.label)
            assertEquals(42L, saved.amountCents)
        }

    @Test
    fun saveBlankLabel_setsLabelError_keepsEditorAndSavesNothing() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            vm.onAddIncomeLine()
            advanceUntilIdle()

            vm.onSaveIncomeLine("   ", 100L, IncomeSchedule(), month)
            advanceUntilIdle()

            val editor = vm.content().editor
            assertEquals(UiText.StringResource(R.string.error_plan_label_required), editor?.labelError)
            assertEquals(emptyList<Any>(), incomeRepo.lines)
        }

    @Test
    fun saveTooLongLabel_setsTooLongError() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            vm.onAddIncomeLine()
            advanceUntilIdle()

            vm.onSaveIncomeLine("a".repeat(51), 100L, IncomeSchedule(), month)
            advanceUntilIdle()

            assertEquals(UiText.StringResource(R.string.error_plan_label_too_long), vm.content().editor?.labelError)
            assertEquals(emptyList<Any>(), incomeRepo.lines)
        }

    @Test
    fun saveFailure_keepsEditorOpen_andEmitsErrorEvent() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            vm.onAddIncomeLine()
            advanceUntilIdle()
            incomeRepo.nextSaveError = IllegalStateException("nope")

            vm.onSaveIncomeLine("Salary", 100L, IncomeSchedule(), month)
            advanceUntilIdle()

            assertNotNull(vm.content().editor)
            assertEquals(UiText.StringResource(R.string.error_plan_save_failed), vm.errorEvent.first())
            assertEquals(emptyList<Any>(), incomeRepo.lines)
        }
}
