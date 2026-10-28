package com.joaoeoneves.fintrack.data.local

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_GOAL_LINKED_RECURRING
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The goal record: validation mirroring `firestore.rules` plus the date order, and the domain mapping. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GoalRecordsTest {
    private val valid =
        LocalSavingsGoal(id = "g1", name = "Holiday", targetCents = 120_000, startDate = "2026-01-01")

    private fun invalid(goal: LocalSavingsGoal) {
        assertThrows(IllegalArgumentException::class.java) { validateGoal(goal) }
    }

    @Test
    fun valid_passes() {
        validateGoal(valid)
        validateGoal(valid.copy(targetType = "TOTAL", targetDate = "2026-01-01", initialCents = 0))
    }

    @Test
    fun blankId_rejected() = invalid(valid.copy(id = " "))

    @Test
    fun nameBoundary_countsCodePoints() {
        validateGoal(valid.copy(name = "a".repeat(50)))
        validateGoal(valid.copy(name = "😀".repeat(50)))
        invalid(valid.copy(name = "a".repeat(51)))
        invalid(valid.copy(name = "😀".repeat(51)))
        invalid(valid.copy(name = ""))
    }

    @Test
    fun targetBoundary() {
        validateGoal(valid.copy(targetCents = 1))
        validateGoal(valid.copy(targetCents = MAX_AMOUNT_CENTS))
        validateGoal(valid.copy(targetCents = 0)) // no target
        invalid(valid.copy(targetCents = -1))
        invalid(valid.copy(targetCents = MAX_AMOUNT_CENTS + 1))
    }

    @Test
    fun initialBoundary() {
        validateGoal(valid.copy(initialCents = 0))
        validateGoal(valid.copy(initialCents = MAX_AMOUNT_CENTS))
        invalid(valid.copy(initialCents = -1))
        invalid(valid.copy(initialCents = MAX_AMOUNT_CENTS + 1))
    }

    @Test
    fun unknownType_rejected() = invalid(valid.copy(targetType = "WEEKLY"))

    @Test
    fun badDates_rejected() {
        invalid(valid.copy(startDate = "not-a-date"))
        invalid(valid.copy(startDate = "2026-02-30"))
        invalid(valid.copy(targetDate = "2026/12/31"))
    }

    @Test
    fun targetDate_beforeStart_rejected_sameDayOrLaterAccepted() {
        invalid(valid.copy(targetDate = "2025-12-31"))
        validateGoal(valid.copy(targetDate = "2026-01-01"))
        validateGoal(valid.copy(targetDate = "2030-01-01"))
    }

    @Test
    fun linkedRecurringBoundary() {
        validateGoal(valid.copy(linkedRecurringIds = List(MAX_GOAL_LINKED_RECURRING) { "r$it" }))
        invalid(valid.copy(linkedRecurringIds = List(MAX_GOAL_LINKED_RECURRING + 1) { "r$it" }))
    }

    @Test
    fun linkedCategory_shapeChecked() {
        validateGoal(valid.copy(linkedCategoryId = "custom_1"))
        invalid(valid.copy(linkedCategoryId = ""))
        invalid(valid.copy(linkedCategoryId = "bad id!"))
    }

    @Test
    fun recurringId_shapeValidated_nullAllowed() {
        validateGoal(valid.copy(recurringId = "r_1"))
        validateGoal(valid.copy(recurringId = null))
        invalid(valid.copy(recurringId = ""))
        invalid(valid.copy(recurringId = "bad id!"))
    }

    @Test
    fun recurringId_roundTripsThroughDomain_andNoTargetPlanMaps() {
        val start = LocalDate.of(2026, 1, 1)
        val plan = SavingsGoal("plan_r1", "Fund", 0L, GoalTargetType.ANNUAL, start, recurringId = "r1")

        assertEquals("r1", plan.toLocal().recurringId)
        assertEquals(plan, plan.toLocal().toDomainOrNull())
    }

    @Test
    fun domainRoundTrip_keepsEveryField() {
        val goal =
            SavingsGoal(
                id = "g1",
                name = "Holiday",
                targetCents = 120_000,
                targetType = GoalTargetType.TOTAL,
                startDate = LocalDate.of(2026, 1, 1),
                targetDate = LocalDate.of(2027, 6, 30),
                initialCents = 5_000,
                linkedRecurringIds = setOf("r2", "r1"),
                linkedCategoryId = CategoryId("custom_1"),
                archived = true,
                order = 3,
            )

        val local = goal.toLocal()

        assertEquals(listOf("r1", "r2"), local.linkedRecurringIds)
        assertEquals(goal, local.toDomainOrNull())
    }

    @Test
    fun unknownType_orBadDate_isDroppedOnMapping() {
        assertNull(valid.copy(targetType = "WEEKLY").toDomainOrNull())
        assertNull(valid.copy(startDate = "garbage").toDomainOrNull())
        assertNull(valid.copy(targetDate = "garbage").toDomainOrNull())
    }
}
