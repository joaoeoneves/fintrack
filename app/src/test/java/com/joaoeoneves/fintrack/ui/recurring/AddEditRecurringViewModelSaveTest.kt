package com.joaoeoneves.fintrack.ui.recurring

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditRecurringViewModelSaveTest : AddEditRecurringViewModelTestBase() {
    @Test
    fun save_success_storesItem_andEmitsSavedEvent() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository()
            val vm = viewModel(repo)
            var saved = 0
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.savedEvent.collect { saved++ } }
            advanceUntilIdle()
            vm.fill()

            vm.onSave()
            advanceUntilIdle()
            assertEquals(1, saved)
            val item = repo.items.single()
            assertEquals("Gym", item.name)
            assertEquals(2_000L, item.amountCents)
            assertTrue(item.active)
        }

    @Test
    fun save_failure_showsSaveError_andAllowsRetry() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository(nextSaveError = IllegalStateException("boom"))
            val vm = viewModel(repo)
            var saved = 0
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.savedEvent.collect { saved++ } }
            advanceUntilIdle()
            vm.fill()

            vm.onSave()
            advanceUntilIdle()

            assertEquals(UiText.StringResource(R.string.error_recurring_save_failed), vm.ready().saveError)
            assertFalse(vm.ready().isSaving)
            assertEquals(0, saved)

            vm.onSave()
            advanceUntilIdle()
            assertEquals(1, saved)
            assertEquals(1, repo.items.size)
        }

    @Test
    fun doubleSave_writesOnce() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository()
            val vm = viewModel(repo)
            advanceUntilIdle()
            vm.fill()

            vm.onSave()
            assertTrue(vm.ready().isSaving)
            vm.onSave()
            advanceUntilIdle()

            assertEquals(1, repo.items.size)
        }

    @Test
    fun editSave_preservesCursorAndActiveFlag() =
        runTest(testDispatcher) {
            val item = recurringItem(id = "r5", handledThrough = day(2026, 2, 15), active = false)
            val repo = FakeRecurringRepository(listOf(item))
            val vm = viewModel(repo, id = "r5")
            var saved = 0
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.savedEvent.collect { saved++ } }
            advanceUntilIdle()

            vm.onSave()
            advanceUntilIdle()

            assertEquals(day(2026, 2, 15), repo.items.single().handledThrough)
            assertFalse(repo.items.single().active)
            assertEquals(1, saved)
        }

    @Test
    fun delete_removesItem_andEmitsSavedEvent_orReportsFailure() =
        runTest(testDispatcher) {
            val item = recurringItem(id = "r5")
            val repo = FakeRecurringRepository(listOf(item))
            val vm = viewModel(repo, id = "r5")
            var saved = 0
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.savedEvent.collect { saved++ } }
            advanceUntilIdle()

            repo.nextDeleteError = IllegalStateException("boom")
            vm.onDelete()
            advanceUntilIdle()
            assertNotNull(vm.ready().saveError)
            assertFalse(vm.ready().isSaving)
            assertEquals(1, repo.items.size)
            assertEquals(0, saved)

            vm.onDelete()
            advanceUntilIdle()
            assertTrue(repo.items.isEmpty())
            assertEquals(1, saved)
        }
}
