package com.joaoeoneves.fintrack.ui.recurring

import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.testutil.recurringItem
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

@OptIn(ExperimentalCoroutinesApi::class)
class RecurringUndoViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun restore_savesTheItemBackUnderItsOldId() =
        runTest(dispatcher) {
            val repo = FakeRecurringRepository()
            val item = recurringItem(id = "r9")

            RecurringUndoViewModel(repo).restore(item)
            advanceUntilIdle()

            assertEquals(listOf(item), repo.items)
        }

    @Test
    fun restore_twice_doesNotDuplicate() =
        runTest(dispatcher) {
            val repo = FakeRecurringRepository()
            val item = recurringItem(id = "r9")
            val vm = RecurringUndoViewModel(repo)

            vm.restore(item)
            vm.restore(item)
            advanceUntilIdle()

            assertEquals(1, repo.items.size)
        }
}
