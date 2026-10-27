package com.joaoeoneves.fintrack.ui.plan.goals

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.domain.model.GoalStatus
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GoalsViewModelStateTest : GoalsViewModelTestBase() {
    private fun at(
        year: Int,
        month: Int,
        day: Int,
    ) = LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant()

    @Test
    fun beforeCollecting_isNotLoaded() =
        runTest(dispatcher) {
            val viewModel =
                GoalsViewModel(
                    GoalsSources(goals, recurring, categories, transactions),
                    clock,
                )

            assertFalse(viewModel.uiState.value.loaded)
        }

    @Test
    fun noGoals_isLoadedAndEmpty() =
        runTest(dispatcher) {
            val state = start().uiState.value

            assertTrue(state.loaded)
            assertTrue(state.goals.isEmpty())
            assertTrue(state.investmentCandidates.isEmpty())
        }

    @Test
    fun archivedGoals_areHidden() =
        runTest(dispatcher) {
            goals.saveGoal(goal("a", archived = true))
            goals.saveGoal(goal("b"))

            val state = start().uiState.value

            assertEquals(listOf("b"), state.goals.map { it.goal.id })
        }

    @Test
    fun progress_countsLinkedRecurringExpenses_refundsSubtract() =
        runTest(dispatcher) {
            goals.saveGoal(goal("a", recurringIds = setOf("r1")))
            transactions.expenses =
                listOf(
                    testExpense("rec_r1_20260301", 1_000, at(2026, 3, 1)),
                    testExpense("rec_r1_20260401", 1_000, at(2026, 4, 1)),
                    testExpense(
                        "rec_r1_20260402",
                        300,
                        at(2026, 4, 2),
                        isRefund = true,
                    ),
                    testExpense("rec_r2_20260401", 5, at(2026, 4, 1)),
                )

            val progress =
                start()
                    .uiState.value.goals
                    .single()

            assertEquals(1_700L, progress.savedCents)
            assertEquals(GoalStatus.BEHIND, progress.status)
        }

    @Test
    fun progress_categoryLink_usesResolvedCategory() =
        runTest(dispatcher) {
            val savings = userCategory("savings")
            categories.setDocs(listOf(savings))
            goals.saveGoal(goal("a").copy(linkedCategoryId = savings.id))
            transactions.expenses =
                listOf(testExpense("e1", 700, at(2026, 2, 1), savings))

            assertEquals(
                700L,
                start()
                    .uiState.value.goals
                    .single()
                    .savedCents,
            )
        }

    @Test
    fun totalGoal_queryRangeReachesBackToItsStartDate() =
        runTest(dispatcher) {
            goals.saveGoal(goal("t", type = GoalTargetType.TOTAL, startDate = LocalDate.of(2023, 5, 1), initial = 50))

            start()

            val range = transactions.ranges.last()
            assertEquals(at(2023, 5, 1), range.startInclusive)
        }

    @Test
    fun annualOnlyGoals_queryRangeStartsAtTheCurrentYear() =
        runTest(dispatcher) {
            goals.saveGoal(goal("a", startDate = LocalDate.of(2020, 1, 1)))

            start()

            assertEquals(
                at(2026, 1, 1),
                transactions.ranges.last().startInclusive,
            )
        }

    @Test
    fun options_listExpenseRecurringByName_andSelectableCategories() =
        runTest(dispatcher) {
            recurring.setItems(
                listOf(
                    recurringItem("b", name = "beta"),
                    recurringItem("a", name = "Alpha"),
                    recurringItem("i", type = RecurringType.INCOME, name = "Pay"),
                ),
            )
            categories.setDocs(listOf(userCategory("old", archived = true), userCategory("live")))

            val options = start().uiState.value.options

            assertEquals(listOf("a", "b"), options.recurring.map { it.id })
            assertTrue(options.categories.any { it.id.value == "live" })
            assertTrue(options.categories.none { it.id.value == "old" })
        }

    @Test
    fun observeFailure_isLoadedWithEmptyState() =
        runTest(dispatcher) {
            val failing = FakeGoalRepository(nextObserveError = IllegalStateException("boom"))

            val state = start(goalRepo = failing).uiState.value

            assertTrue(state.loaded)
            assertTrue(state.goals.isEmpty())
        }

    @Test
    fun transactionsFailure_isLoadedWithEmptyState() =
        runTest(dispatcher) {
            goals.saveGoal(goal("a"))

            val state = start(transactionRepo = Transactions(fail = true)).uiState.value

            assertTrue(state.loaded)
            assertTrue(state.goals.isEmpty())
        }
}
