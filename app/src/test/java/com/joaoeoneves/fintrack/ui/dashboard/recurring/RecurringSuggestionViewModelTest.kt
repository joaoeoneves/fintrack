package com.joaoeoneves.fintrack.ui.dashboard.recurring

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSuggestionRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.RecurringCandidate
import com.joaoeoneves.fintrack.testutil.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RecurringSuggestionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = MutableClock(Instant.parse("2026-10-29T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun netflix(
        id: String,
        month: Int,
    ) = Expense(
        id,
        "Netflix",
        1_000L,
        BuiltInCategories.FOOD,
        Instant.parse("2026-%02d-05T10:00:00Z".format(month)),
        null,
    )

    private val streak = listOf(netflix("a", 8), netflix("b", 9), netflix("c", 10))

    private fun TestScope.started(
        expenses: List<Expense> = streak,
        suggestions: FakeSuggestionRepository = FakeSuggestionRepository(),
    ): RecurringSuggestionViewModel {
        val expenseRepo = FakeExpenseRepository(expenses)
        val vm = RecurringSuggestionViewModel(expenseRepo, FakeRecurringRepository(), suggestions, clock)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        return vm
    }

    @Test
    fun `three monthly records make the card visible with the candidate`() =
        runTest(dispatcher) {
            val state = started().uiState.value as RecurringSuggestionUiState.Visible
            assertEquals("netflix", state.candidate.key)
            assertEquals(YearMonth.of(2026, 10), state.candidate.lastMonth)
            assertEquals(5, state.candidate.dayOfMonth)
        }

    @Test
    fun `no streak keeps the card hidden`() =
        runTest(dispatcher) {
            assertEquals(RecurringSuggestionUiState.Hidden, started(streak.take(2)).uiState.value)
        }

    @Test
    fun `dismissing hides the card, announces the dismissal and stores the key`() =
        runTest(dispatcher) {
            val suggestions = FakeSuggestionRepository()
            val vm = started(suggestions = suggestions)
            val candidate = (vm.uiState.value as RecurringSuggestionUiState.Visible).candidate

            vm.onDismiss(candidate)
            advanceUntilIdle()

            assertEquals(setOf("netflix"), suggestions.keys)
            assertEquals(candidate, vm.dismissedEvent.first())
            assertEquals(RecurringSuggestionUiState.Hidden, vm.uiState.value)
        }

    @Test
    fun `a failed dismissal announces nothing and keeps the card`() =
        runTest(dispatcher) {
            val suggestions = FakeSuggestionRepository(nextError = IllegalStateException("offline"))
            val vm = started(suggestions = suggestions)
            val candidate = (vm.uiState.value as RecurringSuggestionUiState.Visible).candidate

            vm.onDismiss(candidate)
            advanceUntilIdle()

            assertTrue(suggestions.keys.isEmpty())
            assertTrue(vm.uiState.value is RecurringSuggestionUiState.Visible)
        }

    @Test
    fun `undo calls undismiss and the card comes back`() =
        runTest(dispatcher) {
            val suggestions = FakeSuggestionRepository(initialDismissed = setOf("netflix"))
            val vm = started(suggestions = suggestions)
            assertEquals(RecurringSuggestionUiState.Hidden, vm.uiState.value)
            val candidate =
                RecurringCandidate("netflix", "Netflix", 1_000L, BuiltInCategories.FOOD.id, 5, YearMonth.of(2026, 10))

            vm.onUndoDismiss(candidate)
            advanceUntilIdle()

            assertTrue(suggestions.keys.isEmpty())
            assertTrue(vm.uiState.value is RecurringSuggestionUiState.Visible)
        }
}
