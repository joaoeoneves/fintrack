package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LooksLikeSalaryTest {
    private val net = PlannedIncome("n", "Net salary", 100_000, 0, managedKey = MANAGED_NET_SALARY)

    private fun manual(
        label: String = "Side gig",
        cents: Long = 30_000,
        frequency: IncomeFrequency = IncomeFrequency.MONTHLY,
    ) = PlannedIncome(
        "m",
        label,
        cents,
        1,
        frequency = frequency,
        monthOfYear = 3.takeIf { frequency == IncomeFrequency.YEARLY },
    )

    @Test
    fun keywords_matchIgnoringCaseAndAccents() {
        for (label in listOf("Salário", "ORDENADO", "Vencimento Abril", "my Salary", "WAGE", "Paycheck")) {
            assertTrue(label, manual(label).looksLikeSalary(listOf(net)))
        }
    }

    @Test
    fun amountWithinTenPercent_matches_justOutsideDoesNot() {
        assertTrue(manual(cents = 90_000).looksLikeSalary(listOf(net)))
        assertTrue(manual(cents = 110_000).looksLikeSalary(listOf(net)))
        assertFalse(manual(cents = 89_999).looksLikeSalary(listOf(net)))
        assertFalse(manual(cents = 110_001).looksLikeSalary(listOf(net)))
    }

    @Test
    fun unrelatedLine_doesNotMatch() {
        assertFalse(manual().looksLikeSalary(listOf(net)))
    }

    @Test
    fun managedLines_neverMatch() {
        val managed = manual("Salary").copy(managedKey = MANAGED_MEAL)

        assertFalse(managed.looksLikeSalary(listOf(net)))
        assertFalse(net.looksLikeSalary(listOf(net)))
    }

    @Test
    fun nonMonthlyLines_neverMatch() {
        assertFalse(manual("Salary", 100_000, IncomeFrequency.YEARLY).looksLikeSalary(listOf(net)))
    }

    @Test
    fun noSalaryProfile_isFalse() {
        assertFalse(manual("Salary", 100_000).looksLikeSalary(emptyList()))
        assertFalse(manual("Salary", 100_000).looksLikeSalary(listOf(manual("Other"))))
    }
}
