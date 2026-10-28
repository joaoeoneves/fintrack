package com.joaoeoneves.fintrack.ui.savings

import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SavingsPlanWriterTest {
    private val goalRepo = FakeGoalRepository()
    private val itemRepo = FakeRecurringRepository()
    private val log = WriteLog()
    private val writer = SavingsPlanWriter(log.goals(goalRepo), log.recurring(itemRepo))
    private val today = SAVINGS_TODAY
    private val draft =
        SavingsPlanDraft(name = " Holiday ", monthlyCents = 5_000L, dayText = "7", targetCents = 60_000L)

    private fun stored(
        goalId: String? = "g1",
        recurringId: String? = "r1",
        item: Boolean = true,
        original: SavingsPlanDraft? = null,
        itemValue: RecurringItem? = savingsItem(),
    ) = StoredPlan(
        goal = goalId?.let { savingsGoal(id = it, recurringId = recurringId) },
        item = itemValue.takeIf { item },
        original = original,
        nextOrder = 3,
    )

    private fun seed(
        goal: Boolean = true,
        item: RecurringItem? = savingsItem(),
    ) = runTest {
        if (goal) goalRepo.saveGoal(savingsGoal(recurringId = item?.id))
        item?.let { itemRepo.saveRecurring(it) }
        log.entries.clear()
    }

    @Test
    fun newPlanWithMonthly_savesItemFirstThenGoalPointingAtIt() =
        runTest {
            val id = writer.save(draft, stored(null, null, item = false), today).getOrThrow()

            assertEquals(listOf("item:save:", "goal:save:"), log.entries)
            val item = itemRepo.items.single()
            val goal = goalRepo.goals.single()
            assertEquals(id, goal.id)
            assertEquals(item.id, goal.recurringId)
            assertEquals("Holiday", goal.name)
            assertEquals(3, goal.order)
            assertEquals(RecurringFrequency.MONTHLY, item.frequency)
            assertEquals(5_000L, item.amountCents)
            assertEquals(7, item.dayOfMonth)
            assertEquals(today, item.startDate)
            assertTrue(item.active)
            assertTrue(item.autoPost)
            assertEquals(BuiltInCategories.INVESTMENTS.id, item.categoryId)
        }

    @Test
    fun newPlanWithoutMonthly_writesOnlyTheGoal() =
        runTest {
            writer.save(draft.copy(monthlyCents = 0L, targetCents = 0L), stored(null, null, item = false), today)

            assertEquals(listOf("goal:save:"), log.entries)
            assertTrue(itemRepo.items.isEmpty())
            assertNull(goalRepo.goals.single().recurringId)
            assertEquals(0L, goalRepo.goals.single().targetCents)
        }

    @Test
    fun newPlan_goalSaveFails_deletesTheCreatedItem() =
        runTest {
            goalRepo.nextSaveError = IllegalStateException("offline")

            val result = writer.save(draft, stored(null, null, item = false), today)

            assertTrue(result.isFailure)
            assertTrue(itemRepo.items.isEmpty())
            assertTrue(goalRepo.goals.isEmpty())
            assertEquals(listOf("item:save:", "item:delete:rec1"), log.entries.filterNot { it.startsWith("goal") })
        }

    @Test
    fun itemSaveFails_goalIsNotWritten() =
        runTest {
            itemRepo.nextSaveError = IllegalStateException("offline")

            val result = writer.save(draft, stored(null, null, item = false), today)

            assertTrue(result.isFailure)
            assertTrue(goalRepo.goals.isEmpty())
            assertTrue(log.entries.none { it.startsWith("goal") })
        }

    @Test
    fun editFailure_doesNotDeleteTheExistingItem() =
        runTest {
            seed()
            goalRepo.nextSaveError = IllegalStateException("offline")

            assertTrue(writer.save(draft, stored(), today).isFailure)

            assertEquals(listOf("r1"), itemRepo.items.map { it.id })
        }

    @Test
    fun edit_updatesTheOwnedItemInPlace() =
        runTest {
            seed()

            writer.save(draft.copy(autoPost = false), stored(), today).getOrThrow()

            assertEquals(listOf("item:save:r1", "goal:save:g1"), log.entries)
            val item = itemRepo.items.single()
            assertEquals(5_000L, item.amountCents)
            assertEquals(7, item.dayOfMonth)
            assertFalse(item.autoPost)
            assertEquals(LocalDate.of(2026, 1, 1), item.startDate)
            val goal = goalRepo.goals.single()
            assertEquals("r1", goal.recurringId)
            assertEquals(0, goal.order)
        }

    @Test
    fun clearedMonthly_deactivatesTheItemKeepingItsId() =
        runTest {
            seed()

            writer.save(draft.copy(monthlyCents = 0L), stored(), today).getOrThrow()

            val item = itemRepo.items.single()
            assertEquals("r1", item.id)
            assertFalse(item.active)
            assertEquals(10_000L, item.amountCents)
            assertEquals("r1", goalRepo.goals.single().recurringId)
        }

    @Test
    fun clearedMonthly_alreadyInactive_doesNotRewriteTheItem() =
        runTest {
            val inactive = savingsItem(active = false)
            seed(item = inactive)

            writer.save(draft.copy(monthlyCents = 0L), stored(itemValue = inactive), today).getOrThrow()

            assertEquals(listOf("goal:save:g1"), log.entries)
            assertEquals("r1", goalRepo.goals.single().recurringId)
        }

    @Test
    fun readdedMonthly_reactivatesWithStartDateToday() =
        runTest {
            val inactive = savingsItem(active = false, end = LocalDate.of(2026, 3, 1))
            seed(item = inactive)

            writer.save(draft, stored(itemValue = inactive), today).getOrThrow()

            val item = itemRepo.items.single()
            assertEquals("r1", item.id)
            assertTrue(item.active)
            assertEquals(today, item.startDate)
            assertNull(item.endDate)
            assertEquals(5_000L, item.amountCents)
        }

    @Test
    fun danglingRecurringId_createsANewItem() =
        runTest {
            seed(item = null)

            writer.save(draft, stored(recurringId = "gone", item = false), today).getOrThrow()

            val item = itemRepo.items.single()
            assertEquals("rec1", item.id)
            assertEquals("rec1", goalRepo.goals.single().recurringId)
        }

    @Test
    fun yearlyItem_untouched_keepsItsSchedule() =
        runTest {
            val yearly = savingsItem(amount = 120_000L, day = 20, frequency = RecurringFrequency.YEARLY, month = 3)
            seed(item = yearly)
            val original = SavingsPlanDraft(name = "Holiday", monthlyCents = 10_000L, dayText = "20")

            writer.save(original.copy(name = "Trip"), stored(itemValue = yearly, original = original), today)

            val item = itemRepo.items.single()
            assertEquals(RecurringFrequency.YEARLY, item.frequency)
            assertEquals(120_000L, item.amountCents)
            assertEquals(3, item.monthOfYear)
            assertEquals("Trip", item.name)
        }

    @Test
    fun yearlyItem_amountChanged_becomesMonthly() =
        runTest {
            val yearly = savingsItem(amount = 120_000L, day = 20, frequency = RecurringFrequency.YEARLY, month = 3)
            seed(item = yearly)
            val original = SavingsPlanDraft(name = "Holiday", monthlyCents = 10_000L, dayText = "20")

            writer.save(original.copy(monthlyCents = 20_000L), stored(itemValue = yearly, original = original), today)

            val item = itemRepo.items.single()
            assertEquals(RecurringFrequency.MONTHLY, item.frequency)
            assertEquals(20_000L, item.amountCents)
            assertNull(item.monthOfYear)
        }

    @Test
    fun yearlyItem_dayChanged_becomesMonthly() =
        runTest {
            val yearly = savingsItem(amount = 120_000L, day = 20, frequency = RecurringFrequency.YEARLY, month = 3)
            seed(item = yearly)
            val original = SavingsPlanDraft(name = "Holiday", monthlyCents = 10_000L, dayText = "20")

            writer.save(original.copy(dayText = "21"), stored(itemValue = yearly, original = original), today)

            assertEquals(RecurringFrequency.MONTHLY, itemRepo.items.single().frequency)
            assertEquals(21, itemRepo.items.single().dayOfMonth)
        }

    @Test
    fun annualPlan_dropsTotalOnlyFields() =
        runTest {
            val d = draft.copy(targetDate = LocalDate.of(2027, 1, 1), initialCents = 900L)

            writer.save(d, stored(null, null, item = false), today).getOrThrow()

            val goal = goalRepo.goals.single()
            assertNull(goal.targetDate)
            assertEquals(0L, goal.initialCents)
        }

    @Test
    fun totalPlan_keepsDateAndInitial() =
        runTest {
            val d = draft.copy(type = GoalTargetType.TOTAL, targetDate = LocalDate.of(2027, 1, 1), initialCents = 900L)

            writer.save(d, stored(null, null, item = false), today).getOrThrow()

            val goal = goalRepo.goals.single()
            assertEquals(LocalDate.of(2027, 1, 1), goal.targetDate)
            assertEquals(900L, goal.initialCents)
        }

    @Test
    fun delete_removesItemThenGoal() =
        runTest {
            seed()

            writer.delete(stored()).getOrThrow()

            assertEquals(listOf("item:delete:r1", "goal:delete:g1"), log.entries)
            assertTrue(itemRepo.items.isEmpty())
            assertTrue(goalRepo.goals.isEmpty())
        }

    @Test
    fun delete_withoutItem_removesOnlyTheGoal() =
        runTest {
            seed(item = null)

            writer.delete(stored(recurringId = null, item = false)).getOrThrow()

            assertEquals(listOf("goal:delete:g1"), log.entries)
        }

    @Test
    fun delete_goalFails_restoresTheItem() =
        runTest {
            seed()
            goalRepo.nextDeleteError = IllegalStateException("offline")

            val result = writer.delete(stored())

            assertTrue(result.isFailure)
            assertEquals(listOf("r1"), itemRepo.items.map { it.id })
            assertEquals(listOf("g1"), goalRepo.goals.map { it.id })
        }

    @Test
    fun delete_itemFails_leavesTheGoal() =
        runTest {
            seed()
            itemRepo.nextDeleteError = IllegalStateException("offline")

            assertTrue(writer.delete(stored()).isFailure)

            assertEquals(listOf("g1"), goalRepo.goals.map { it.id })
            assertTrue(log.entries.none { it.startsWith("goal") })
        }
}
