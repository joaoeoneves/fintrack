package com.joaoeoneves.fintrack.ui.income

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Deleting income lines and setting/clearing category allocations through [IncomeTabViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class IncomeTabViewModelWriteTest : IncomeTabViewModelTestBase() {
    private val failed = UiText.StringResource(R.string.error_plan_save_failed)

    @Test
    fun deleteIncomeLine_removesLine_andClosesEditor() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a"))
            val vm = viewModel()
            collect(vm)
            vm.onEditIncomeLine("a")
            advanceUntilIdle()

            vm.onDeleteIncomeLine("a")
            advanceUntilIdle()

            assertEquals(emptyList<Any>(), incomeRepo.lines)
            assertNull(vm.content().editor)
        }

    @Test
    fun deleteIncomeLine_failure_keepsEditorAndEmitsError() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a"))
            val vm = viewModel()
            collect(vm)
            vm.onEditIncomeLine("a")
            advanceUntilIdle()
            incomeRepo.nextDeleteError = IllegalStateException("nope")

            vm.onDeleteIncomeLine("a")
            advanceUntilIdle()

            assertEquals(1, incomeRepo.lines.size)
            assertNotNull(vm.content().editor)
            assertEquals(failed, vm.errorEvent.first())
        }

    @Test
    fun deleteIncomeLine_emitsTheLine_andUndoRestoresIt() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a"))
            val vm = viewModel()
            collect(vm)

            vm.onDeleteIncomeLine("a")
            advanceUntilIdle()
            val deleted = vm.deletedLineEvent.first()

            assertEquals("a", deleted.id)
            assertEquals(emptyList<Any>(), incomeRepo.lines)

            vm.onUndoDeleteIncomeLine(deleted)
            advanceUntilIdle()

            assertEquals(listOf("a"), incomeRepo.lines.map { it.id })
        }

    @Test
    fun deleteIncomeLine_unknownId_emitsNothing() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a"))
            val vm = viewModel()
            collect(vm)

            vm.onDeleteIncomeLine("nope")
            advanceUntilIdle()

            assertEquals(1, incomeRepo.lines.size)
            assertNull(withTimeoutOrNull(1) { vm.deletedLineEvent.first() })
        }

    @Test
    fun undoDelete_failure_emitsError() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            incomeRepo.nextSaveError = IllegalStateException("nope")

            vm.onUndoDeleteIncomeLine(line("a"))
            advanceUntilIdle()

            assertEquals(failed, vm.errorEvent.first())
        }
}
