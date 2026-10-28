@file:Suppress("ktlint:standard:filename") // the breakdown type and the math that fills it live together

package com.joaoeoneves.fintrack.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.math.RoundingMode.HALF_UP

private const val MONTHS = 12
private const val SALARY_MONTHS = 14

// Digits kept after the cent for the exact intermediate values (the only non-terminating one is duo / 12).
private const val EXACT_SCALE = 12
private val PPM = BigDecimal(PPM_SCALE)

/**
 * Every intermediate value of a salary computation, in cents (0 when not applicable). In NET mode only the
 * amounts the user typed are filled: [netMonthlyCents], the lump nets, the flex amounts, meal, bonus.
 */
data class SalaryBreakdown(
    val baseCents: Long,
    val duoCents: Long,
    val grossCents: Long,
    val ssCents: Long,
    val irsBaseCents: Long,
    val irsSubsidiesCents: Long,
    val irsCents: Long,
    val netMonthlyCents: Long,
    /** The base salary alone after SS and IRS; with [netDuoCents] it adds up to [netMonthlyCents] exactly. */
    val netBaseCents: Long,
    /** The twelfths of the subsidies paid with the monthly salary, net. */
    val netDuoCents: Long,
    /** Gross of each of the two subsidy lump sums (base x (1 - spread)). */
    val lumpGrossCents: Long,
    val holidayLumpNetCents: Long,
    val christmasLumpNetCents: Long,
    /** Annual flexible benefit before the withdrawal fee. */
    val flexGrossAnnualCents: Long,
    /** Annual flexible benefit after the withdrawal fee. */
    val flexAnnualCents: Long,
    /** The four quarterly payments; they add up to [flexAnnualCents]. */
    val flexQuarterlyCents: List<Long>,
    val profitShareCents: Long,
    val bonusCents: Long,
    val mealRatePerDayCents: Long,
)

/** The breakdown of [profile], or `null` while its required amount (base / net salary) is missing. */
fun salaryBreakdownOf(profile: SalaryProfile): SalaryBreakdown? =
    when (profile.inputMode) {
        SalaryInputMode.GROSS -> profile.baseGrossCents?.takeIf { it > 0 }?.let { grossBreakdown(profile, it) }
        SalaryInputMode.NET -> profile.netMonthlyCents?.takeIf { it > 0 }?.let { netBreakdown(profile, it) }
    }

private fun grossBreakdown(
    profile: SalaryProfile,
    base: Long,
): SalaryBreakdown {
    // Exact arithmetic throughout; each reported value is rounded HALF_UP once, none is derived from a rounded one.
    val baseExact = BigDecimal(base)
    val spread = profile.subsidySpreadPpm
    val duo = baseExact.multiply(BigDecimal(2 * spread)).divide(PPM.multiply(BigDecimal(MONTHS)), EXACT_SCALE, HALF_UP)
    val gross = baseExact.add(duo)
    val ss = gross.ppm(profile.ssPpm)
    val irsBase = baseExact.ppm(profile.irsBasePpm)
    val irsSubs = duo.ppm(profile.effectiveIrsSubsidiesPpm)
    val irs = irsBase.add(irsSubs).ppm(PPM_SCALE - profile.irsJovemExemptPpm)
    val netMonthly = gross.subtract(ss).subtract(irs).cents()
    val irsBasePaid = irsBase.ppm(PPM_SCALE - profile.irsJovemExemptPpm)
    val netBase = baseExact.subtract(baseExact.ppm(profile.ssPpm)).subtract(irsBasePaid).cents()
    val lump = base.ppm(PPM_SCALE - spread)
    val lumpNet = lump.lumpNet(profile)
    val flexGross = profile.flexPpm?.let { baseExact.multiply(BigDecimal(SALARY_MONTHS)).ppm(it) } ?: BigDecimal.ZERO
    val flex = flexGross.ppm(PPM_SCALE - profile.flexWithdrawalFeePpm).cents()
    return SalaryBreakdown(
        baseCents = base,
        duoCents = duo.cents(),
        grossCents = gross.cents(),
        ssCents = ss.cents(),
        irsBaseCents = irsBase.cents(),
        irsSubsidiesCents = irsSubs.cents(),
        irsCents = irs.cents(),
        netMonthlyCents = netMonthly,
        netBaseCents = netBase,
        netDuoCents = netMonthly - netBase,
        lumpGrossCents = lump,
        holidayLumpNetCents = lumpNet,
        christmasLumpNetCents = lumpNet,
        flexGrossAnnualCents = flexGross.cents(),
        flexAnnualCents = flex,
        flexQuarterlyCents = quarterly(flex),
        profitShareCents = profile.profitShareMultiplierPpm?.let { base.ppm(it) } ?: 0L,
        bonusCents = profile.bonusCents ?: 0L,
        mealRatePerDayCents = profile.mealRatePerDayCents ?: 0L,
    )
}

private fun netBreakdown(
    profile: SalaryProfile,
    net: Long,
): SalaryBreakdown {
    val flex = profile.flexAnnualCents ?: 0L
    return SalaryBreakdown(
        baseCents = 0L,
        duoCents = 0L,
        grossCents = 0L,
        ssCents = 0L,
        irsBaseCents = 0L,
        irsSubsidiesCents = 0L,
        irsCents = 0L,
        netMonthlyCents = net,
        netBaseCents = net,
        netDuoCents = 0L,
        lumpGrossCents = 0L,
        holidayLumpNetCents = profile.netHolidayLumpCents ?: 0L,
        christmasLumpNetCents = profile.netChristmasLumpCents ?: 0L,
        flexGrossAnnualCents = flex,
        flexAnnualCents = flex,
        flexQuarterlyCents = quarterly(flex),
        profitShareCents = 0L,
        bonusCents = profile.bonusCents ?: 0L,
        mealRatePerDayCents = profile.mealRatePerDayCents ?: 0L,
    )
}

/** A subsidy lump after SS and the subsidy IRS: lump x (1 - ss - irsSub x (1 - exempt)), rounded once. */
private fun Long.lumpNet(profile: SalaryProfile): Long {
    val irsSubsPaid =
        BigDecimal(profile.effectiveIrsSubsidiesPpm).multiply(BigDecimal(PPM_SCALE - profile.irsJovemExemptPpm))
    val factor = PPM.multiply(PPM).subtract(BigDecimal(profile.ssPpm).multiply(PPM)).subtract(irsSubsPaid)
    return BigDecimal(this).multiply(factor).divide(PPM.multiply(PPM), 0, RoundingMode.HALF_UP).toLong()
}

/** [annualCents] in [FLEX_PAYMENTS] equal payments, the rounding remainder on the last one. */
private fun quarterly(annualCents: Long): List<Long> {
    val each = annualCents / FLEX_PAYMENTS
    return List(FLEX_PAYMENTS) { if (it == FLEX_PAYMENTS - 1) annualCents - each * (FLEX_PAYMENTS - 1) else each }
}

/** This many cents times a ppm rate, rounded HALF_UP to cents. */
private fun Long.ppm(ratePpm: Int): Long = BigDecimal(this).ppm(ratePpm).cents()

/** An exact amount times a ppm rate, not rounded. */
private fun BigDecimal.ppm(ratePpm: Int) = multiply(BigDecimal(ratePpm)).divide(PPM, EXACT_SCALE, HALF_UP)

/** This exact amount rounded HALF_UP to whole cents. */
private fun BigDecimal.cents(): Long = setScale(0, HALF_UP).toLong()
