package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

/** Neutral figures: base 1000.00, SS 11%, IRS 10% on the base and 5% on subsidies. */
class SalaryYearSummaryTest {
    private val year = 2026
    private val workingDays = (1..12).sumOf { workingDaysIn(YearMonth.of(year, it)) }

    private fun gross(
        spread: Int = SPREAD_HALF_PPM,
        meal: Long? = null,
        extras: List<SalaryExtra> = emptyList(),
    ) = SalaryProfile(
        baseGrossCents = 100_000,
        subsidySpreadPpm = spread,
        irsBasePpm = 100_000,
        irsSubsidiesPpm = 50_000,
        ssPpm = 110_000,
        mealRatePerDayCents = meal,
        extras = extras,
    )

    private fun summary(profile: SalaryProfile) = salaryYearSummaryOf(profile, requireNotNull(salaryBreakdownOf(profile)), year)

    @Test
    fun grossHalfSpread_hasTwoLumps_andYearTotalsAddTwelveMonthsToTheLumps() {
        val s = summary(gross())

        assertEquals(
            listOf(
                YearPayment(YearPaymentKind.HOLIDAY, "", listOf(6), 39_500, 39_500),
                YearPayment(YearPaymentKind.CHRISTMAS, "", listOf(12), 39_500, 39_500),
            ),
            s.payments,
        )
        // Monthly rows: gross 108333, SS 11917, IRS 10416, net 86000; each lump: gross 50000, SS 5500, IRS 5000.
        assertEquals(108_333L * 12 + 100_000, s.grossYearCents)
        assertEquals(11_917L * 12 + 11_000, s.ssYearCents)
        assertEquals(10_416L * 12 + 10_000, s.irsYearCents)
        assertEquals(0L, s.otherDeductionsYearCents)
        assertEquals(86_000L * 12 + 79_000, s.netYearCents)
    }

    @Test
    fun grossYearRows_reconcile() {
        val s = summary(gross())

        val deductions = requireNotNull(s.ssYearCents) + requireNotNull(s.irsYearCents)
        assertEquals(requireNotNull(s.grossYearCents) - deductions, s.netYearCents)
    }

    @Test
    fun grossFullSpread_hasNoLumps() {
        val s = summary(gross(spread = SPREAD_FULL_PPM))

        assertEquals(emptyList<YearPayment>(), s.payments)
        assertEquals(116_667L * 12, s.grossYearCents)
        assertEquals(12_833L * 12, s.ssYearCents)
        assertEquals(10_834L * 12, s.irsYearCents)
        assertEquals(93_000L * 12, s.netYearCents)
    }

    @Test
    fun meal_isPerYearWorkingDays_andAddedToTheNet_orZeroWithoutMeal() {
        val withMeal = summary(gross(spread = SPREAD_FULL_PPM, meal = 1_000))
        val without = summary(gross(spread = SPREAD_FULL_PPM))

        assertEquals(1_000L, withMeal.mealPerDayCents)
        assertEquals(1_000L * workingDays, withMeal.mealYearCents)
        assertEquals(without.netYearCents + 1_000L * workingDays, withMeal.netYearCents)
        assertEquals(0L, without.mealPerDayCents)
        assertEquals(0L, without.mealYearCents)
    }

    @Test
    fun extraWithTaxAndFee_isListedNet_andItsGrossIsWorkedBack() {
        // 10000 gross, 21% IRS+SS, 10% fee: 10000 * 0.79 * 0.90 = 7110 net.
        val bonus = testExtra("b", "Bonus", SalaryExtraAmountType.FIXED_CENTS, 10_000, setOf(5), SalaryExtraDeductions.IRS_SS, 100_000)
        val base = summary(gross(spread = SPREAD_FULL_PPM))
        val s = summary(gross(spread = SPREAD_FULL_PPM, extras = listOf(bonus)))

        assertEquals(YearPayment(YearPaymentKind.EXTRA, "Bonus", listOf(5), 7_110, 7_110), s.payments.single())
        assertEquals(base.netYearCents + 7_110, s.netYearCents)
        assertEquals(requireNotNull(base.grossYearCents) + 10_000, s.grossYearCents)
        assertEquals(requireNotNull(base.ssYearCents) + 1_100, s.ssYearCents)
        assertEquals(requireNotNull(base.irsYearCents) + 1_000, s.irsYearCents)
    }

    @Test
    fun multiPaymentExtra_isOneEntryWithTheFirstPaymentAsEach_andLumpsComeFirst() {
        // 7% of the annual gross (1000 x 14) over four months, untaxed.
        val s = summary(gross(extras = listOf(testExtra())))

        assertEquals(listOf(YearPaymentKind.HOLIDAY, YearPaymentKind.CHRISTMAS, YearPaymentKind.EXTRA), s.payments.map { it.kind })
        val extra = s.payments.last()
        assertEquals(listOf(1, 4, 7, 10), extra.months)
        assertEquals(24_500L, extra.eachCents)
        assertEquals(98_000L, extra.totalCents)
        assertEquals(86_000L * 12 + 79_000 + 98_000, s.netYearCents)
        // An untaxed extra adds its whole amount to the gross and nothing to SS or IRS.
        assertEquals(108_333L * 12 + 100_000 + 98_000, s.grossYearCents)
        assertEquals(11_917L * 12 + 11_000, s.ssYearCents)
    }

    @Test
    fun netMode_hasNoTaxFigures_butKeepsNetLumpsExtrasAndMeal() {
        val profile =
            SalaryProfile(
                inputMode = SalaryInputMode.NET,
                netMonthlyCents = 80_000,
                netHolidayLumpCents = 30_000,
                netChristmasLumpCents = 40_000,
                mealRatePerDayCents = 500,
                extras = listOf(fixedExtra(cents = 5_000, month = 3)),
            )

        val s = summary(profile)

        assertNull(s.grossYearCents)
        assertNull(s.ssYearCents)
        assertNull(s.otherDeductionsYearCents)
        assertNull(s.irsYearCents)
        assertEquals(listOf(30_000L, 40_000L, 5_000L), s.payments.map { it.totalCents })
        assertEquals(500L * workingDays, s.mealYearCents)
        assertEquals(80_000L * 12 + 70_000 + 5_000 + 500L * workingDays, s.netYearCents)
    }

    @Test
    fun netMode_withoutLumps_hasNoPayments() {
        val s = summary(SalaryProfile(inputMode = SalaryInputMode.NET, netMonthlyCents = 80_000))

        assertEquals(emptyList<YearPayment>(), s.payments)
        assertEquals(80_000L * 12, s.netYearCents)
        assertEquals(0L, s.mealYearCents)
    }
}
