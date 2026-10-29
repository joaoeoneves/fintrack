package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** [incomeRowsIn] drops a managed line that pays 0 (ended by the profile) but keeps everything else. */
class IncomeRowsInManagedTest {
    private val feb = YearMonth.of(2026, 2)
    private val mar = YearMonth.of(2026, 3)
    private val income = IncomeMonth.of(emptyList())

    private val endedMeal =
        PlannedIncome(
            "meal",
            "Meal",
            0L,
            0,
            managedKey = MANAGED_MEAL,
            startMonth = YearMonth.of(2026, 1),
            versions = listOf(AmountVersion(YearMonth.of(2026, 1), 900L), AmountVersion(mar, 0L)),
        )

    @Test
    fun managedLineWithZeroVersion_hasNoRowThere_butRowBefore() {
        assertTrue(incomeRowsIn(listOf(endedMeal), mar, income).isEmpty())
        assertEquals(listOf(SalaryComponent.MEAL), incomeRowsIn(listOf(endedMeal), feb, income).map { it.component })
    }

    @Test
    fun positiveManagedLine_keepsItsRow_evenInAMonthItDoesNotPay() {
        val holiday =
            PlannedIncome(
                "h",
                "Holiday",
                50_000L,
                0,
                managedKey = MANAGED_HOLIDAY,
                frequency = IncomeFrequency.YEARLY,
                monthOfYear = 6,
            )
        val row = incomeRowsIn(listOf(holiday), mar, income).single()
        assertEquals(0L, row.cents)
        assertEquals(SalaryComponent.HOLIDAY, row.component)
    }

    @Test
    fun manualLineOfZero_staysSoItCanBeEdited() {
        val manual = PlannedIncome("m", "Side gig", 0L, 0)
        val row = incomeRowsIn(listOf(manual), mar, income).single()
        assertEquals(listOf(manual), row.lines)
        assertEquals(0L, row.cents)
    }
}
