package com.joaoeoneves.fintrack.ui.savings

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.MAX_GOAL_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SavingsPlanDraftTest {
    private val ok = SavingsPlanDraft(name = "Holiday", monthlyCents = 5_000L)

    @Test
    fun defaults_areAnnualInvestmentsDayOneAutoPost() {
        val d = SavingsPlanDraft()
        assertEquals(GoalTargetType.ANNUAL, d.type)
        assertEquals(BuiltInCategories.INVESTMENTS.id, d.categoryId)
        assertEquals(1, d.day)
        assertTrue(d.autoPost)
        assertFalse(d.hasMonthly)
    }

    @Test
    fun blankName_isInvalid() {
        assertFalse(ok.copy(name = "  ").valid)
        assertTrue(ok.valid)
    }

    @Test
    fun nameLength_boundary() {
        assertTrue(ok.copy(name = "a".repeat(MAX_GOAL_NAME_LENGTH)).nameValid)
        assertFalse(ok.copy(name = "a".repeat(MAX_GOAL_NAME_LENGTH + 1)).nameValid)
    }

    @Test
    fun targetZero_isAllowed() {
        assertTrue(ok.copy(targetCents = 0L).valid)
    }

    @Test
    fun day_boundsOnlyMatterWithMonthly() {
        assertTrue(ok.copy(dayText = "1").dayValid)
        assertTrue(ok.copy(dayText = "31").dayValid)
        assertFalse(ok.copy(dayText = "0").dayValid)
        assertFalse(ok.copy(dayText = "32").dayValid)
        assertFalse(ok.copy(dayText = "").dayValid)
        assertTrue(ok.copy(monthlyCents = 0L, dayText = "").valid)
    }

    @Test
    fun category_requiredOnlyWithMonthly() {
        assertFalse(ok.copy(categoryId = null).valid)
        assertTrue(ok.copy(monthlyCents = 0L, categoryId = null).valid)
    }

    @Test
    fun targetDate_beforeStart_isInvalidForTotalOnly() {
        val start = LocalDate.of(2026, 5, 1)
        val bad = ok.copy(type = GoalTargetType.TOTAL, startDate = start, targetDate = start.minusDays(1))
        assertFalse(bad.valid)
        assertTrue(bad.copy(targetDate = start).valid)
        assertTrue(bad.copy(type = GoalTargetType.ANNUAL).valid)
    }

    @Test
    fun draftOf_newPlan_startsToday() {
        val d = draftOf(null, null, SAVINGS_TODAY)
        assertEquals(SAVINGS_TODAY, d.startDate)
        assertEquals("", d.name)
        assertEquals(0L, d.monthlyCents)
    }

    @Test
    fun draftOf_roundTripsGoalAndItem() {
        val goal =
            savingsGoal(
                target = 90_000L,
                type = GoalTargetType.TOTAL,
                targetDate = LocalDate.of(2027, 6, 1),
                initial = 2_500L,
                recurringId = "r1",
            )
        val d = draftOf(goal, savingsItem(amount = 7_000L, day = 20, autoPost = false), SAVINGS_TODAY)
        assertEquals("Holiday", d.name)
        assertEquals(7_000L, d.monthlyCents)
        assertEquals("20", d.dayText)
        assertFalse(d.autoPost)
        assertEquals(90_000L, d.targetCents)
        assertEquals(GoalTargetType.TOTAL, d.type)
        assertEquals(LocalDate.of(2027, 6, 1), d.targetDate)
        assertEquals(2_500L, d.initialCents)
        assertEquals(goal.startDate, d.startDate)
    }

    @Test
    fun draftOf_inactiveOrEndedItem_hasNoMonthlyButKeepsDay() {
        val inactive = draftOf(savingsGoal(), savingsItem(active = false, day = 9), SAVINGS_TODAY)
        assertEquals(0L, inactive.monthlyCents)
        assertEquals("9", inactive.dayText)
        val ended = draftOf(savingsGoal(), savingsItem(end = SAVINGS_TODAY.minusDays(1)), SAVINGS_TODAY)
        assertEquals(0L, ended.monthlyCents)
    }

    @Test
    fun draftOf_yearlyItem_showsMonthlyShare() {
        val item = savingsItem(amount = 120_000L, frequency = RecurringFrequency.YEARLY, month = 3)
        assertEquals(10_000L, draftOf(savingsGoal(), item, SAVINGS_TODAY).monthlyCents)
    }

    @Test
    fun draftOf_noItem_hasNullDateDefaults() {
        assertNull(draftOf(savingsGoal(), null, SAVINGS_TODAY).targetDate)
    }
}
