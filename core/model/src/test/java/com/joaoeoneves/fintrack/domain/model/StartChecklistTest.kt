package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [startChecklistOf]: which "Get started" steps count as done. */
class StartChecklistTest {
    private fun line(
        id: String = "l1",
        autoRecord: Boolean = false,
    ) = PlannedIncome(id, "Line $id", 100_000L, 0, autoRecord = autoRecord)

    @Suppress("LongParameterList") // Mirrors startChecklistOf so each test sets only what it cares about.
    private fun checklist(
        profile: SalaryProfile? = null,
        lines: List<PlannedIncome> = emptyList(),
        envelopes: List<Envelope> = emptyList(),
        recurring: List<RecurringItem> = emptyList(),
        goals: List<SavingsGoal> = emptyList(),
        hasExpense: Boolean = false,
    ) = startChecklistOf(profile, lines, envelopes, recurring, goals, hasExpense)

    @Test
    fun emptyState_hasNothingDone() {
        val result = checklist()

        assertTrue(result.done.isEmpty())
        assertEquals(0, result.doneCount)
        assertEquals(6, result.total)
        assertFalse(result.isComplete)
    }

    @Test
    fun stepOrder_isTheDisplayOrder() {
        assertEquals(
            listOf(
                StartStep.SALARY,
                StartStep.ENVELOPES,
                StartStep.BILLS,
                StartStep.SAVINGS_PLAN,
                StartStep.PAYDAY,
                StartStep.FIRST_EXPENSE,
            ),
            StartStep.entries,
        )
    }

    @Test
    fun salary_doneByProfileAlone() {
        assertEquals(setOf(StartStep.SALARY), checklist(profile = SalaryProfile(baseGrossCents = 100_000L)).done)
    }

    @Test
    fun salary_doneByIncomeLinesOnly() {
        assertEquals(setOf(StartStep.SALARY), checklist(lines = listOf(line())).done)
    }

    @Test
    fun envelopes_doneByAnyEnvelope() {
        assertEquals(setOf(StartStep.ENVELOPES), checklist(envelopes = listOf(Envelope("e1", "Home", 0))).done)
    }

    @Test
    fun bills_doneByAnActiveExpenseItem() {
        assertEquals(setOf(StartStep.BILLS), checklist(recurring = listOf(recurringItem())).done)
    }

    @Test
    fun bills_ignoreInactiveIncomeAndPlanOwnedItems() {
        val result =
            checklist(
                recurring =
                    listOf(
                        recurringItem(id = "off", active = false),
                        recurringItem(id = "pay", type = RecurringType.INCOME),
                        recurringItem(id = "plan"),
                    ),
                goals = listOf(savingsGoal(id = "g", recurringId = "plan", archived = true)),
            )

        assertFalse(StartStep.BILLS in result.done)
    }

    @Test
    fun bills_planOwnedItemIgnoredEvenWhenGoalIsArchived_butAnotherBillCounts() {
        val result =
            checklist(
                recurring = listOf(recurringItem(id = "plan"), recurringItem(id = "rent")),
                goals = listOf(savingsGoal(recurringId = "plan")),
            )

        assertTrue(StartStep.BILLS in result.done)
    }

    @Test
    fun savingsPlan_ignoresArchivedGoals() {
        assertTrue(StartStep.SAVINGS_PLAN !in checklist(goals = listOf(savingsGoal(archived = true))).done)
        assertTrue(
            StartStep.SAVINGS_PLAN in
                checklist(goals = listOf(savingsGoal(id = "a", archived = true), savingsGoal(id = "b"))).done,
        )
    }

    @Test
    fun payday_requiresAutoRecord() {
        val manual = checklist(lines = listOf(line()))
        val auto = checklist(lines = listOf(line("a"), line("b", autoRecord = true)))

        assertFalse(StartStep.PAYDAY in manual.done)
        assertTrue(StartStep.PAYDAY in auto.done)
    }

    @Test
    fun firstExpense_followsHasExpense() {
        assertEquals(setOf(StartStep.FIRST_EXPENSE), checklist(hasExpense = true).done)
    }

    @Test
    fun everyStepDone_isComplete() {
        val result =
            checklist(
                profile = SalaryProfile(baseGrossCents = 1L),
                lines = listOf(line(autoRecord = true)),
                envelopes = listOf(Envelope("e1", "Home", 0)),
                recurring = listOf(recurringItem()),
                goals = listOf(savingsGoal()),
                hasExpense = true,
            )

        assertEquals(StartStep.entries.toSet(), result.done)
        assertEquals(6, result.doneCount)
        assertTrue(result.isComplete)
    }

    @Test
    fun fiveOfSix_isNotComplete() {
        val result = StartChecklist(StartStep.entries.toSet() - StartStep.PAYDAY)

        assertEquals(5, result.doneCount)
        assertFalse(result.isComplete)
    }
}
