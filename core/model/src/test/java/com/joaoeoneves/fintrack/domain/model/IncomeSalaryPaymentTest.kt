package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/** [isSalaryPayment] / [fundingMonthOf] / [fundingInstant]: manual salary incomes matched to the profile lines. */
class IncomeSalaryPaymentTest {
    private val zone = ZoneOffset.UTC
    private val aug = YearMonth.of(2026, 8)
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)
    private val on = IncomeFunding(true) { 20 }
    private val off = IncomeFunding(false) { 20 }

    private fun line(
        id: String,
        label: String,
        payDay: Int? = null,
        managed: Boolean = true,
    ) = PlannedIncome(id, label, 100_000L, 0, managedKey = if (managed) id else null, payDay = payDay)

    private val lines =
        listOf(
            line("salary", "Salário líquido"),
            line("meal", "Subsídio de refeição"),
        )

    private fun income(
        source: String,
        date: String,
        id: String = "i-$source-$date",
    ) = Income(id = id, source = source, amountCents = 50_000L, date = Instant.parse("${date}T12:00:00Z"), note = null)

    private fun fundedMonth(
        source: String,
        date: String,
        funding: IncomeFunding = on,
        planned: List<PlannedIncome> = lines,
    ) = fundingMonthOf(income(source, date), planned, funding, zone)

    @Test
    fun salary_endOfAugust_matchingTheLineLabelByPrefix_fundsSeptember() {
        assertEquals(sep, fundedMonth("Salário", "2026-08-28"))
    }

    @Test
    fun matching_ignoresCaseAccentsAndSurroundingSpaces() {
        assertEquals(sep, fundedMonth("salario liquido", "2026-08-28"))
        assertEquals(sep, fundedMonth("  SALÁRIO   Líquido ", "2026-08-28"))
    }

    @Test
    fun matching_worksBothWays_longerSourceWithTheLabelAsPrefix() {
        assertEquals(sep, fundedMonth("Salário líquido agosto", "2026-08-28"))
    }

    @Test
    fun mealAllowance_onTheLastWorkingDay_matches() {
        // 31 Aug 2026 is a Monday: the last working day of August.
        assertEquals(sep, fundedMonth("Subsídio de refeição", "2026-08-31"))
        assertTrue(isSalaryPayment(income("Subsídio de refeição", "2026-08-31"), lines, zone))
    }

    @Test
    fun otherSources_atMonthEnd_stayInTheirOwnMonth() {
        assertEquals(aug, fundedMonth("Cashback ActivoBank", "2026-08-28"))
        assertEquals(aug, fundedMonth("Prémio Poliempreende", "2026-08-28"))
    }

    @Test
    fun salary_beforeTheTwentySixth_staysInItsOwnMonth() {
        assertEquals(aug, fundedMonth("Salário", "2026-08-15"))
        assertEquals(aug, fundedMonth("Salário", "2026-08-25"))
        assertEquals(sep, fundedMonth("Salário", "2026-08-26"))
    }

    @Test
    fun partialWordsAndEmptySources_doNotMatch() {
        assertFalse(isSalaryPayment(income("Sal", "2026-08-28"), lines, zone))
        assertFalse(isSalaryPayment(income("Salar", "2026-08-28"), lines, zone))
        assertFalse(isSalaryPayment(income("", "2026-08-28"), lines, zone))
        assertFalse(isSalaryPayment(income("   ", "2026-08-28"), lines, zone))
        assertEquals(aug, fundedMonth("Sal", "2026-08-28"))
        assertEquals(aug, fundedMonth("", "2026-08-28"))
    }

    @Test
    fun noManagedProfileLine_nothingMatches() {
        val manual = listOf(line("salary", "Salário líquido", managed = false))

        assertEquals(aug, fundedMonth("Salário líquido", "2026-08-28", planned = manual))
        assertEquals(aug, fundedMonth("Salário", "2026-08-28", planned = emptyList()))
    }

    @Test
    fun lineThatPaysInItsOwnMonth_doesNotMatch() {
        val early = listOf(line("salary", "Salário líquido", payDay = 10))

        assertEquals(aug, fundedMonth("Salário", "2026-08-28", planned = early))
    }

    @Test
    fun shiftOff_everyIncomeFundsItsDateMonth() {
        assertEquals(aug, fundedMonth("Salário", "2026-08-28", funding = off))
        assertEquals(aug, fundedMonth("Salário líquido", "2026-08-31", funding = off))
        val payday = income("Salário", "2026-08-28", id = "pay_salary_202608")
        assertEquals(aug, fundingMonthOf(payday, lines, off, zone))
    }

    @Test
    fun paydayIncome_followsItsLine_shiftingOnlyWhenTheLineDoes() {
        val shifting = income("x", "2026-08-28", id = "pay_salary_202608")
        assertEquals(sep, fundingMonthOf(shifting, lines, on, zone))

        val early = listOf(line("salary", "Salário líquido", payDay = 10))
        // A payday income of a non-shifting line keeps its date month even when its source looks like a salary.
        val own = income("Salário líquido", "2026-08-28", id = "pay_salary_202608")
        assertEquals(aug, fundingMonthOf(own, early, on, zone))
    }

    @Test
    fun fundingInstant_isTheFundedMonthStart_forShiftedIncome_elseTheOwnDate() {
        val salary = income("Salário", "2026-09-30")
        val prize = income("Prémio", "2026-09-30")

        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), salary.fundingInstant(lines, on, zone))
        assertEquals(prize.date, prize.fundingInstant(lines, on, zone))
        assertEquals(salary.date, salary.fundingInstant(lines, off, zone))
    }

    @Test
    fun fundingIn_collectsOnlyTheIncomesOfThatMonth() {
        val sepSalary = income("Salário", "2026-09-30")
        val sepPrize = income("Prémio", "2026-09-30")
        val octSalary = income("Salário", "2026-10-30")
        val all = listOf(sepSalary, sepPrize, octSalary)

        assertEquals(listOf(sepPrize), all.fundingIn(sep, lines, on, zone))
        assertEquals(listOf(sepSalary), all.fundingIn(oct, lines, on, zone))
    }
}
