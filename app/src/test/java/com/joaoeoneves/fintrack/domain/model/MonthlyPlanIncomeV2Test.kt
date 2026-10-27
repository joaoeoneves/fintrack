package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** [monthlyPlanOf] with per-working-day lines, extras (yearly and one-off) and skipped months. */
class MonthlyPlanIncomeV2Test : MonthlyPlanTestBase() {
    private val food = BuiltInCategories.all.first { it.kind == CategoryKind.SPENDING }
    private val oct = YearMonth.of(2026, 10)

    private fun yearly(
        amount: Long,
        monthOfYear: Int,
    ) = PlannedIncome("y$amount", "Yearly", amount, 1, frequency = IncomeFrequency.YEARLY, monthOfYear = monthOfYear)

    private fun once(
        amount: Long,
        month: YearMonth,
    ) = PlannedIncome("o$amount", "Once", amount, 2, frequency = IncomeFrequency.ONCE, yearMonth = month)

    private val mealAllowance =
        PlannedIncome("meal", "Meal", 1046L, 3, basis = IncomeBasis.PER_WORKING_DAY)

    @Test
    fun perWorkingDayLine_countsInExpectedTotal() {
        val summary = plan(lines = listOf(line(200_000L), mealAllowance), workingDays = 21)

        assertEquals(200_000L + 1046L * 21, summary.expectedIncomeCents)
        assertEquals(0L, summary.extrasExpectedCents)
    }

    @Test
    fun workingDaysOverride_changesTheTotal() {
        val summary = plan(lines = listOf(mealAllowance), workingDays = 18)

        assertEquals(18_828L, summary.expectedIncomeCents)
    }

    @Test
    fun extrasInTheirMonth_areKeptOutOfExpectedAndSafeToSpend() {
        val lines = listOf(line(200_000L), yearly(50_000L, 10), once(30_000L, oct))
        val summary = plan(buckets = listOf(bucket(food, 50_000L)), lines = lines)

        assertEquals(200_000L, summary.expectedIncomeCents)
        assertEquals(80_000L, summary.extrasExpectedCents)
        assertEquals(200_000L, summary.incomeBasisCents)
        assertEquals(150_000L, summary.safeToSpendCents)
    }

    @Test
    fun extrasOutsideTheirMonth_doNotCount() {
        val lines = listOf(line(200_000L), yearly(50_000L, 7), once(30_000L, YearMonth.of(2027, 10)))
        val summary = plan(lines = lines)

        assertEquals(200_000L, summary.expectedIncomeCents)
        assertEquals(0L, summary.extrasExpectedCents)
    }

    @Test
    fun skippedMonthlyLine_contributesNothing() {
        val skipped = PlannedIncome("s", "Skipped", 10_000L, 0, skipMonths = setOf(10))

        assertEquals(200_000L, plan(lines = listOf(line(200_000L), skipped)).expectedIncomeCents)
        val september = plan(lines = listOf(line(200_000L), skipped), month = YearMonth.of(2026, 9))
        assertEquals(210_000L, september.expectedIncomeCents)
    }

    @Test
    fun onlyExtras_expectedIsZero_andPerWorkingDayExtraMultiplies() {
        val perDayYearly =
            PlannedIncome("p", "Bonus/day", 100L, 0, IncomeBasis.PER_WORKING_DAY, IncomeFrequency.YEARLY, 10)
        val summary = plan(lines = listOf(perDayYearly), workingDays = 21)

        assertEquals(0L, summary.expectedIncomeCents)
        assertEquals(2_100L, summary.extrasExpectedCents)
    }

    @Test
    fun zeroWorkingDays_perDayLineIsZero() {
        assertEquals(0L, plan(lines = listOf(mealAllowance), workingDays = 0).expectedIncomeCents)
    }

    @Test
    fun noLines_extrasZero() {
        assertEquals(0L, plan(lines = emptyList()).extrasExpectedCents)
    }
}
