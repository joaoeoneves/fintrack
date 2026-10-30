package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class SalaryYearEstimateTest {
    private val year = 2026
    private val profile =
        SalaryProfile(baseGrossCents = 200_000, irsBasePpm = 100_000, mealRatePerDayCents = 900)
    private val breakdown = salaryBreakdownOf(profile)!!

    private fun ym(m: Int) = YearMonth.of(year, m)

    private fun yearWorkingDays() = (1..12).sumOf { workingDaysIn(ym(it)) }

    private fun estimate(
        p: SalaryProfile = profile,
        lines: List<PlannedIncome> = salaryProfileLines(p, emptyList(), effectiveFrom = ym(1)),
        settings: List<MonthSettings> = emptyList(),
        vacation: Int = p.vacationDays,
    ) = salaryYearEstimateOf(p, salaryBreakdownOf(p)!!, year, YearEstimateInput(lines, settings, vacation))

    @Test
    fun spread_givesEarlierMonthsTheRemainder() {
        val spread = assumedVacationByMonth(year, emptyList(), 22)
        assertEquals(22, spread.values.sum())
        assertEquals(2, spread[ym(10)])
        assertEquals(1, spread[ym(11)])
        assertEquals(1, spread[ym(12)])
    }

    @Test
    fun spread_leavesMonthsWithDaysOffOut_andSubtractsRecordedDays() {
        val settings = listOf(MonthSettings(ym(1), vacationDays = 3), MonthSettings(ym(2), vacationDays = 4))
        val spread = assumedVacationByMonth(year, settings, 22)
        assertNull(spread[ym(1)])
        assertNull(spread[ym(2)])
        // 22 - 3 - 4 recorded = 15 over the 10 free months: Mar..Jul take 2, the rest 1.
        assertEquals(15, spread.values.sum())
        assertEquals(2, spread[ym(3)])
        assertEquals(1, spread[ym(12)])
    }

    @Test
    fun spread_ignoresSickAndOtherDays_soAMonthWithOnlyThoseStillGetsAssumedVacation() {
        val settings = listOf(MonthSettings(ym(1), sickDays = 5, otherDays = 2))
        val spread = assumedVacationByMonth(year, settings, 22)
        // 22 days over all 12 free months (January included), not 11.
        assertEquals(22, spread.values.sum())
        assertEquals(2, spread[ym(1)])
    }

    @Test
    fun spread_isEmptyWhenRecordedDaysCoverTheVacation_orThereIsNone() {
        assertTrue(assumedVacationByMonth(year, listOf(MonthSettings(ym(1), vacationDays = 25)), 22).isEmpty())
        assertTrue(assumedVacationByMonth(year, emptyList(), 0).isEmpty())
        val allSet = (1..12).map { MonthSettings(ym(it), 20) }
        assertTrue(assumedVacationByMonth(year, allSet, 22).isEmpty())
    }

    @Test
    fun spread_isCappedAtAMonthsWorkingDays() {
        val spread = assumedVacationByMonth(year, emptyList(), 12 * 40)
        (1..12).forEach { assertEquals(workingDaysIn(ym(it)), spread[ym(it)]) }
    }

    @Test
    fun estimate_withNoVacation_matchesTheTypicalYearFigures() {
        val typical = salaryYearSummaryOf(profile, breakdown, year)
        val result = estimate(vacation = 0)
        assertEquals(typical.mealYearCents, result.mealYearCents)
        assertEquals(typical.netYearCents, result.netYearCents)
        assertEquals(typical.payments, result.payments)
    }

    @Test
    fun estimate_usesTheDefaultOf22Days_orTheProfilesOwn() {
        assertEquals(DEFAULT_VACATION_DAYS, profile.vacationDays)
        assertEquals(10, profile.copy(vacationDaysPerYear = 10).vacationDays)
        assertEquals(0, profile.copy(vacationDaysPerYear = 0).vacationDays)
        assertEquals(900L * (yearWorkingDays() - 22), estimate().mealYearCents)
        val ten = profile.copy(vacationDaysPerYear = 10)
        assertEquals(900L * (yearWorkingDays() - 10), estimate(ten).mealYearCents)
    }

    @Test
    fun estimate_respectsDaysOffAlreadySet() {
        val settings = listOf(MonthSettings(ym(1), vacationDays = 10))
        val result = estimate(settings = settings)
        assertEquals(900L * (yearWorkingDays() - 22), result.mealYearCents)
    }

    @Test
    fun estimate_respectsTheStartMonth_andTheVersions() {
        val fromJuly = salaryProfileLines(profile, emptyList(), effectiveFrom = ym(7))
        val result = estimate(lines = fromJuly, vacation = 0)
        val julyOn = (7..12).sumOf { workingDaysIn(ym(it)) }
        assertEquals(900L * julyOn, result.mealYearCents)
        assertEquals(
            6 * (breakdown.netBaseCents + breakdown.netDuoCents) + 900L * julyOn,
            result.netYearCents - result.payments.sumOf { it.totalCents },
        )

        // A raise from October: the net line pays the new amount from October only.
        val raised = profile.copy(baseGrossCents = 300_000)
        val before = salaryProfileLines(profile, emptyList(), effectiveFrom = ym(1))
        val lines = salaryProfileLines(raised, before, effectiveFrom = ym(10))
        val raisedBreakdown = salaryBreakdownOf(raised)!!
        val netLine = lines.single { it.managedKey == MANAGED_NET_SALARY }
        assertEquals(breakdown.netBaseCents, netLine.asOf(ym(9))?.amountCents)
        assertEquals(raisedBreakdown.netBaseCents, netLine.asOf(ym(10))?.amountCents)
        val withRaise = salaryYearEstimateOf(raised, raisedBreakdown, year, YearEstimateInput(lines, emptyList(), 0))
        val withoutRaise = estimate(lines = before, vacation = 0)
        assertTrue(withRaise.netYearCents > withoutRaise.netYearCents)
    }
}
