package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Goals linked to a category are counted by [CheckCategoryUsage] and re-pointed by [MoveCategoryItems]. */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryGoalLinksTest {
    private val source = BuiltInCategories.RECURRING.id
    private val target = BuiltInCategories.FOOD.id
    private val other = BuiltInCategories.SHOPPING.id
    private val unused = CategoryId("unused")
    private val expenses = FakeExpenseRepository()
    private val budgets = FakeBudgetRepository()
    private val recurring = FakeRecurringRepository()

    private fun goal(
        id: String,
        category: CategoryId?,
    ) = SavingsGoal(id, "Goal $id", 1_000L, GoalTargetType.TOTAL, LocalDate.of(2026, 1, 1), linkedCategoryId = category)

    private val goals = FakeGoalRepository(listOf(goal("g1", source), goal("g2", source), goal("g3", other)))

    private fun move() = MoveCategoryItems(expenses, budgets, recurring, UnconfinedTestDispatcher(), goals)

    @Test
    fun move_rewritesTheLinkOfEveryLinkedGoal_andCountsThem() =
        runTest {
            val result = move()(source, target).getOrThrow()

            assertEquals(2, result.goalCount)
            assertEquals(
                mapOf("g1" to target, "g2" to target, "g3" to other),
                goals.goals.associate { it.id to it.linkedCategoryId },
            )
        }

    @Test
    fun move_failingGoalSave_failsTheResult() =
        runTest {
            goals.nextSaveError = IllegalStateException("offline")

            assertTrue(move()(source, target).isFailure)
        }

    @Test
    fun preview_countsLinkedGoals_andIsNotEmptyBecauseOfThem() =
        runTest {
            val preview = move().preview(source, target).getOrThrow()

            assertEquals(2, preview.goalCount)
            assertFalse(preview.isEmpty)
            assertTrue(move().preview(unused).getOrThrow().isEmpty)
        }

    @Test
    fun usage_flagsAGoalLink_andMarksTheCategoryInUse() =
        runTest {
            val check = CheckCategoryUsage(expenses, budgets, recurring, goals)

            val linked = check(source).getOrThrow()
            val free = check(unused).getOrThrow()

            assertTrue(linked.hasGoals)
            assertTrue(linked.inUse)
            assertFalse(free.hasGoals)
            assertFalse(free.inUse)
        }
}
