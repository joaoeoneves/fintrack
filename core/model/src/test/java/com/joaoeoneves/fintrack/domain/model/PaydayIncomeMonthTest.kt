package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** A payday income dated before the month's end still settles its own month's row, and only that one. */
class PaydayIncomeMonthTest {
    private val utc = ZoneOffset.UTC
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val line = PlannedIncome("p1", "Salary", 100_000L, 0)
    private val incomeMonth = IncomeMonth.of(emptyList())

    private fun rowIn(month: YearMonth) = incomeRowsIn(listOf(line), month, incomeMonth).first()

    private val payday = line.paydayIncome(oct, 21, LocalDate.of(2026, 10, 29), utc)!!

    @Test
    fun incomeDatedThe29th_makesOctoberReceived_onThatDate() {
        assertEquals("pay_p1_202610", payday.id)
        assertEquals(listOf(payday), rowIn(oct).receivedIncomes(oct, incomeMonth, listOf(payday)))
        assertEquals(LocalDate.of(2026, 10, 29), rowIn(oct).receivedOn(oct, incomeMonth, listOf(payday), utc))
    }

    @Test
    fun novemberRow_isStillExpected() {
        assertNull(rowIn(nov).receivedIncomes(nov, incomeMonth, listOf(payday)))
        assertNull(rowIn(nov).receivedOn(nov, incomeMonth, listOf(payday), utc))
    }
}
