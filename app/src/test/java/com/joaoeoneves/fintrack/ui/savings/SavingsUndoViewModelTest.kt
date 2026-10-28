package com.joaoeoneves.fintrack.ui.savings

import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavingsUndoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val goalRepo = FakeGoalRepository()
    private val itemRepo = FakeRecurringRepository()
    private val log = WriteLog()
    private val viewModel = SavingsUndoViewModel(log.goals(goalRepo), log.recurring(itemRepo))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun restore_recreatesItemWithOriginalIdThenGoal() =
        runTest(dispatcher) {
            viewModel.restore(DeletedSavingsPlan(savingsGoal(recurringId = "r1"), savingsItem()))
            advanceUntilIdle()

            assertEquals(listOf("item:save:r1", "goal:save:g1"), log.entries)
            assertEquals("r1", itemRepo.items.single().id)
            assertEquals("r1", goalRepo.goals.single().recurringId)
        }

    @Test
    fun restore_planWithoutItem_savesOnlyTheGoal() =
        runTest(dispatcher) {
            viewModel.restore(DeletedSavingsPlan(savingsGoal(), null))
            advanceUntilIdle()

            assertEquals(listOf("goal:save:g1"), log.entries)
        }

    @Test
    fun restore_itemFails_goalIsNotRestored() =
        runTest(dispatcher) {
            itemRepo.nextSaveError = IllegalStateException("offline")

            viewModel.restore(DeletedSavingsPlan(savingsGoal(recurringId = "r1"), savingsItem()))
            advanceUntilIdle()

            assertTrue(goalRepo.goals.isEmpty())
        }
}
