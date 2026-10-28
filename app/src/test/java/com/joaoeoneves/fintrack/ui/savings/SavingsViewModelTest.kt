package com.joaoeoneves.fintrack.ui.savings

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.GoalStatus
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import com.joaoeoneves.fintrack.testutil.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SavingsViewModelTest {
    private class Transactions(
        var expenses: List<Expense> = emptyList(),
        var income: List<Income> = emptyList(),
        var fail: Boolean = false,
    ) : TransactionRepository {
        override fun observeTransactions(range: InstantRange): Flow<TransactionsInRange> =
            flow {
                if (fail) error("boom")
                emit(
                    TransactionsInRange(
                        range,
                        expenses.filter { it.date >= range.startInclusive && it.date < range.endExclusive },
                        income.filter { it.date >= range.startInclusive && it.date < range.endExclusive },
                    ),
                )
            }
    }

    private val dispatcher = StandardTestDispatcher()
    private val clock = MutableClock(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    private val goals = FakeGoalRepository()
    private val recurring = FakeRecurringRepository()
    private val categories = FakeCategoryRepository()
    private val transactions = Transactions()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.start(): SavingsViewModel {
        val vm = SavingsViewModel(SavingsSources(goals, recurring, categories, transactions), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun expense(
        id: String,
        cents: Long,
        date: String = "2026-10-05T10:00:00Z",
        category: Category = BuiltInCategories.INVESTMENTS,
        refund: Boolean = false,
    ) = Expense(id, "x", cents, category, Instant.parse(date), null, refund)

    private fun income(
        cents: Long,
        date: String = "2026-09-01T10:00:00Z",
    ) = Income("i$cents$date", "Salary", cents, Instant.parse(date), null)

    @Test
    fun beforeFirstEmission_isNotLoaded() =
        runTest(dispatcher) {
            val vm = SavingsViewModel(SavingsSources(goals, recurring, categories, transactions), clock)
            assertFalse(vm.uiState.value.loaded)
        }

    @Test
    fun emptyState_isLoadedWithoutCards() =
        runTest(dispatcher) {
            val state = start().uiState.value
            assertTrue(state.loaded)
            assertEquals(2026, state.year)
            assertTrue(state.cards.isEmpty())
            assertEquals(0L, state.savedCents)
            assertNull(state.savingsRate)
        }

    @Test
    fun savedYtd_netsRefundsAndIgnoresPreviousYearsAndSpending() =
        runTest(dispatcher) {
            transactions.expenses =
                listOf(
                    expense("a", 30_000L),
                    expense("b", 5_000L, refund = true),
                    expense("c", 99_000L, date = "2025-12-31T10:00:00Z"),
                    expense("d", 7_000L, category = BuiltInCategories.FOOD),
                )
            transactions.income = listOf(income(100_000L))

            val state = start().uiState.value

            assertEquals(25_000L, state.savedCents)
            assertEquals(0.25, state.savingsRate!!, 1e-9)
        }

    @Test
    fun savingsRate_isHiddenWithZeroIncome() =
        runTest(dispatcher) {
            transactions.expenses = listOf(expense("a", 30_000L))

            val state = start().uiState.value

            assertEquals(30_000L, state.savedCents)
            assertNull(state.savingsRate)
        }

    @Test
    fun savedYtd_netNegativeWhenOnlyRefunds() =
        runTest(dispatcher) {
            transactions.expenses = listOf(expense("a", 4_000L, refund = true))
            transactions.income = listOf(income(10_000L))

            val state = start().uiState.value

            assertEquals(-4_000L, state.savedCents)
            assertEquals(-0.4, state.savingsRate!!, 1e-9)
        }

    @Test
    fun cardsAreOrderedAndArchivedPlansHidden() =
        runTest(dispatcher) {
            goals.saveGoal(savingsGoal(id = "b", name = "B", order = 1))
            goals.saveGoal(savingsGoal(id = "a", name = "A", order = 0))
            goals.saveGoal(savingsGoal(id = "z", name = "Z", order = 2, archived = true))

            assertEquals(
                listOf("a", "b"),
                start()
                    .uiState.value.cards
                    .map { it.id },
            )
        }

    @Test
    fun ownedActiveItem_mapsScheduleAndCategory() =
        runTest(dispatcher) {
            goals.saveGoal(savingsGoal(recurringId = "r1"))
            recurring.saveRecurring(savingsItem(amount = 10_000L, day = 5))
            transactions.expenses = listOf(expense("rec_r1_2026-10-05", 50_000L))

            val card =
                start()
                    .uiState.value.cards
                    .single()

            assertEquals(10_000L, card.monthlyCents)
            assertEquals(5, card.dayOfMonth)
            assertEquals(BuiltInCategories.INVESTMENTS.id, card.category?.id)
            assertNull(card.suggestedCents)
            assertEquals(50_000L, card.progress.savedCents)
        }

    @Test
    fun yearlyItem_showsMonthlyShare() =
        runTest(dispatcher) {
            goals.saveGoal(savingsGoal(recurringId = "r1"))
            recurring.saveRecurring(
                savingsItem(amount = 120_000L, frequency = RecurringFrequency.YEARLY, month = 3),
            )

            assertEquals(
                10_000L,
                start()
                    .uiState.value.cards
                    .single()
                    .monthlyCents,
            )
        }

    @Test
    fun status_behindWhenUnderPace() =
        runTest(dispatcher) {
            goals.saveGoal(savingsGoal(recurringId = "r1"))
            recurring.saveRecurring(savingsItem())
            transactions.expenses = listOf(expense("rec_r1_a", 50_000L))

            assertEquals(
                GoalStatus.BEHIND,
                start()
                    .uiState.value.cards
                    .single()
                    .progress.status,
            )
        }

    @Test
    fun status_onTrackWhenAheadOfPace() =
        runTest(dispatcher) {
            goals.saveGoal(savingsGoal(recurringId = "r1"))
            recurring.saveRecurring(savingsItem())
            transactions.expenses = listOf(expense("rec_r1_a", 100_000L))

            assertEquals(
                GoalStatus.ON_TRACK,
                start()
                    .uiState.value.cards
                    .single()
                    .progress.status,
            )
        }

    @Test
    fun status_doneAtTarget_evenWithoutMonthly() =
        runTest(dispatcher) {
            goals.saveGoal(savingsGoal(recurringId = "r1"))
            transactions.expenses = listOf(expense("rec_r1_a", 120_000L))

            val card =
                start()
                    .uiState.value.cards
                    .single()

            assertEquals(GoalStatus.DONE, card.progress.status)
            assertNull(card.suggestedCents)
        }

    @Test
    fun status_noMonthlyWhenItemMissingInactiveOrEnded() =
        runTest(dispatcher) {
            goals.saveGoal(savingsGoal(id = "dangling", recurringId = "gone", order = 0))
            goals.saveGoal(savingsGoal(id = "off", recurringId = "r1", order = 1))
            goals.saveGoal(savingsGoal(id = "ended", recurringId = "r2", order = 2))
            recurring.saveRecurring(savingsItem(id = "r1", active = false))
            recurring.saveRecurring(savingsItem(id = "r2", end = LocalDate.of(2026, 6, 30)))

            val cards = start().uiState.value.cards

            assertTrue(cards.all { it.progress.status == GoalStatus.NO_MONTHLY })
            assertTrue(cards.all { it.monthlyCents == null && it.dayOfMonth == null && it.category == null })
        }

    @Test
    fun suggestion_annualSpreadsRemainderOverRestOfYear() =
        runTest(dispatcher) {
            goals.saveGoal(savingsGoal())

            val card =
                start()
                    .uiState.value.cards
                    .single()

            // 1200 EUR over October, November and December.
            assertEquals(40_000L, card.suggestedCents)
            assertNull(card.monthlyCents)
        }

    @Test
    fun suggestion_totalWithDateUsesMonthsToDate_withoutDateIsHidden() =
        runTest(dispatcher) {
            goals.saveGoal(
                savingsGoal(
                    id = "dated",
                    target = 100_000L,
                    type = GoalTargetType.TOTAL,
                    targetDate = LocalDate.of(2027, 4, 15),
                    initial = 10_000L,
                    order = 0,
                ),
            )
            goals.saveGoal(savingsGoal(id = "open", target = 100_000L, type = GoalTargetType.TOTAL, order = 1))

            val cards = start().uiState.value.cards

            assertEquals(90_000L / 6, cards[0].suggestedCents)
            assertEquals(10_000L, cards[0].progress.savedCents)
            assertNull(cards[1].suggestedCents)
        }

    @Test
    fun noTargetNoMonthly_hasNoSuggestionAndNoFraction() =
        runTest(dispatcher) {
            goals.saveGoal(savingsGoal(target = 0L))

            val card =
                start()
                    .uiState.value.cards
                    .single()

            assertNull(card.suggestedCents)
            assertEquals(0f, card.progress.fraction, 0f)
            assertEquals(0L, card.progress.effectiveTargetCents)
        }

    @Test
    fun noTarget_withMonthly_fillsAgainstYearlyPlan() =
        runTest(dispatcher) {
            goals.saveGoal(savingsGoal(target = 0L, recurringId = "r1"))
            recurring.saveRecurring(savingsItem(amount = 10_000L))
            transactions.expenses = listOf(expense("rec_r1_a", 30_000L))

            val card =
                start()
                    .uiState.value.cards
                    .single()

            assertEquals(120_000L, card.progress.effectiveTargetCents)
            assertEquals(0.25f, card.progress.fraction, 1e-6f)
        }

    @Test
    fun totalPlan_countsContributionsFromBeforeThisYear() =
        runTest(dispatcher) {
            goals.saveGoal(
                savingsGoal(
                    target = 500_000L,
                    type = GoalTargetType.TOTAL,
                    startDate = LocalDate.of(2025, 1, 1),
                    recurringId = "r1",
                ),
            )
            recurring.saveRecurring(savingsItem())
            transactions.expenses = listOf(expense("rec_r1_2025", 20_000L, date = "2025-06-05T10:00:00Z"))

            val state = start().uiState.value

            assertEquals(
                20_000L,
                state.cards
                    .single()
                    .progress.savedCents,
            )
            assertEquals(0L, state.savedCents)
        }

    @Test
    fun repositoryFailure_emitsLoadedEmptyState() =
        runTest(dispatcher) {
            goals.saveGoal(savingsGoal())
            transactions.fail = true

            val state = start().uiState.value

            assertTrue(state.loaded)
            assertTrue(state.cards.isEmpty())
        }
}
