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
        recurring: List<RecurringItem> = emptyList(),
        hasExpense: Boolean = false,
    ) = startChecklistOf(profile, lines, recurring, hasExpense)

    @Test
    fun emptyState_hasNothingDone() {
        val result = checklist()

        assertTrue(result.done.isEmpty())
        assertEquals(0, result.doneCount)
        assertEquals(4, result.total)
        assertFalse(result.isComplete)
    }

    @Test
    fun stepOrder_isTheDisplayOrder() {
        assertEquals(
            listOf(StartStep.SALARY, StartStep.BILLS, StartStep.PAYDAY, StartStep.FIRST_EXPENSE),
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
    fun bills_doneByAnActiveExpenseItem() {
        assertEquals(setOf(StartStep.BILLS), checklist(recurring = listOf(recurringItem())).done)
    }

    @Test
    fun bills_ignoreInactiveAndIncomeItems() {
        val result =
            checklist(
                recurring =
                    listOf(
                        recurringItem(id = "off", active = false),
                        recurringItem(id = "pay", type = RecurringType.INCOME),
                    ),
            )

        assertFalse(StartStep.BILLS in result.done)
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
                recurring = listOf(recurringItem()),
                hasExpense = true,
            )

        assertEquals(StartStep.entries.toSet(), result.done)
        assertEquals(4, result.doneCount)
        assertTrue(result.isComplete)
    }

    @Test
    fun threeOfFour_isNotComplete() {
        val result = StartChecklist(StartStep.entries.toSet() - StartStep.PAYDAY)

        assertEquals(3, result.doneCount)
        assertFalse(result.isComplete)
    }
}
