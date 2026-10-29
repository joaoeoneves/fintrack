package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.data.preferences.StartChecklistState
import com.joaoeoneves.fintrack.data.preferences.StartChecklistStore
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.StartStep
import com.joaoeoneves.fintrack.domain.usecase.ObserveStartChecklist
import com.joaoeoneves.fintrack.testutil.InMemoryPreferencesDataStore
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StartChecklistViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settleMillis = 2_000L
    private val store = StartChecklistStore(InMemoryPreferencesDataStore())
    private val sessions = FakeSessionRepository(initialSession = Session.Guest)
    private val profiles = FakeSalaryProfileRepository()
    private val plan = FakePlannedIncomeRepository()
    private val recurring = FakeRecurringRepository()
    private val expenses = FakeExpenseRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(session: FakeSessionRepository = sessions) =
        StartChecklistViewModel(
            store,
            ObserveStartChecklist(profiles, plan, recurring, expenses),
            session,
            settleMillis,
        )

    private fun TestScope.started(vm: StartChecklistViewModel): StartChecklistViewModel {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        return vm
    }

    private suspend fun setUpSteps(count: Int) {
        val steps: List<suspend () -> Unit> =
            listOf(
                { profiles.saveSalaryProfile(SalaryProfile(baseGrossCents = 100_000L)) },
                { recurring.saveRecurring(recurringItem(id = "")) },
                { expenses.addExpense(expense()) },
                { plan.savePlannedIncome(PlannedIncome("l1", "Pay", 100_000L, 0, autoRecord = true)) },
            )
        steps.take(count).forEach { it() }
    }

    private fun expense(): Expense {
        val date = Instant.parse("2026-10-05T10:00:00Z")
        return Expense("e1", "Coffee", 300L, BuiltInCategories.FOOD, date, null)
    }

    private fun StartChecklistViewModel.visible() = uiState.value as StartChecklistUiState.Visible

    @Test
    fun beforeAnySession_staysUndecided_untilGuestOrSignedIn_thenSettleDelay() =
        runTest(dispatcher) {
            val loading = FakeSessionRepository(initialSession = Session.Loading)
            val vm = started(viewModel(loading))

            advanceTimeBy(10 * settleMillis)
            assertNull(store.state.first())
            assertEquals(StartChecklistUiState.Hidden, vm.uiState.value)

            loading.emit(Session.SignedOut)
            advanceTimeBy(10 * settleMillis)
            assertNull(store.state.first())

            loading.emit(Session.Guest)
            runCurrent()
            advanceTimeBy(settleMillis - 1)
            assertNull(store.state.first())

            advanceTimeBy(1)
            runCurrent()
            assertEquals(StartChecklistState.ACTIVE, store.state.first())
        }

    @Test
    fun threeStepsDone_retiresTheCard_andHidesIt() =
        runTest(dispatcher) {
            setUpSteps(3)
            val vm = started(viewModel())

            advanceUntilIdle()

            assertEquals(StartChecklistState.RETIRED, store.state.first())
            assertEquals(StartChecklistUiState.Hidden, vm.uiState.value)
        }

    @Test
    fun twoStepsDone_isActive_andVisibleWithProgress() =
        runTest(dispatcher) {
            setUpSteps(2)
            val vm = started(viewModel())

            advanceUntilIdle()

            assertEquals(StartChecklistState.ACTIVE, store.state.first())
            val visible = vm.visible()
            assertEquals(2, visible.doneCount)
            assertEquals(4, visible.total)
            assertEquals(StartStep.entries, visible.steps.map { it.step })
            assertEquals(
                setOf(StartStep.SALARY, StartStep.BILLS),
                visible.steps
                    .filter { it.done }
                    .map { it.step }
                    .toSet(),
            )
        }

    @Test
    fun emptyAccount_isActive_withNothingDone() =
        runTest(dispatcher) {
            val vm = started(viewModel())

            advanceUntilIdle()

            assertEquals(0, vm.visible().doneCount)
            assertTrue(vm.visible().steps.none { it.done })
        }

    @Test
    fun activeChecklist_staysVisibleAsMoreStepsGetDone() =
        runTest(dispatcher) {
            setUpSteps(1)
            val vm = started(viewModel())
            advanceUntilIdle()

            recurring.saveRecurring(recurringItem(id = ""))
            advanceUntilIdle()
            assertEquals(2, vm.visible().doneCount)
            assertEquals(StartChecklistState.ACTIVE, store.state.first())

            expenses.addExpense(expense())
            advanceUntilIdle()
            assertEquals(3, vm.visible().doneCount)
            assertEquals(StartChecklistState.ACTIVE, store.state.first())
        }

    @Test
    fun completeChecklist_isHidden_butStaysActiveInTheStore() =
        runTest(dispatcher) {
            setUpSteps(3)
            store.set(StartChecklistState.ACTIVE)
            val vm = started(viewModel())
            advanceUntilIdle()
            assertEquals(3, vm.visible().doneCount)

            plan.savePlannedIncome(PlannedIncome("l1", "Pay", 100_000L, 0, autoRecord = true))
            advanceUntilIdle()

            assertEquals(StartChecklistUiState.Hidden, vm.uiState.value)
            assertEquals(StartChecklistState.ACTIVE, store.state.first())
        }

    @Test
    fun onDismiss_storesDismissed_andHides() =
        runTest(dispatcher) {
            val vm = started(viewModel())
            advanceUntilIdle()
            assertTrue(vm.uiState.value is StartChecklistUiState.Visible)

            vm.onDismiss()
            advanceUntilIdle()

            assertEquals(StartChecklistState.DISMISSED, store.state.first())
            assertEquals(StartChecklistUiState.Hidden, vm.uiState.value)
        }

    @Test
    fun alreadyDecided_isNotDecidedAgain() =
        runTest(dispatcher) {
            store.set(StartChecklistState.DISMISSED)
            setUpSteps(0)
            val vm = started(viewModel())

            advanceUntilIdle()

            assertEquals(StartChecklistState.DISMISSED, store.state.first())
            assertEquals(StartChecklistUiState.Hidden, vm.uiState.value)
        }

    @Test
    fun aFailingDecision_isSwallowed_andALaterClearDecidesAgain() =
        runTest(dispatcher) {
            profiles.nextObserveError = IllegalStateException("offline")
            val vm = started(viewModel())
            advanceUntilIdle()

            // The failure was logged, not thrown: nothing decided and the card stays hidden.
            assertNull(store.state.first())
            assertEquals(StartChecklistUiState.Hidden, vm.uiState.value)

            // The collector is still alive: a later undecided state (after a store change) decides again.
            profiles.nextObserveError = null
            store.set(StartChecklistState.DISMISSED)
            advanceUntilIdle()
            store.clear()
            advanceUntilIdle()

            assertEquals(StartChecklistState.ACTIVE, store.state.first())
        }

    @Test
    fun afterClear_decidesAgain() =
        runTest(dispatcher) {
            val vm = started(viewModel())
            advanceUntilIdle()
            vm.onDismiss()
            advanceUntilIdle()
            assertEquals(StartChecklistUiState.Hidden, vm.uiState.value)

            store.clear()
            advanceUntilIdle()

            assertEquals(StartChecklistState.ACTIVE, store.state.first())
            assertTrue(vm.uiState.value is StartChecklistUiState.Visible)
        }

    @Test
    fun afterClear_anAlreadySetUpAccountIsRetiredAgain() =
        runTest(dispatcher) {
            setUpSteps(3)
            val vm = started(viewModel())
            advanceUntilIdle()
            assertEquals(StartChecklistState.RETIRED, store.state.first())

            store.clear()
            advanceTimeBy(settleMillis - 1)
            assertNull(store.state.first())
            advanceUntilIdle()

            assertEquals(StartChecklistState.RETIRED, store.state.first())
            assertEquals(StartChecklistUiState.Hidden, vm.uiState.value)
        }
}
