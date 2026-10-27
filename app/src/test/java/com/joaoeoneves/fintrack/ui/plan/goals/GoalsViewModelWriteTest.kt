package com.joaoeoneves.fintrack.ui.plan.goals

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.MAX_GOAL_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GoalsViewModelWriteTest : GoalsViewModelTestBase() {
    private val saveFailed = UiText.StringResource(R.string.error_goal_save_failed)

    @Suppress("LongParameterList") // Test fixture: each test overrides only what it cares about.
    private fun invest(
        id: String,
        name: String = "ETF $id",
        amount: Long = 10_000,
        frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
        active: Boolean = true,
        endDate: LocalDate? = null,
        startDate: LocalDate = LocalDate.of(2026, 1, 1),
    ) = recurringItem(
        id,
        name = name,
        amountCents = amount,
        categoryId = investments,
        frequency = frequency,
        active = active,
        endDate = endDate,
        startDate = startDate,
    )

    @Test
    fun save_blankId_createsGoalAtTheEndOfTheList() =
        runTest(dispatcher) {
            goals.saveGoal(goal("a", order = 4))
            val viewModel = start()

            viewModel.onSave(goal(name = "Car"))
            advanceUntilIdle()

            val created = goals.goals.single { it.name == "Car" }
            assertEquals(5, created.order)
            assertTrue(created.id.isNotBlank())
            assertEquals(
                listOf("a", created.id),
                viewModel.uiState.value.goals
                    .map { it.goal.id },
            )
        }

    @Test
    fun save_firstGoal_getsOrderZero() =
        runTest(dispatcher) {
            val viewModel = start()

            viewModel.onSave(goal(name = "Car"))
            advanceUntilIdle()

            assertEquals(0, goals.goals.single().order)
        }

    @Test
    fun save_existingId_keepsItsOrder_andTrimsTheName() =
        runTest(dispatcher) {
            goals.saveGoal(goal("a", order = 2))
            goals.saveGoal(goal("b", order = 7))
            val viewModel = start()

            viewModel.onSave(goal("a", name = "  Renamed  ", target = 5, order = 2))
            advanceUntilIdle()

            val saved = goals.goals.single { it.id == "a" }
            assertEquals("Renamed", saved.name)
            assertEquals(5L, saved.targetCents)
            assertEquals(2, saved.order)
            assertEquals(2, goals.goals.size)
        }

    @Test
    fun save_invalid_isIgnored_withoutAnErrorEvent() =
        runTest(dispatcher) {
            val viewModel = start()
            val longName = "a".repeat(MAX_GOAL_NAME_LENGTH + 1)
            val reversed = goal(type = GoalTargetType.TOTAL, startDate = today, targetDate = today.minusDays(1))

            viewModel.onSave(goal(name = ""))
            viewModel.onSave(goal(name = "   "))
            viewModel.onSave(goal(name = longName))
            viewModel.onSave(goal(target = 0))
            viewModel.onSave(goal(target = -100))
            viewModel.onSave(goal(initial = -1))
            viewModel.onSave(reversed)
            advanceUntilIdle()

            assertTrue(goals.goals.isEmpty())
            assertTrue(errors.isEmpty())
        }

    @Test
    fun save_boundaries_areAccepted() =
        runTest(dispatcher) {
            val viewModel = start()

            viewModel.onSave(goal(name = "a".repeat(MAX_GOAL_NAME_LENGTH)))
            viewModel.onSave(goal(name = "😀".repeat(MAX_GOAL_NAME_LENGTH)))
            viewModel.onSave(goal(name = "One cent", target = 1))
            viewModel.onSave(goal(name = "Day", type = GoalTargetType.TOTAL, startDate = today, targetDate = today))
            advanceUntilIdle()

            assertEquals(4, goals.goals.size)
        }

    @Test
    fun save_failure_emitsErrorEvent() =
        runTest(dispatcher) {
            val viewModel = start()
            goals.nextSaveError = IllegalStateException("nope")

            viewModel.onSave(goal(name = "Car"))
            advanceUntilIdle()

            assertEquals(listOf<UiText>(saveFailed), errors)
            assertTrue(goals.goals.isEmpty())
        }

    @Test
    fun delete_removesTheGoal() =
        runTest(dispatcher) {
            goals.saveGoal(goal("a"))
            goals.saveGoal(goal("b"))
            val viewModel = start()

            viewModel.onDelete("a")
            advanceUntilIdle()

            assertEquals(listOf("b"), goals.goals.map { it.id })
            assertEquals(
                listOf("b"),
                viewModel.uiState.value.goals
                    .map { it.goal.id },
            )
        }

    @Test
    fun delete_missing_isNoOp_noError() =
        runTest(dispatcher) {
            val viewModel = start()

            viewModel.onDelete("ghost")
            advanceUntilIdle()

            assertTrue(errors.isEmpty())
        }

    @Test
    fun delete_failure_emitsErrorEvent_andKeepsTheGoal() =
        runTest(dispatcher) {
            goals.saveGoal(goal("a"))
            val viewModel = start()
            goals.nextDeleteError = IllegalStateException("nope")

            viewModel.onDelete("a")
            advanceUntilIdle()

            assertEquals(listOf<UiText>(saveFailed), errors)
            assertEquals(1, goals.goals.size)
        }

    @Test
    fun candidates_onlyActiveUnendedUnlinkedInvestmentsExpenseItems() =
        runTest(dispatcher) {
            recurring.setItems(
                listOf(
                    invest("ok"),
                    invest("linked"),
                    invest("paused", active = false),
                    invest("ended", endDate = today.minusDays(1)),
                    invest("endsToday", endDate = today),
                    recurringItem("food", categoryId = BuiltInCategories.FOOD.id),
                    recurringItem("income", type = RecurringType.INCOME),
                ),
            )
            goals.saveGoal(goal("g", recurringIds = setOf("linked")))

            val candidates = start().uiState.value.investmentCandidates

            assertEquals(listOf("endsToday", "ok"), candidates.map { it.id }.sorted())
        }

    @Test
    fun candidates_linkedOnlyToAnArchivedGoal_stillCountAsLinked() =
        runTest(dispatcher) {
            recurring.setItems(listOf(invest("a")))
            goals.saveGoal(goal("g", archived = true, recurringIds = setOf("a")))

            assertTrue(
                start()
                    .uiState.value.investmentCandidates
                    .isEmpty(),
            )
        }

    @Test
    fun createFromInvestments_makesOneAnnualGoalPerCandidate() =
        runTest(dispatcher) {
            recurring.setItems(
                listOf(
                    invest("m", name = "Index fund", amount = 10_000),
                    invest("y", name = "Bonds", amount = 70_000, frequency = RecurringFrequency.YEARLY),
                    invest("linked"),
                    invest("paused", active = false),
                    recurringItem("food", categoryId = BuiltInCategories.FOOD.id),
                ),
            )
            goals.saveGoal(goal("g", order = 3, recurringIds = setOf("linked")))
            val viewModel = start()

            viewModel.onCreateFromInvestments()
            advanceUntilIdle()

            val created = goals.goals.filter { it.id != "g" }.sortedBy { it.order }
            assertEquals(listOf("Index fund", "Bonds"), created.map { it.name })
            assertEquals(listOf(120_000L, 70_000L), created.map { it.targetCents })
            assertEquals(listOf(4, 5), created.map { it.order })
            assertTrue(created.all { it.targetType == GoalTargetType.ANNUAL && it.startDate == today })
            assertEquals(listOf(setOf("m"), setOf("y")), created.map { it.linkedRecurringIds })
            assertTrue(errors.isEmpty())
        }

    @Test
    fun createFromInvestments_twice_doesNotDuplicate() =
        runTest(dispatcher) {
            recurring.setItems(listOf(invest("a"), invest("b")))
            val viewModel = start()

            viewModel.onCreateFromInvestments()
            advanceUntilIdle()
            viewModel.onCreateFromInvestments()
            advanceUntilIdle()

            assertEquals(2, goals.goals.size)
            assertTrue(
                viewModel.uiState.value.investmentCandidates
                    .isEmpty(),
            )
        }

    @Test
    fun createFromInvestments_noCandidates_doesNothing() =
        runTest(dispatcher) {
            recurring.setItems(listOf(recurringItem("food", categoryId = BuiltInCategories.FOOD.id)))
            val viewModel = start()

            viewModel.onCreateFromInvestments()
            advanceUntilIdle()

            assertEquals(0, goals.saveCount)
            assertTrue(errors.isEmpty())
        }

    @Test
    fun createFromInvestments_truncatesLongNamesToFiftyCodePoints() =
        runTest(dispatcher) {
            val emojiName = "💰".repeat(60)
            recurring.setItems(
                listOf(
                    invest("a", name = "n".repeat(60)),
                    invest("b", name = emojiName),
                    invest("c", name = "x".repeat(MAX_GOAL_NAME_LENGTH)),
                ),
            )
            val viewModel = start()

            viewModel.onCreateFromInvestments()
            advanceUntilIdle()

            val names = goals.goals.associate { goal -> goal.linkedRecurringIds.single() to goal.name }
            assertEquals("n".repeat(MAX_GOAL_NAME_LENGTH), names["a"])
            assertEquals("💰".repeat(MAX_GOAL_NAME_LENGTH), names["b"])
            assertEquals("x".repeat(MAX_GOAL_NAME_LENGTH), names["c"])
        }

    @Test
    fun createFromInvestments_trimsTheItemName() =
        runTest(dispatcher) {
            recurring.setItems(listOf(invest("a", name = "  Fund  ")))
            val viewModel = start()

            viewModel.onCreateFromInvestments()
            advanceUntilIdle()

            assertEquals("Fund", goals.goals.single().name)
        }

    @Test
    fun createFromInvestments_saveFailure_emitsErrorEvent() =
        runTest(dispatcher) {
            recurring.setItems(listOf(invest("a"), invest("b")))
            val viewModel = start()
            goals.nextSaveError = IllegalStateException("nope")

            viewModel.onCreateFromInvestments()
            advanceUntilIdle()

            assertEquals(listOf<UiText>(saveFailed), errors)
            assertTrue(goals.goals.isEmpty())
        }
}
