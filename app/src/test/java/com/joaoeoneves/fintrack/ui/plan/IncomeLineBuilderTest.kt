package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeLineBuilderTest {
    private val manual = PlannedIncome("m", "Side", 5_000L, 0)
    private val managed = PlannedIncome("s", "Salary", 100_000L, 0, managedKey = MANAGED_NET_SALARY)

    @Test
    fun earlierFlag_isKeptWithAFixedDay() {
        val line =
            incomeLineOf(
                null,
                emptyList(),
                "Side",
                1L,
                IncomeSchedule(
                    payDay = 31,
                    payDayEarlierIfClosed = true,
                ),
            )

        assertEquals(31, line.payDay)
        assertTrue(line.payDayEarlierIfClosed)
    }

    @Test
    fun earlierFlag_isClearedWhenPayDayIsNull() {
        val line =
            incomeLineOf(
                null,
                emptyList(),
                "Side",
                1L,
                IncomeSchedule(
                    payDay = null,
                    payDayEarlierIfClosed = true,
                ),
            )

        assertFalse(line.payDayEarlierIfClosed)
    }

    @Test
    fun managedLine_takesPayDayAndFlag_butKeepsItsAmount() {
        val edited =
            incomeLineOf(
                managed,
                listOf(managed),
                "Pay",
                1L,
                IncomeSchedule(
                    payDay = 25,
                    payDayEarlierIfClosed = true,
                ),
            )

        assertEquals(100_000L, edited.amountCents)
        assertEquals(25, edited.payDay)
        assertTrue(edited.payDayEarlierIfClosed)
        val cleared =
            incomeLineOf(
                edited,
                listOf(edited),
                "Pay",
                1L,
                IncomeSchedule(
                    payDay = null,
                    payDayEarlierIfClosed = true,
                ),
            )
        assertFalse(cleared.payDayEarlierIfClosed)
    }

    @Test
    fun paysDifferently_countsTheFlag() {
        val fixed = manual.copy(payDay = 10)

        assertTrue(fixed.paysDifferently(fixed.copy(payDayEarlierIfClosed = true)))
        assertTrue(fixed.paysDifferently(fixed.copy(payDay = 11)))
        assertTrue(fixed.paysDifferently(fixed.copy(autoRecord = true)))
        assertFalse(fixed.paysDifferently(fixed.copy(label = "x", amountCents = 1L)))
    }
}
