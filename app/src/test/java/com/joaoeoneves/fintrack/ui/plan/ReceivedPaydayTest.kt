package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.model.IncomeRow
import com.joaoeoneves.fintrack.domain.model.MANAGED_MEAL
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReceivedPaydayTest {
    private val salary =
        IncomeRow(
            SalaryComponent.SALARY,
            listOf(
                PlannedIncome(
                    "s",
                    "Salary",
                    1L,
                    0,
                    managedKey = MANAGED_NET_SALARY,
                ),
            ),
            100L,
        )
    private val meal =
        IncomeRow(
            SalaryComponent.MEAL,
            listOf(
                PlannedIncome(
                    "m",
                    "Meal",
                    1L,
                    1,
                    managedKey = MANAGED_MEAL,
                ),
            ),
            50L,
        )
    private val manual = IncomeRow(null, listOf(PlannedIncome("x", "Gig", 1L, 2)), 20L)
    private val idle = IncomeRow(SalaryComponent.EXTRA, listOf(PlannedIncome("i", "Idle", 0L, 3)), 0L)
    private val d29 = LocalDate.of(2026, 10, 29)
    private val d30 = LocalDate.of(2026, 10, 30)

    @Test
    fun withASalaryRow_theSalaryDayCounts_evenIfOthersAreNot() {
        assertEquals(d29, receivedPaydayOf(listOf(salary, meal), mapOf("s" to d29)))
        assertNull(receivedPaydayOf(listOf(salary, meal), mapOf("m" to d30)))
    }

    @Test
    fun withoutASalary_allMustBeReceived_latestDay() {
        assertEquals(d30, receivedPaydayOf(listOf(meal, manual, idle), mapOf("m" to d29, "x" to d30)))
    }

    @Test
    fun partial_isNotReceived() {
        assertNull(receivedPaydayOf(listOf(meal, manual), mapOf("m" to d29)))
        assertNull(receivedPaydayOf(listOf(idle), emptyMap()))
        assertNull(receivedPaydayOf(emptyList(), emptyMap()))
    }
}
