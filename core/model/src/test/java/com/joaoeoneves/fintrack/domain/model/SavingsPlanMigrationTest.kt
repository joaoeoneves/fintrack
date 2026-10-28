package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SavingsPlanMigrationTest {
    private val today = LocalDate.of(2026, 10, 28)
    private val saving = Category(CategoryId("invest"), "Invest", "icon", "slate", CategoryKind.SAVING, 0, false, false)
    private val spending = Category(CategoryId("food"), "Food", "icon", "slate", CategoryKind.SPENDING, 0, false, false)
    private val categories = listOf(saving, spending)

    private fun item(
        id: String,
        category: String = "invest",
        name: String = "Fund $id",
        type: RecurringType = RecurringType.EXPENSE,
    ) = recurringItem(
        id = id,
        name = name,
        type = type,
        categoryId = CategoryId(category),
        startDate = LocalDate.of(2026, 2, 1),
    )

    private fun migrate(
        goals: List<SavingsGoal> = emptyList(),
        recurring: List<RecurringItem> = emptyList(),
        cats: List<Category> = categories,
        envelopes: List<Envelope> = emptyList(),
    ) = savingsPlanMigrationOf(goals, recurring, cats, today, envelopes)

    @Test
    fun nothing_isEmpty() {
        assertTrue(migrate().isEmpty)
    }

    @Test
    fun savingItemWithoutGoal_becomesANoTargetAnnualPlan() {
        val plan = migrate(recurring = listOf(item("r1", name = "  ETF  "))).newGoals.single()

        assertEquals("plan_r1", plan.id)
        assertEquals("ETF", plan.name)
        assertEquals(0L, plan.targetCents)
        assertEquals(GoalTargetType.ANNUAL, plan.targetType)
        assertEquals(LocalDate.of(2026, 2, 1), plan.startDate)
        assertEquals("r1", plan.recurringId)
        assertEquals(0, plan.order)
    }

    @Test
    fun nameIsCutToTheLimit_andOrdersFollowTheExistingGoals() {
        val existing = savingsGoal(id = "g", order = 4)
        val result = migrate(listOf(existing), listOf(item("b", name = "x".repeat(80)), item("a")))

        assertEquals(listOf("plan_a", "plan_b"), result.newGoals.map { it.id })
        assertEquals(listOf(5, 6), result.newGoals.map { it.order })
        assertEquals(MAX_GOAL_NAME_LENGTH, result.newGoals[1].name.length)
    }

    @Test
    fun ignoresSpendingIncomeInactiveEndedAndUnknownCategoryItems() {
        val recurring =
            listOf(
                item("spend", category = "food"),
                item("inc", type = RecurringType.INCOME),
                item("off").copy(active = false),
                item("ended").copy(endDate = LocalDate.of(2026, 10, 27)),
                item("unknown", category = "gone"),
            )

        assertTrue(migrate(recurring = recurring).isEmpty)
    }

    @Test
    fun itemEndingToday_isStillActive() {
        val result = migrate(recurring = listOf(item("r1").copy(endDate = today)))

        assertEquals(listOf("plan_r1"), result.newGoals.map { it.id })
    }

    @Test
    fun partialCache_categoryMissing_skipsTheItem() {
        assertTrue(migrate(recurring = listOf(item("r1")), cats = emptyList()).isEmpty)
    }

    @Test
    fun itemCountedByAGoal_isNotMigrated() {
        val byLink = savingsGoal(linkedRecurringIds = setOf("r1", "other"))
        val byOwner = savingsGoal(id = "g2", recurringId = "r2")

        assertTrue(migrate(listOf(byLink, byOwner), listOf(item("r1"), item("r2"))).newGoals.isEmpty())
    }

    @Test
    fun singleLinkGoal_ownsTheItem_noNewGoal() {
        val goal = savingsGoal(linkedRecurringIds = setOf("r1"))

        val result = migrate(listOf(goal), listOf(item("r1")))

        assertEquals("r1", result.patchedGoals.single().recurringId)
        assertEquals(goal.id, result.patchedGoals.single().id)
        assertTrue(result.newGoals.isEmpty())
    }

    @Test
    fun singleLink_toANonSavingItem_isNotPatched() {
        val goal = savingsGoal(linkedRecurringIds = setOf("r1"))

        assertTrue(migrate(listOf(goal), listOf(item("r1", category = "food"))).patchedGoals.isEmpty())
    }

    @Test
    fun multiLinkGoal_isUntouched_andItsItemsAreNotRecreated() {
        val goal = savingsGoal(linkedRecurringIds = setOf("r1", "r2"))

        val result = migrate(listOf(goal), listOf(item("r1"), item("r2")))

        assertTrue(result.isEmpty)
    }

    @Test
    fun twoGoalsLinkingTheSameItem_onlyTheFirstOwnsIt() {
        val first = savingsGoal(id = "a", order = 0, linkedRecurringIds = setOf("r1"))
        val second = savingsGoal(id = "b", order = 1, linkedRecurringIds = setOf("r1"))

        val result = migrate(listOf(second, first), listOf(item("r1")))

        assertEquals(listOf("a"), result.patchedGoals.map { it.id })
        assertTrue(result.newGoals.isEmpty())
    }

    @Test
    fun linkedItemOwnedByAnotherGoal_isNotPatched() {
        val owner = savingsGoal(id = "o", recurringId = "r1")
        val linker = savingsGoal(id = "l", linkedRecurringIds = setOf("r1"))

        assertTrue(migrate(listOf(owner, linker), listOf(item("r1"))).isEmpty)
    }

    @Test
    fun existingPlanIdCollision_isSkipped() {
        val clash = savingsGoal(id = "plan_r1", recurringId = null)

        assertTrue(migrate(listOf(clash), listOf(item("r1"))).newGoals.isEmpty())
    }

    @Test
    fun isIdempotent_applyingTheResultMigratesNothingMore() {
        val goals = listOf(savingsGoal(id = "g", linkedRecurringIds = setOf("r1")))
        val recurring = listOf(item("r1"), item("r2"))
        val first = migrate(goals, recurring)

        val applied = goals.map { g -> first.patchedGoals.firstOrNull { it.id == g.id } ?: g } + first.newGoals

        assertEquals(1, first.patchedGoals.size)
        assertEquals(1, first.newGoals.size)
        assertTrue(migrate(applied, recurring).isEmpty)
    }

    @Test
    fun savingOnlyEnvelopes_areListed_mixedAndEmptyOnesAreNot() {
        val savingOnly = Envelope("e1", "Invest", 0)
        val mixed = Envelope("e2", "Mixed", 1)
        val empty = Envelope("e3", "Empty", 2)
        val cats =
            listOf(
                saving.copy(groupId = "e1"),
                saving.copy(id = CategoryId("etf"), groupId = "e1"),
                saving.copy(id = CategoryId("pension"), groupId = "e2"),
                spending.copy(groupId = "e2"),
            )

        val result = migrate(cats = cats, envelopes = listOf(empty, mixed, savingOnly))

        assertEquals(listOf("e1"), result.savingEnvelopeIds)
        assertTrue(result.newGoals.isEmpty())
        assertNull(result.patchedGoals.firstOrNull())
    }
}
