package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset

/** Which month an income belongs to: a payday income by its id, any other by its date. */
class IncomeOwnershipTest {
    private val utc = ZoneOffset.UTC
    private val dec = YearMonth.of(2026, 12)
    private val jan = YearMonth.of(2027, 1)

    private fun at(
        y: Int,
        m: Int,
        d: Int,
    ): Instant = LocalDate.of(y, m, d).atTime(LocalTime.NOON).toInstant(utc)

    private fun income(
        id: String,
        date: Instant,
    ) = Income(id, "src", 1_000L, date, null)

    private val decPayOnJan2 = income("pay_p1_202612", at(2027, 1, 2))
    private val plainJan2 = income("manual", at(2027, 1, 2))

    @Test
    fun ownerMonth_payIdWinsOverDate_plainIncomeUsesItsDate() {
        assertEquals(dec, decPayOnJan2.ownerMonth(utc))
        assertEquals(jan, plainJan2.ownerMonth(utc))
    }

    @Test
    fun ownerMonth_malformedPayId_fallsBackToDate() {
        assertEquals(jan, income("pay_p1_notamonth", at(2027, 1, 2)).ownerMonth(utc))
    }

    @Test
    fun filterOwnedInMonth_decemberPayDatedInJanuary_countsInDecemberOnly() {
        val all = listOf(decPayOnJan2, plainJan2)
        assertEquals(listOf(decPayOnJan2), filterOwnedInMonth(all, dec, utc))
        assertEquals(listOf(plainJan2), filterOwnedInMonth(all, jan, utc))
    }

    @Test
    fun filterOwnedIn_reattributesByIdOutsideItsDateMonth() {
        val all = listOf(decPayOnJan2, plainJan2)
        val december = InstantRange(at(2026, 12, 1), at(2027, 1, 1))
        val january = InstantRange(at(2027, 1, 1), at(2027, 2, 1))
        assertEquals(listOf(decPayOnJan2), filterOwnedIn(all, december, utc))
        assertEquals(listOf(plainJan2), filterOwnedIn(all, january, utc))
    }

    @Test
    fun filterOwnedIn_incomeInItsOwnMonth_usesItsDateAtTheRangeEdges() {
        val inOwn = income("pay_p1_202612", at(2026, 12, 31))
        val range = InstantRange(at(2026, 12, 31), at(2027, 1, 1))
        assertEquals(listOf(inOwn), filterOwnedIn(listOf(inOwn), range, utc))
        val after = InstantRange(at(2027, 1, 1), at(2027, 2, 1))
        assertEquals(emptyList<Income>(), filterOwnedIn(listOf(inOwn), after, utc))
    }

    @Test
    fun canReceiveIn_onlyOnOrAfterThePayDate() {
        val nov = YearMonth.of(2026, 11)
        val income = IncomeMonth.of(emptyList())
        val line = PlannedIncome("p1", "Salary", 100_000L, 0, payDay = 20)
        val row = incomeRowsIn(listOf(line), nov, income).single()
        assertFalse(row.canReceiveIn(nov, income, LocalDate.of(2026, 11, 19)))
        assertTrue(row.canReceiveIn(nov, income, LocalDate.of(2026, 11, 20)))
        assertTrue(row.canReceiveIn(nov, income, LocalDate.of(2026, 12, 1)))
    }
}
