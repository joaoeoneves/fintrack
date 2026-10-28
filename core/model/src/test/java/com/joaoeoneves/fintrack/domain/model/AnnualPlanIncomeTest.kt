package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnualPlanIncomeTest {
    private val food = planEnvelope("food")
    private val dinners = planCategory("dinners", "food")
    private val other = planCategory("other", "gone")

    private fun plan(
        lines: List<PlannedIncome> = emptyList(),
        settings: List<MonthSettings> = emptyList(),
        budgets: List<Budget> = emptyList(),
        envelope: Envelope = food,
        year: Int = PLAN_YEAR,
    ) = annualPlanOf(
        year,
        planInputs(
            lines = lines,
            settings = settings,
            budgets = budgets,
            categories = listOf(dinners, other),
            envelopes = listOf(envelope),
        ),
    )

    private fun once(
        id: String,
        cents: Long,
        order: Int,
        month: java.time.YearMonth,
    ) = PlannedIncome(id, id, cents, order, frequency = IncomeFrequency.ONCE, yearMonth = month)

    private fun calendarDays(year: Int = PLAN_YEAR) = (1..12).map { workingDaysIn(planMonth(it, year)) }

    @Test
    fun fixedLine_isRateTimesTwelve_minusSkipMonths() {
        val result = plan(listOf(planFixed("salary", 100_000L, skip = setOf(7, 8))))

        assertEquals(10 * 100_000L, result.income.regularCents)
    }

    @Test
    fun perWorkingDayLine_usesRealWorkingDaysOfEachMonth() {
        val result = plan(listOf(planPerDay("meal", 1_000L)))

        assertEquals(calendarDays().sum() * 1_000L, result.income.regularCents)
    }

    @Test
    fun perWorkingDayLine_overrideAndDaysOff_replaceTheCalendarCount() {
        val settings =
            listOf(
                MonthSettings(planMonth(3), workingDaysOverride = 15),
                MonthSettings(planMonth(5), workingDaysOverride = null, daysOff = 4),
            )

        val result = plan(listOf(planPerDay("meal", 1_000L)), settings)

        val days = calendarDays().toMutableList()
        days[2] = 15
        days[4] = days[4] - 4
        assertEquals(days.sum() * 1_000L, result.income.regularCents)
    }

    @Test
    fun overrideWinsOverDaysOff_andDaysOffNeverGoBelowZero() {
        val settings =
            listOf(
                MonthSettings(planMonth(1), workingDaysOverride = 10, daysOff = 5),
                MonthSettings(planMonth(2), workingDaysOverride = null, daysOff = 99),
            )

        val result = plan(listOf(planPerDay("meal", 100L)), settings)

        val days = calendarDays()
        assertEquals((10 + 0 + days.drop(2).sum()) * 100L, result.income.regularCents)
    }

    @Test
    fun perWorkingDayLine_skipMonth_isNotPaid() {
        val result = plan(listOf(planPerDay("meal", 1_000L, skip = setOf(8))))

        assertEquals((calendarDays().sum() - calendarDays()[7]) * 1_000L, result.income.regularCents)
    }

    @Test
    fun extras_lumpsAndFlexQuarters_listedByMonth_andOtherYearExcluded() {
        val lumpJune = PlannedIncome("june", "june", 60_000L, 0, frequency = IncomeFrequency.YEARLY, monthOfYear = 6)
        val lumpDec = PlannedIncome("dec", "dec", 70_000L, 1, frequency = IncomeFrequency.YEARLY, monthOfYear = 12)
        val flex =
            listOf(3, 6, 9, 12).mapIndexed { i, m ->
                once("flex$m", 30_000L, 2 + i, planMonth(m))
            }
        val elsewhere =
            once("old", 99_999L, 9, planMonth(5, PLAN_YEAR - 1))

        val result = plan(listOf(lumpJune, lumpDec, elsewhere) + flex)

        assertEquals(0L, result.income.regularCents)
        assertEquals(60_000L + 70_000L + 4 * 30_000L, result.income.extrasCents)
        assertEquals(listOf(3, 6, 6, 9, 12, 12), result.income.extras.map { it.month })
        assertTrue(result.income.extras.none { it.line.id == "old" })
        assertEquals(result.income.extrasCents, result.income.totalCents)
    }

    @Test
    fun leapYear_februaryWorkingDaysUseTheLeapCalendar() {
        val result = plan(listOf(planPerDay("meal", 100L)), year = 2024)

        assertEquals(calendarDays(2024).sum() * 100L, result.income.regularCents)
        assertEquals(planMonth(2, 2024).lengthOfMonth(), 29)
    }

    @Test
    fun leftover_positiveZeroAndNegative() {
        val line = listOf(planFixed("salary", 1_000L))
        val budget = { cents: Long -> listOf(Budget(dinners.id, cents)) }

        assertEquals(12_000L - 6_000L, plan(line, budgets = budget(500L)).leftoverCents)
        assertEquals(0L, plan(line, budgets = budget(1_000L)).leftoverCents)
        assertEquals(-12_000L, plan(budgets = budget(1_000L)).leftoverCents)
    }

    @Test
    fun leftover_includesExtras() {
        val lump = PlannedIncome("l", "l", 5_000L, 0, frequency = IncomeFrequency.YEARLY, monthOfYear = 6)

        assertEquals(5_000L, plan(listOf(lump)).leftoverCents)
    }

    @Test
    fun funding_fundingLineIncomeMinusEnvelopePlanned() {
        val line = planFixed("meal", 20_000L)
        val budgets = listOf(Budget(dinners.id, 16_000L, skipMonths = setOf(8)))
        val funded = food.copy(fundedByLineId = "meal")

        val lines = listOf(line, planFixed("salary", 99L, 1))
        val funding = plan(lines, budgets = budgets, envelope = funded).funding.single()

        assertEquals(funded, funding.envelope)
        assertEquals(line, funding.line)
        assertEquals(12 * 20_000L, funding.incomeCents)
        assertEquals(11 * 16_000L, funding.plannedCents)
        assertEquals(12 * 20_000L - 11 * 16_000L, funding.leftoverCents)
    }

    @Test
    fun funding_negativeLeftoverWhenPlanExceedsIncome() {
        val line = planFixed("meal", 100L)

        val funding =
            plan(
                listOf(line),
                budgets = listOf(Budget(dinners.id, 500L)),
                envelope = food.copy(fundedByLineId = "meal"),
            ).funding
                .single()

        assertEquals(1_200L - 6_000L, funding.leftoverCents)
    }

    @Test
    fun funding_fixedAmountEnvelope_plannedIsAmountTimesTwelve() {
        val funded = food.copy(amountCents = 15_000L, fundedByLineId = "meal")

        val funding = plan(listOf(planFixed("meal", 20_000L)), envelope = funded).funding.single()

        assertEquals(180_000L, funding.plannedCents)
    }

    @Test
    fun funding_missingExtraOrMoneylessLine_isDropped() {
        val extra = PlannedIncome("x", "x", 100L, 0, frequency = IncomeFrequency.YEARLY, monthOfYear = 6)
        val empty = planFixed("y", 0L, order = 1)

        assertTrue(plan(listOf(extra), envelope = food.copy(fundedByLineId = "x")).funding.isEmpty())
        assertTrue(plan(listOf(empty), envelope = food.copy(fundedByLineId = "y")).funding.isEmpty())
        assertTrue(plan(listOf(empty), envelope = food.copy(fundedByLineId = "missing")).funding.isEmpty())
    }

    @Test
    fun funding_twoEnvelopesOnOneLine_firstByOrderOnly() {
        val line = planFixed("meal", 100L)
        val second = planEnvelope("second", order = 1).copy(fundedByLineId = "meal")
        val result =
            annualPlanOf(
                PLAN_YEAR,
                planInputs(lines = listOf(line), envelopes = listOf(second, food.copy(fundedByLineId = "meal"))),
            )

        assertEquals(listOf("food"), result.funding.map { it.envelope.id })
    }

    @Test
    fun emptyInputs_giveAnEmptyPlan() {
        val result = plan()

        assertEquals(0L, result.income.totalCents)
        assertEquals(0L, result.plannedCents)
        assertEquals(0L, result.leftoverCents)
        assertTrue(result.subscriptions.isEmpty())
        assertTrue(result.funding.isEmpty())
    }
}
