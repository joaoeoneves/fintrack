package com.joaoeoneves.fintrack.ui.recurring

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RecurringListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val clock = MutableClock(Instant.parse("2026-03-15T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repo: FakeRecurringRepository,
        goals: FakeGoalRepository = FakeGoalRepository(),
    ) = RecurringListViewModel(repo, FakeCategoryRepository(), goals, clock)

    private fun TestScope.started(vm: RecurringListViewModel): RecurringListUiState {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm.uiState.value
    }

    private fun content(vm: RecurringListViewModel) = vm.uiState.value as RecurringListUiState.Content

    @Test
    fun splitsActiveFromPausedAndEnded_withTotalsOfActiveOnly() =
        runTest(testDispatcher) {
            val active = recurringItem(id = "a", amountCents = 1_000)
            val paused = recurringItem(id = "p", amountCents = 9_000, active = false)
            val ended = recurringItem(id = "e", amountCents = 9_000, endDate = day(2026, 3, 14))
            val income = recurringItem(id = "i", type = RecurringType.INCOME, amountCents = 2_000)
            val vm = viewModel(FakeRecurringRepository(listOf(active, paused, ended, income)))
            started(vm)

            val state = content(vm)

            assertEquals(setOf("a"), state.active.map { it.item.id }.toSet())
            assertEquals(setOf("p", "e"), state.inactive.map { it.item.id }.toSet())
            assertEquals(12_000L, state.annualExpenseCents)
            assertTrue(state.inactive.first { it.item.id == "e" }.isEnded)
            assertNull(state.inactive.first { it.item.id == "p" }.nextDue)
            assertEquals(
                BuiltInCategories.FOOD.id,
                state.active
                    .first { it.item.id == "a" }
                    .category
                    ?.id,
            )
        }

    @Test
    fun planOwnedItems_areHidden_otherSavingItemsStay() =
        runTest(testDispatcher) {
            val owned = recurringItem(id = "own", amountCents = 5_000)
            val linkedOnly = recurringItem(id = "lnk", amountCents = 1_000)
            val ownedInactive = recurringItem(id = "off", amountCents = 7_000, active = false)
            val goals =
                FakeGoalRepository(
                    listOf(
                        SavingsGoal("g1", "Plan", 0, GoalTargetType.ANNUAL, day(2026, 1, 1), recurringId = "own"),
                        SavingsGoal(
                            "g2",
                            "Linked",
                            100,
                            GoalTargetType.ANNUAL,
                            day(2026, 1, 1),
                            linkedRecurringIds = setOf("lnk"),
                        ),
                        SavingsGoal("g3", "Off", 0, GoalTargetType.ANNUAL, day(2026, 1, 1), recurringId = "off"),
                    ),
                )
            val vm = viewModel(FakeRecurringRepository(listOf(owned, linkedOnly, ownedInactive)), goals)
            started(vm)

            val state = content(vm)

            assertEquals(listOf("lnk"), state.active.map { it.item.id })
            assertTrue(state.inactive.isEmpty())
            assertEquals(12_000L, state.annualExpenseCents)
        }

    @Test
    fun endingToday_isStillActive_andEmptyListIsEmptyContent() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeRecurringRepository(listOf(recurringItem(endDate = day(2026, 3, 15)))))
            started(vm)
            assertEquals(1, content(vm).active.size)

            val empty = viewModel(FakeRecurringRepository())
            started(empty)
            assertTrue(content(empty).active.isEmpty() && content(empty).inactive.isEmpty())
        }

    @Test
    fun pause_setsInactive_keepsCursor() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository(listOf(recurringItem(handledThrough = day(2026, 2, 15))))
            val vm = viewModel(repo)
            started(vm)

            vm.onTogglePaused("r1")
            advanceUntilIdle()

            assertEquals(false, repo.items.single().active)
            assertEquals(day(2026, 2, 15), repo.items.single().handledThrough)
            assertEquals(1, content(vm).inactive.size)
        }

    @Test
    fun resume_movesCursorToLastOccurrenceBeforeToday() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository(listOf(recurringItem(active = false, handledThrough = day(2026, 1, 15))))
            val vm = viewModel(repo)
            started(vm)

            vm.onTogglePaused("r1")
            advanceUntilIdle()

            assertEquals(true, repo.items.single().active)
            // Today is the 15th: the 15th itself is still pending, earlier ones are skipped.
            assertEquals(day(2026, 2, 15), repo.items.single().handledThrough)
            assertEquals(1, content(vm).active.size)
        }

    @Test
    fun pause_recordsTheDay() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository(listOf(recurringItem(handledThrough = day(2026, 2, 15))))
            val vm = viewModel(repo)
            started(vm)

            vm.onTogglePaused("r1")
            advanceUntilIdle()

            assertEquals(day(2026, 3, 15), repo.items.single().pausedAt)
        }

    @Test
    fun resume_withPausedAt_recordsThePausedGapAsSkipped_andClearsPausedAt() =
        runTest(testDispatcher) {
            val paused =
                recurringItem(handledThrough = day(2026, 1, 15), active = false, pausedAt = day(2026, 2, 20))
            val repo = FakeRecurringRepository(listOf(paused))
            val vm = viewModel(repo)
            started(vm)

            vm.onTogglePaused("r1")
            advanceUntilIdle()

            val item = repo.items.single()
            assertEquals(true, item.active)
            assertEquals(null, item.pausedAt)
            // Feb 15 was pending before the pause: stays pending. Nothing fell inside the gap before today.
            assertEquals(day(2026, 1, 15), item.handledThrough)
            assertEquals(emptySet<java.time.LocalDate>(), item.skipped)
        }

    @Test
    fun resume_legacyItem_skipsEarlierOccurrences() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository(listOf(recurringItem(active = false, handledThrough = day(2026, 1, 15))))
            val vm = viewModel(repo)
            started(vm)

            vm.onTogglePaused("r1")
            advanceUntilIdle()

            assertEquals(setOf(day(2026, 2, 15)), repo.items.single().skipped)
        }

    @Test
    fun toggleUnknownId_isNoOp() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository(listOf(recurringItem()))
            val vm = viewModel(repo)
            started(vm)

            vm.onTogglePaused("nope")
            advanceUntilIdle()

            assertEquals(true, repo.items.single().active)
        }

    @Test
    fun toggleFailure_emitsError() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository(listOf(recurringItem()))
            val vm = viewModel(repo)
            val errors = mutableListOf<UiText>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errorEvent.collect { errors += it } }
            started(vm)
            repo.nextSaveError = IllegalStateException("boom")

            vm.onTogglePaused("r1")
            advanceUntilIdle()

            assertEquals(listOf<UiText>(UiText.StringResource(R.string.error_recurring_save_failed)), errors)
            assertEquals(true, repo.items.single().active)
        }

    @Test
    fun deleteFlow_requestDismissConfirm() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository(listOf(recurringItem()))
            val vm = viewModel(repo)
            started(vm)

            vm.onDeleteRequested("r1")
            advanceUntilIdle()
            assertEquals("r1", content(vm).deleteTarget?.id)

            vm.onDismissDelete()
            advanceUntilIdle()
            assertNull(content(vm).deleteTarget)
            assertEquals(1, repo.items.size)

            vm.onDeleteRequested("r1")
            advanceUntilIdle()
            vm.onConfirmDelete()
            advanceUntilIdle()
            assertTrue(repo.items.isEmpty())
            assertNull(content(vm).deleteTarget)
        }

    @Test
    fun deleteFailure_emitsError_andConfirmWithoutTargetIsNoOp() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository(listOf(recurringItem()))
            val vm = viewModel(repo)
            val errors = mutableListOf<UiText>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errorEvent.collect { errors += it } }
            started(vm)

            vm.onConfirmDelete()
            advanceUntilIdle()
            assertTrue(errors.isEmpty())

            repo.nextDeleteError = IllegalStateException("boom")
            vm.onDeleteRequested("r1")
            advanceUntilIdle()
            vm.onConfirmDelete()
            advanceUntilIdle()

            assertEquals(1, errors.size)
            assertEquals(1, repo.items.size)
        }

    @Test
    fun observeFailure_showsError_thenRetryRecovers() =
        runTest(testDispatcher) {
            val repo =
                FakeRecurringRepository(listOf(recurringItem()), nextObserveError = IllegalStateException("down"))
            val vm = viewModel(repo)
            assertTrue(started(vm) is RecurringListUiState.Error)

            repo.nextObserveError = null
            vm.onRetry()
            advanceUntilIdle()

            assertEquals(1, content(vm).active.size)
        }

    @Test
    fun monthlyEquivalent_dividesByTwelve() {
        assertEquals(1_000L, monthlyEquivalentCents(12_000L))
        assertEquals(0L, monthlyEquivalentCents(0L))
        assertEquals(8L, monthlyEquivalentCents(100L))
    }
}
