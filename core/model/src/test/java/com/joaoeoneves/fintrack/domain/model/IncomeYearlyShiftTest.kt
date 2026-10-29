package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** Yearly salary-profile lines (subsidies, profit share, ...) fund the NEXT month like the salary does. */
class IncomeYearlyShiftTest {
    private val zone = ZoneOffset.UTC
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)
    private val on = IncomeFunding(true) { 20 }
    private val off = IncomeFunding(false) { 20 }

    private fun yearly(
        id: String = "xmas",
        label: String = id,
        month: Int = 11,
        cents: Long = 66_330L,
        payDay: Int? = null,
        key: String? = MANAGED_CHRISTMAS,
    ) = PlannedIncome(
        id,
        label,
        cents,
        1,
        frequency = IncomeFrequency.YEARLY,
        monthOfYear = month,
        managedKey = key,
        payDay = payDay,
    )

    private val duodecimos =
        PlannedIncome("duod", "Duodécimos", 112_22L, 0, managedKey = MANAGED_DUODECIMOS)

    private fun income(
        id: String,
        source: String,
        date: String,
    ) = Income(id, source, 10_000L, Instant.parse("${date}T12:00:00Z"), null)

    private fun fundedMonth(
        source: String,
        date: String,
        lines: List<PlannedIncome>,
    ) = fundingMonthOf(income("i-$source", source, date), lines, on, zone)

    @Test
    fun paysForNextMonth_managedYearly_noPayDayOrFrom26() {
        assertTrue(yearly(payDay = null).paysForNextMonth)
        assertTrue(yearly(payDay = 26).paysForNextMonth)
        assertTrue(yearly(payDay = 31).paysForNextMonth)
    }

    @Test
    fun paysForNextMonth_falseBefore26_unmanagedAndOnce() {
        assertFalse(yearly(payDay = 25).paysForNextMonth)
        assertFalse(yearly(key = null).paysForNextMonth)
        assertFalse(yearly(key = null, payDay = 30).paysForNextMonth)
        val once = yearly().copy(frequency = IncomeFrequency.ONCE, monthOfYear = null, yearMonth = nov)
        assertFalse(once.paysForNextMonth)
    }

    @Test
    fun christmasInNovember_fundsDecember() {
        val xmas = yearly()

        assertEquals(0L, on.amountIn(xmas, nov))
        assertEquals(66_330L, on.amountIn(xmas, dec))
        assertEquals(LocalDate.of(2026, 11, 30), on.payDateOf(xmas, dec))
        assertEquals(66_330L, on.amountPaidIn(xmas, nov))
        assertEquals(0L, on.amountPaidIn(xmas, oct))
    }

    @Test
    fun christmasInNovember_shiftOff_staysInNovember() {
        val xmas = yearly()

        assertEquals(66_330L, off.amountIn(xmas, nov))
        assertEquals(0L, off.amountIn(xmas, dec))
        assertEquals(LocalDate.of(2026, 11, 30), off.payDateOf(xmas, nov))
    }

    @Test
    fun yearlyWithPayDayBefore26_staysInItsOwnMonth() {
        val xmas = yearly(payDay = 15)

        assertEquals(66_330L, on.amountIn(xmas, nov))
        assertEquals(0L, on.amountIn(xmas, dec))
    }

    @Test
    fun decemberLine_fundsJanuaryOfNextYear() {
        val bonus = yearly(id = "bonus", month = 12, cents = 50_000L, key = MANAGED_BONUS)
        val jan = YearMonth.of(2027, 1)

        assertEquals(50_000L, on.amountIn(bonus, jan))
        assertEquals(0L, on.amountIn(bonus, dec))
        assertEquals(LocalDate.of(2026, 12, 31), on.payDateOf(bonus, jan))
        assertEquals(jan, on.planMonthOf(bonus, dec))
    }

    @Test
    fun annualPlan_decemberLineCountsInJanuary_notDecember() {
        val bonus = yearly(id = "bonus", month = 12, cents = 50_000L, key = MANAGED_BONUS)
        val plan = annualPlanOf(PLAN_YEAR_2026, planInputs(listOf(bonus)).copy(shiftSalary = true))
        val row = plan.income.rows.single()

        assertEquals(listOf(1), row.months)
        assertEquals(50_000L, plan.income.totalCents)
    }

    @Test
    fun subsidiesRow_novemberHasOnlyDuodecimos_decemberBoth() {
        val lines = listOf(duodecimos, yearly())
        val novRow = incomeRowsIn(lines, nov, on).single { it.component == SalaryComponent.SUBSIDIES }
        val decRow = incomeRowsIn(lines, dec, on).single { it.component == SalaryComponent.SUBSIDIES }

        assertEquals(112_22L, novRow.cents)
        assertEquals(listOf("duod"), novRow.payingLines(nov, on).map { it.id })
        assertEquals(112_22L + 663_30L, decRow.cents)
        assertEquals(listOf("duod", "xmas"), decRow.payingLines(dec, on).map { it.id }.sorted())
    }

    @Test
    fun subsidiesRow_received_dependsOnEveryPayingLineBeingRecorded() {
        val lines = listOf(duodecimos, yearly())
        val novRow = incomeRowsIn(lines, nov, on).single { it.component == SalaryComponent.SUBSIDIES }
        val decRow = incomeRowsIn(lines, dec, on).single { it.component == SalaryComponent.SUBSIDIES }
        val duodOct = income("pay_duod_202610", "Duodécimos", "2026-10-30")
        val duodNov = income("pay_duod_202611", "Duodécimos", "2026-11-30")
        val xmasNov = income("pay_xmas_202611", "xmas", "2026-11-30")

        val novReceived = novRow.receivedIncomes(nov, on, listOf(duodOct))
        assertEquals(listOf("pay_duod_202610"), novReceived?.map { it.id })
        assertNull(decRow.receivedIncomes(dec, on, listOf(duodOct, duodNov)))
        assertNull(decRow.receivedIncomes(dec, on, listOf(xmasNov)))
        assertNotNull(decRow.receivedIncomes(dec, on, listOf(duodNov, xmasNov)))
    }

    @Test
    fun fundingMonthOf_yearlyPaydayIncome_fundsTheNextMonth() {
        val paid = income("pay_xmas_202611", "xmas", "2026-11-30")

        assertEquals(dec, fundingMonthOf(paid, listOf(yearly()), on, zone))
        assertEquals(nov, fundingMonthOf(paid, listOf(yearly()), off, zone))
    }

    @Test
    fun fundingMonthOf_manualYearlySources_atMonthEnd_fundNextMonth() {
        val lines =
            listOf(
                yearly("profit", "Distr. de Lucros", 6, key = "profile_profit_share"),
                yearly("holiday", "Subsídio de férias", 7, key = MANAGED_HOLIDAY),
                yearly("cover", "Benefícios Coverflex", 3, key = "profile_extra_cover"),
            )

        assertEquals(YearMonth.of(2026, 7), fundedMonth("Distr. de Lucros", "2026-06-29", lines))
        assertEquals(YearMonth.of(2026, 8), fundedMonth("Subsídio de férias", "2026-07-31", lines))
        assertEquals(YearMonth.of(2026, 4), fundedMonth("Benefícios Coverflex", "2026-03-31", lines))
    }

    @Test
    fun fundingMonthOf_manualYearlySources_midMonth_fundOwnMonth() {
        val lines =
            listOf(
                yearly("profit", "Distr. de Lucros", 6, key = "profile_profit_share"),
                yearly("holiday", "Subsídio de férias", 7, key = MANAGED_HOLIDAY),
                yearly("cover", "Benefícios Coverflex", 3, key = "profile_extra_cover"),
            )

        assertEquals(YearMonth.of(2026, 6), fundedMonth("Distr. de Lucros", "2026-06-15", lines))
        assertEquals(YearMonth.of(2026, 7), fundedMonth("Subsídio de férias", "2026-07-10", lines))
        assertEquals(YearMonth.of(2026, 3), fundedMonth("Benefícios Coverflex", "2026-03-25", lines))
    }

    private companion object {
        const val PLAN_YEAR_2026 = 2026
    }
}
