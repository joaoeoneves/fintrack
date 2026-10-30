package com.joaoeoneves.fintrack.ui.recurring

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.monthlyEquivalentCents
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.userCategory
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

    private fun viewModel(repo: FakeRecurringRepository) = RecurringListViewModel(repo, FakeCategoryRepository(), clock)

    private fun TestScope.started(vm: RecurringListViewModel): RecurringListUiState {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm.uiState.value
    }

    private fun content(vm: RecurringListViewModel) = vm.uiState.value as RecurringListUiState.Content

    @Test
    fun savingCategoryItems_goToInvestments_notBills() =
        runTest(testDispatcher) {
            val etf =
                com.joaoeoneves.fintrack.testutil
                    .userCategory("etf", kind = com.joaoeoneves.fintrack.domain.model.CategoryKind.SAVING)
            val invest = recurringItem(id = "inv", categoryId = etf.id, amountCents = 10_000)
            val bill = recurringItem(id = "bill", amountCents = 1_000)
            val repo = FakeRecurringRepository(listOf(invest, bill))
            val vm = RecurringListViewModel(repo, FakeCategoryRepository(listOf(etf)), clock)
            started(vm)

            val state = content(vm)

            assertEquals(listOf("inv"), state.investments.map { it.item.id })
            assertEquals(listOf("bill"), state.active.map { it.item.id })
        }

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
            assertEquals(12_000L, state.cost.annualCents)
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
    fun planOwnedItems_areOrdinaryBills_listedWithTheOthers() =
        runTest(testDispatcher) {
            val owned = recurringItem(id = "own", amountCents = 5_000)
            val linkedOnly = recurringItem(id = "lnk", amountCents = 1_000)
            val ownedInactive = recurringItem(id = "off", amountCents = 7_000, active = false)
            val vm = viewModel(FakeRecurringRepository(listOf(owned, linkedOnly, ownedInactive)))
            started(vm)

            val state = content(vm)

            assertEquals(setOf("own", "lnk"), state.active.map { it.item.id }.toSet())
            assertEquals(listOf("off"), state.inactive.map { it.item.id })
            assertEquals(72_000L, state.cost.annualCents)
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
    fun delete_removesAtOnce_emitsTheItem_andUndoRestoresIt() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository(listOf(recurringItem()))
            val vm = viewModel(repo)
            val deleted = mutableListOf<com.joaoeoneves.fintrack.domain.model.RecurringItem>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.deletedEvent.collect { deleted += it }
            }
            started(vm)

            vm.onDeleteRequested("r1")
            advanceUntilIdle()

            assertTrue(repo.items.isEmpty())
            assertEquals(listOf("r1"), deleted.map { it.id })

            vm.onUndoDelete(deleted.single())
            advanceUntilIdle()

            assertEquals(listOf("r1"), repo.items.map { it.id })
        }

    @Test
    fun delete_unknownId_isNoOp_andFailureEmitsErrorWithoutEvent() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository(listOf(recurringItem()))
            val vm = viewModel(repo)
            val deleted = mutableListOf<com.joaoeoneves.fintrack.domain.model.RecurringItem>()
            val errors = mutableListOf<UiText>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.deletedEvent.collect { deleted += it }
            }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errorEvent.collect { errors += it } }
            started(vm)

            vm.onDeleteRequested("nope")
            advanceUntilIdle()
            repo.nextDeleteError = IllegalStateException("boom")
            vm.onDeleteRequested("r1")
            advanceUntilIdle()

            assertTrue(deleted.isEmpty())
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
        assertEquals(9L, monthlyEquivalentCents(103L))
    }

    @Test
    fun cost_splitsMonthlyFromYearlyItems_ofActiveBillsOnly() =
        runTest(testDispatcher) {
            val monthly = recurringItem(id = "m", amountCents = 1_000)
            val yearly = recurringItem(id = "y", amountCents = 6_000, frequency = RecurringFrequency.YEARLY)
            val paused = recurringItem(id = "p", amountCents = 9_000, active = false)
            val vm = viewModel(FakeRecurringRepository(listOf(monthly, yearly, paused)))
            started(vm)

            val cost = content(vm).cost

            assertEquals(1_000L, cost.monthlyItemsCents)
            assertEquals(6_000L, cost.yearlyItemsCents)
            assertEquals(18_000L, cost.annualCents)
            assertEquals(500L, cost.yearlyPerMonthCents)
        }
}
