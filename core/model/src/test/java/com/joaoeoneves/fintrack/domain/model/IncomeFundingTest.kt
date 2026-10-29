package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** "The salary pays for the next month": the rule, the attribution of amounts, dates and recorded incomes. */
class IncomeFundingTest {
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)
    private val zone = ZoneOffset.UTC

    private fun salary(
        id: String = "sal",
        payDay: Int? = null,
        cents: Long = 100_000L,
        frequency: IncomeFrequency = IncomeFrequency.MONTHLY,
    ) = PlannedIncome(id, id, cents, 0, frequency = frequency, managedKey = "salary", payDay = payDay)

    private val manual = PlannedIncome("man", "Rent income", 20_000L, 1)
    private val on = IncomeFunding(true) { 20 }
    private val off = IncomeFunding(false) { 20 }

    @Test
    fun rule_salaryLineWithNoPayDay_paysForNextMonth() {
        assertTrue(salary(payDay = null).paysForNextMonth)
    }

    @Test
    fun rule_payDayBoundary_is26() {
        assertFalse(salary(payDay = 25).paysForNextMonth)
        assertTrue(salary(payDay = 26).paysForNextMonth)
        assertTrue(salary(payDay = 31).paysForNextMonth)
        assertFalse(salary(payDay = 1).paysForNextMonth)
    }

    @Test
    fun rule_manualLinesAndExtrasNeverShift() {
        assertFalse(manual.paysForNextMonth)
        assertFalse(manual.copy(payDay = 30).paysForNextMonth)
        assertFalse(salary(frequency = IncomeFrequency.YEARLY).paysForNextMonth)
        assertFalse(salary(frequency = IncomeFrequency.ONCE).paysForNextMonth)
    }

    @Test
    fun payMonthOf_shiftsOnlyShiftingLines_whenOn() {
        assertEquals(sep, on.payMonthOf(salary(), oct))
        assertEquals(oct, on.payMonthOf(manual, oct))
        assertEquals(oct, on.payMonthOf(salary(payDay = 10), oct))
        assertEquals(oct, off.payMonthOf(salary(), oct))
    }

    @Test
    fun payMonthOf_crossesTheYearBoundary() {
        assertEquals(YearMonth.of(2025, 12), on.payMonthOf(salary(), YearMonth.of(2026, 1)))
    }

    @Test
    fun amountIn_usesThePayMonthsWorkingDays() {
        val funding = IncomeFunding(true) { if (it == sep) 22 else 19 }
        val perDay = salary(cents = 5_000L).copy(basis = IncomeBasis.PER_WORKING_DAY)

        assertEquals(5_000L * 22, funding.amountIn(perDay, oct))
        assertEquals(5_000L * 19, IncomeFunding(false) { 19 }.amountIn(perDay, oct))
        assertEquals(20_000L, funding.amountIn(manual, oct))
    }

    @Test
    fun amountIn_respectsSkipMonthsOfThePayMonth() {
        val skipSeptember = salary().copy(skipMonths = setOf(9))

        assertEquals(0L, on.amountIn(skipSeptember, oct))
        assertEquals(100_000L, on.amountIn(skipSeptember, sep))
    }

    @Test
    fun payDateOf_isInThePayMonth() {
        assertEquals(LocalDate.of(2026, 9, 30), on.payDateOf(salary(), oct))
        assertEquals(LocalDate.of(2026, 10, 30), off.payDateOf(salary(), oct))
        assertEquals(LocalDate.of(2026, 9, 28), on.payDateOf(salary(payDay = 28), oct))
    }

    @Test
    fun unshifted_usesGivenWorkingDaysOnlyForThatMonth() {
        val funding = IncomeFunding.unshifted(oct, 21)

        assertFalse(funding.shift)
        assertEquals(21, funding.workingDays(oct))
        assertEquals(0, funding.workingDays(sep))
    }

    @Test
    fun of_readsWorkingDaysFromSettings() {
        val settings = listOf(MonthSettings(oct, workingDaysOverride = 18, daysOff = 0))
        val funding = IncomeFunding.of(true, settings)

        assertTrue(funding.shift)
        assertEquals(18, funding.workingDays(oct))
        assertEquals(effectiveWorkingDays(sep, null, 0), funding.workingDays(sep))
    }

    @Test
    fun salaryShiftEnabled_nullPreferenceFollowsTheSalaryProfile() {
        assertTrue(salaryShiftEnabled(null, listOf(salary(), manual)))
        assertFalse(salaryShiftEnabled(null, listOf(manual)))
        assertFalse(salaryShiftEnabled(null, emptyList()))
    }

    @Test
    fun salaryShiftEnabled_explicitPreferenceWins() {
        assertFalse(salaryShiftEnabled(false, listOf(salary())))
        assertTrue(salaryShiftEnabled(true, emptyList()))
    }

    @Test
    fun incomeRowsIn_septemberSalaryFundsOctober_datedThirtiethSeptember() {
        val rows = incomeRowsIn(listOf(salary()), oct, on)
        val row = rows.single()

        assertEquals(100_000L, row.cents)
        assertEquals(LocalDate.of(2026, 9, 30), row.payDateIn(oct, on))
        assertEquals(setOf(sep), row.payingLinesByPayMonth(oct, on).keys)
        assertEquals("pay_sal_202609", paydayIncomeId("sal", sep))
    }

    @Test
    fun incomeRowsIn_manualLineAndExtraStayInTheirOwnMonth() {
        val extra = manual.copy(id = "x", frequency = IncomeFrequency.YEARLY, monthOfYear = 10)
        val rows = incomeRowsIn(listOf(salary(), manual, extra), oct, on)

        assertEquals(100_000L + 20_000L + 20_000L, rows.sumOf { it.cents })
        val septemberRows = incomeRowsIn(listOf(manual, extra), sep, on)
        assertEquals(20_000L, septemberRows.sumOf { it.cents })
    }

    @Test
    fun payDateIn_row_withoutShiftIsTheOwnMonth() {
        val row = incomeRowsIn(listOf(salary()), oct, off).single()

        assertEquals(LocalDate.of(2026, 10, 30), row.payDateIn(oct, off))
        assertEquals(LocalDate.of(2026, 10, 30), commonPayDateIn(listOf(row), oct, off))
    }

    @Test
    fun commonPayDateIn_isNullWhenRowsPayOnDifferentDays() {
        val rows = incomeRowsIn(listOf(salary(), manual.copy(payDay = 5)), oct, on)

        assertNull(commonPayDateIn(rows, oct, on))
    }

    @Test
    fun receivedIncomes_lookedUpInThePayMonth() {
        val row = incomeRowsIn(listOf(salary()), oct, on).single()
        val recorded = testIncome("pay_sal_202609", 100_000L, Instant.parse("2026-09-30T12:00:00Z"))

        assertEquals(listOf(recorded), row.receivedIncomes(oct, on, listOf(recorded)))
        assertEquals(LocalDate.of(2026, 9, 30), row.receivedOn(oct, on, listOf(recorded), zone))
        assertNull(row.receivedIncomes(oct, on, listOf(recorded.copy(id = "pay_sal_202610"))))
    }

    @Test
    fun parsePaydayIncomeId_roundTripsAndRejectsOthers() {
        assertEquals("sal" to sep, parsePaydayIncomeId("pay_sal_202609"))
        assertEquals("my_line" to oct, parsePaydayIncomeId(paydayIncomeId("my_line", oct)))
        assertNull(parsePaydayIncomeId("rec_sal_20260930"))
        assertNull(parsePaydayIncomeId("pay_sal_2026xx"))
        assertNull(parsePaydayIncomeId("pay__202609"))
        assertNull(parsePaydayIncomeId("pay_202609"))
    }

    @Test
    fun fundingMonthOf_shiftedPaydayIncomeFundsTheNextMonth() {
        val income = testIncome("pay_sal_202609", 100_000L, Instant.parse("2026-09-30T12:00:00Z"))

        assertEquals(oct, fundingMonthOf(income, listOf(salary()), on, zone))
        assertEquals(sep, fundingMonthOf(income, listOf(salary()), off, zone))
    }

    @Test
    fun fundingMonthOf_nonShiftingIncomesUseTheirDate() {
        val manualPay = testIncome("pay_man_202609", 1L, Instant.parse("2026-09-30T12:00:00Z"))
        val unknownLine = testIncome("pay_zzz_202609", 1L, Instant.parse("2026-09-30T12:00:00Z"))
        val plain = testIncome("i", 1L, Instant.parse("2026-09-15T12:00:00Z"))
        val lines = listOf(salary(), manual)

        assertEquals(sep, fundingMonthOf(manualPay, lines, on, zone))
        assertEquals(sep, fundingMonthOf(unknownLine, lines, on, zone))
        assertEquals(sep, fundingMonthOf(plain, lines, on, zone))
    }

    @Test
    fun fundingMonthOf_yearEndRollsIntoNextYear() {
        val income = testIncome("pay_sal_202612", 1L, Instant.parse("2026-12-31T12:00:00Z"))

        assertEquals(YearMonth.of(2027, 1), fundingMonthOf(income, listOf(salary()), on, zone))
    }

    @Test
    fun fundingIn_keepsOnlyIncomesFundingThatMonth() {
        val septPay = testIncome("pay_sal_202609", 100_000L, Instant.parse("2026-09-30T12:00:00Z"))
        val octPay = testIncome("pay_sal_202610", 100_000L, Instant.parse("2026-10-30T12:00:00Z"))
        val gift = testIncome("gift", 5_000L, Instant.parse("2026-10-05T12:00:00Z"))
        val all = listOf(septPay, octPay, gift)

        assertEquals(listOf(septPay, gift), all.fundingIn(oct, listOf(salary()), on, zone))
        assertEquals(listOf(octPay, gift), all.fundingIn(oct, listOf(salary()), off, zone))
        assertTrue(emptyList<Income>().fundingIn(oct, listOf(salary()), on, zone).isEmpty())
    }
}
