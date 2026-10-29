@file:Suppress("ktlint:standard:filename") // the breakdown type and the math that fills it live together

package com.joaoeoneves.fintrack.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.math.RoundingMode.HALF_UP

private const val MONTHS = 12
private const val SALARY_MONTHS = 14L

// Digits kept after the cent for the exact intermediate values (the only non-terminating one is duo / 12).
private const val EXACT_SCALE = 12
private val PPM = BigDecimal(PPM_SCALE)

/**
 * Every intermediate value of a salary computation, in cents (0 when not applicable). In NET mode only the
 * amounts the user typed are filled: [netMonthlyCents], the lump nets, the fixed extras, meal.
 */
data class SalaryBreakdown(
    val baseCents: Long,
    val duoCents: Long,
    val grossCents: Long,
    val ssCents: Long,
    /** ADSE / other deductions ([SalaryProfile.otherDeductionsPpm]) on the monthly gross; 0 when none. */
    val otherDeductionsCents: Long,
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
    /** The user-defined extras that pay something, in the profile's order. */
    val extras: List<ExtraBreakdown>,
    val mealRatePerDayCents: Long,
)

/** What one [SalaryExtra] pays: its [totalCents] for the year (after deductions and fee) and the [payments]. */
data class ExtraBreakdown(
    val extraId: String,
    val name: String,
    val totalCents: Long,
    /**
     * (month 1..12, cents) ascending; they add up to [totalCents]. For a yearly total the rounding remainder is on
     * the last month; for a per-payment extra every payment is equal.
     */
    val payments: List<Pair<Int, Long>>,
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
    val other = gross.ppm(profile.otherDeductionsPpm)
    val irsBase = baseExact.ppm(profile.irsBasePpm)
    val irsSubs = duo.ppm(profile.effectiveIrsSubsidiesPpm)
    val irs = irsBase.add(irsSubs).ppm(PPM_SCALE - profile.irsJovemExemptPpm)
    val netMonthly =
        gross
            .subtract(ss)
            .subtract(other)
            .subtract(irs)
            .cents()
    val irsBasePaid = irsBase.ppm(PPM_SCALE - profile.irsJovemExemptPpm)
    val netBase =
        baseExact
            .subtract(baseExact.ppm(profile.ssPpm))
            .subtract(baseExact.ppm(profile.otherDeductionsPpm))
            .subtract(irsBasePaid)
            .cents()
    val lump = base.ppm(PPM_SCALE - spread)
    val lumpNet = lump.lumpNet(profile)
    return SalaryBreakdown(
        baseCents = base,
        duoCents = duo.cents(),
        grossCents = gross.cents(),
        ssCents = ss.cents(),
        otherDeductionsCents = other.cents(),
        irsBaseCents = irsBase.cents(),
        irsSubsidiesCents = irsSubs.cents(),
        // The shown rows add up: IRS is what is left of the rounded gross after the rounded SS, other and net.
        irsCents = gross.cents() - ss.cents() - other.cents() - netMonthly,
        netMonthlyCents = netMonthly,
        netBaseCents = netBase,
        netDuoCents = netMonthly - netBase,
        lumpGrossCents = lump,
        holidayLumpNetCents = lumpNet,
        christmasLumpNetCents = lumpNet,
        extras = profile.extras.mapNotNull { extraBreakdown(it, profile, base) },
        mealRatePerDayCents = profile.mealRatePerDayCents ?: 0L,
    )
}

private fun netBreakdown(
    profile: SalaryProfile,
    net: Long,
): SalaryBreakdown =
    SalaryBreakdown(
        baseCents = 0L,
        duoCents = 0L,
        grossCents = 0L,
        ssCents = 0L,
        otherDeductionsCents = 0L,
        irsBaseCents = 0L,
        irsSubsidiesCents = 0L,
        irsCents = 0L,
        netMonthlyCents = net,
        netBaseCents = net,
        netDuoCents = 0L,
        lumpGrossCents = 0L,
        holidayLumpNetCents = profile.netHolidayLumpCents ?: 0L,
        christmasLumpNetCents = profile.netChristmasLumpCents ?: 0L,
        extras = profile.extras.mapNotNull { extraBreakdown(it, profile, null) },
        mealRatePerDayCents = profile.mealRatePerDayCents ?: 0L,
    )

/**
 * A subsidy lump after SS, other deductions and IRS: lump x (1 - ss - other - irsBase x (1 - exempt)), rounded
 * once. The lump is withheld at
 * about the base rate, not the much lower rate that only fits the small monthly twelfths.
 */
private fun Long.lumpNet(profile: SalaryProfile): Long {
    val irsPaid = BigDecimal(profile.irsBasePpm).multiply(BigDecimal(PPM_SCALE - profile.irsJovemExemptPpm))
    val factor =
        PPM
            .multiply(PPM)
            .subtract(BigDecimal(profile.ssPpm).multiply(PPM))
            .subtract(BigDecimal(profile.otherDeductionsPpm).multiply(PPM))
            .subtract(irsPaid)
    return BigDecimal(this).multiply(factor).divide(PPM.multiply(PPM), 0, RoundingMode.HALF_UP).toLong()
}

/** What [extra] pays over the year and in which months; `null` when it pays nothing. [base] is `null` in NET mode. */
private fun extraBreakdown(
    extra: SalaryExtra,
    profile: SalaryProfile,
    base: Long?,
): ExtraBreakdown? {
    val cents = extraCents(extra, profile, base)
    val months = extra.months.sorted()
    if (cents == 0L || months.isEmpty()) return null
    val perPayment = extra.amountScope == SalaryExtraAmountScope.PER_PAYMENT
    val each = if (perPayment) cents else cents / months.size
    val payments =
        months.mapIndexed { index, month ->
            month to if (!perPayment && index == months.lastIndex) cents - each * index else each
        }
    return ExtraBreakdown(extra.id, extra.name, if (perPayment) cents * months.size else cents, payments)
}

/**
 * The amount of [extra] (the yearly total, or one payment when PER_PAYMENT) in one exact computation, rounded
 * HALF_UP once: FIXED is the amount, MULTIPLE is
 * base x multiple, PERCENT is the annual gross (base x 14) x share; then x (1 - tax) x (1 - fee). [base] is `null`
 * in NET mode, where only FIXED extras count and are already net. 0 when it pays nothing.
 */
private fun extraCents(
    extra: SalaryExtra,
    profile: SalaryProfile,
    base: Long?,
): Long {
    val amount = BigDecimal(extra.amount)
    val exact =
        when {
            base == null && extra.amountType != SalaryExtraAmountType.FIXED_CENTS -> BigDecimal.ZERO
            base == null -> amount
            else -> {
                val raw =
                    when (extra.amountType) {
                        SalaryExtraAmountType.FIXED_CENTS -> amount.multiply(PPM)
                        SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM -> BigDecimal(base).multiply(amount)
                        SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM ->
                            BigDecimal(base * SALARY_MONTHS).multiply(amount)
                    }
                // Denominators: raw / 1e6, tax factor / 1e12, fee / 1e6.
                val oneNum = PPM.multiply(PPM)
                raw
                    .multiply(oneNum.subtract(extra.taxNumerator(profile)))
                    .multiply(BigDecimal(PPM_SCALE - extra.feePpm))
                    .divide(oneNum.multiply(oneNum), EXACT_SCALE, HALF_UP)
            }
        }
    return exact.cents().coerceAtLeast(0L)
}

/** The tax taken from an extra in ppm squared: the base IRS rate (never the subsidy one) x (1 - exempt), plus SS. */
private fun SalaryExtra.taxNumerator(profile: SalaryProfile): BigDecimal {
    val irsPaid = BigDecimal(profile.irsBasePpm).multiply(BigDecimal(PPM_SCALE - profile.irsJovemExemptPpm))
    return when (deductions) {
        SalaryExtraDeductions.NONE -> BigDecimal.ZERO
        SalaryExtraDeductions.IRS -> irsPaid
        SalaryExtraDeductions.IRS_SS -> irsPaid.add(BigDecimal(profile.ssPpm).multiply(PPM))
    }
}

/** This many cents times a ppm rate, rounded HALF_UP to cents. */
private fun Long.ppm(ratePpm: Int): Long = BigDecimal(this).ppm(ratePpm).cents()

/** An exact amount times a ppm rate, not rounded. */
private fun BigDecimal.ppm(ratePpm: Int) = multiply(BigDecimal(ratePpm)).divide(PPM, EXACT_SCALE, HALF_UP)

/** This exact amount rounded HALF_UP to whole cents. */
private fun BigDecimal.cents(): Long = setScale(0, HALF_UP).toLong()
