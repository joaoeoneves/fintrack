package com.joaoeoneves.fintrack.ui.plan.goals

import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalDraftTest {
    private val today = LocalDate.of(2026, 10, 28)

    @Test
    fun newDraft_hasZeroCents_andIsInvalid() {
        val draft = draftOf(null, today)

        assertEquals(0L, draft.targetCents)
        assertEquals(0L, draft.initialCents)
        assertEquals(today, draft.startDate)
        assertFalse(draft.valid)
    }

    @Test
    fun zeroTarget_isInvalid_positiveTargetIsValid() {
        val named = draftOf(null, today).copy(name = "Trip")

        assertFalse(named.copy(targetCents = 0L).valid)
        assertTrue(named.copy(targetCents = 1L).valid)
    }

    @Test
    fun zeroInitial_isAccepted() {
        val draft = draftOf(null, today).copy(name = "Trip", targetCents = 5_000L, initialCents = 0L)

        assertTrue(draft.valid)
    }

    @Test
    fun editDraft_prefillsStoredCentsExactly() {
        val goal =
            SavingsGoal(
                id = "g1",
                name = "Car",
                targetCents = 1_234_567L,
                targetType = GoalTargetType.TOTAL,
                startDate = today,
                initialCents = 5L,
            )

        val draft = draftOf(goal, today)

        assertEquals(1_234_567L, draft.targetCents)
        assertEquals(5L, draft.initialCents)
        assertEquals(goal.targetCents, draft.toGoal(goal).targetCents)
        assertEquals(5L, draft.toGoal(goal).initialCents)
    }

    @Test
    fun annualGoal_dropsInitialCents_onSave() {
        val draft =
            draftOf(null, today).copy(
                name = "Save",
                targetCents = 100L,
                type = GoalTargetType.ANNUAL,
                initialCents = 50L,
            )

        assertEquals(0L, draft.toGoal(null).initialCents)
    }
}
