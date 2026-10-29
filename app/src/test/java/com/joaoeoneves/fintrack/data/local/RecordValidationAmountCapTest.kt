package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertThrows
import org.junit.Test

class RecordValidationAmountCapTest {
    private val known = setOf("FOOD")
    private val date = "2026-03-10T12:00:00Z"
    private val over = MAX_AMOUNT_CENTS + 1

    private fun invalid(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    @Test
    fun expense_cap() {
        validateExpense(LocalExpense("e", "n", MAX_AMOUNT_CENTS, "FOOD", date), known)
        invalid { validateExpense(LocalExpense("e", "n", over, "FOOD", date), known) }
        invalid { validateExpense(LocalExpense("e", "n", Long.MAX_VALUE, "FOOD", date), known) }
    }

    @Test
    fun income_cap() {
        validateIncome(LocalIncome("i", "s", MAX_AMOUNT_CENTS, date))
        invalid { validateIncome(LocalIncome("i", "s", over, date)) }
    }

    @Test
    fun budget_cap() {
        validateBudget("FOOD", MAX_AMOUNT_CENTS, known)
        invalid { validateBudget("FOOD", over, known) }
    }

    @Test
    fun plannedIncome_cap() {
        validatePlannedIncome(LocalPlannedIncome("p", "Salary", MAX_AMOUNT_CENTS, 0))
        invalid { validatePlannedIncome(LocalPlannedIncome("p", "Salary", over, 0)) }
        validatePlannedIncome(LocalPlannedIncome("p", "Salary", 0, 0))
        invalid { validatePlannedIncome(LocalPlannedIncome("p", "Salary", -1, 0)) }
    }

    @Test
    fun recurring_cap() {
        val ok = recurringItem(amountCents = MAX_AMOUNT_CENTS).toLocal()
        validateRecurring(ok, known)
        invalid { validateRecurring(ok.copy(amountCents = over), known) }
    }
}
