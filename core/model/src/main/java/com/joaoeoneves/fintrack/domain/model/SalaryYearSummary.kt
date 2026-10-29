package com.joaoeoneves.fintrack.domain.model

import java.math.BigDecimal
import java.math.RoundingMode.HALF_UP
import java.time.YearMonth

private const val MONTHS_IN_YEAR = 12
private const val RATE_SCALE = 12

/** What a [YearPayment] is: one of the two subsidy lump sums or a user-defined extra. */
enum class YearPaymentKind { HOLIDAY, CHRISTMAS, EXTRA }

/** One lump or extra paid outside the monthly salary: its [months], [eachCents] per payment and the [totalCents]. */
data class YearPayment(
    val kind: YearPaymentKind,
    /** The extra's name; empty for the subsidy lumps. */
    val name: String,
    val months: List<Int>,
    val eachCents: Long,
    val totalCents: Long,
)

/**
 * What a typical year of a [SalaryProfile] pays, with nothing specific to one month. The gross/SS/IRS figures are
 * `null` in NET mode (no tax data). The yearly net adds the meal allowance (every working day, no days off).
 */
data class SalaryYearSummary(
    val payments: List<YearPayment>,
    val mealPerDayCents: Long,
    val mealYearCents: Long,
    val netYearCents: Long,
    val grossYearCents: Long?,
    val ssYearCents: Long?,
    val otherDeductionsYearCents: Long?,
    val irsYearCents: Long?,
)

/** The year summary of [profile] for the calendar [year], from its [breakdown]. */
fun salaryYearSummaryOf(
    profile: SalaryProfile,
    breakdown: SalaryBreakdown,
    year: Int,
): SalaryYearSummary {
    val payments = yearPayments(profile, breakdown)
    val meal = breakdown.mealRatePerDayCents
    val mealYear = meal * (1..MONTHS_IN_YEAR).sumOf { workingDaysIn(YearMonth.of(year, it)) }
    val net = breakdown.netMonthlyCents * MONTHS_IN_YEAR + payments.sumOf { it.totalCents } + mealYear
    val taxes = breakdown.takeIf { it.grossCents > 0 }?.let { yearTaxes(profile, it) }
    return SalaryYearSummary(
        payments = payments,
        mealPerDayCents = meal,
        mealYearCents = mealYear,
        netYearCents = net,
        grossYearCents = taxes?.gross,
        ssYearCents = taxes?.ss,
        otherDeductionsYearCents = taxes?.other,
        irsYearCents = taxes?.irs,
    )
}

private fun yearPayments(
    profile: SalaryProfile,
    breakdown: SalaryBreakdown,
): List<YearPayment> {
    val lumps =
        listOf(
            Triple(YearPaymentKind.HOLIDAY, profile.holidaySubsidyMonth, breakdown.holidayLumpNetCents),
            Triple(YearPaymentKind.CHRISTMAS, profile.christmasSubsidyMonth, breakdown.christmasLumpNetCents),
        ).filter { it.third > 0 }
            .map { (kind, month, net) -> YearPayment(kind, "", listOf(month), net, net) }
    val extras =
        breakdown.extras.map { extra ->
            YearPayment(
                kind = YearPaymentKind.EXTRA,
                name = extra.name,
                months = extra.payments.map { it.first },
                eachCents = extra.payments.first().second,
                totalCents = extra.totalCents,
            )
        }
    return lumps + extras
}

private class YearTaxes(
    val gross: Long,
    val ss: Long,
    val other: Long,
    val irs: Long,
)

// Twelve months of the monthly figures, plus the subsidy lumps and the taxed extras (their gross is worked back).
private fun yearTaxes(
    profile: SalaryProfile,
    breakdown: SalaryBreakdown,
): YearTaxes {
    val lumpNets = listOf(breakdown.holidayLumpNetCents, breakdown.christmasLumpNetCents).filter { it > 0 }
    val lumpGross = breakdown.lumpGrossCents * lumpNets.size
    val lumpSs = breakdown.lumpGrossCents.ppm(profile.ssPpm.toLong()) * lumpNets.size
    val lumpOther = breakdown.lumpGrossCents.ppm(profile.otherDeductionsPpm.toLong()) * lumpNets.size
    var gross = breakdown.grossCents * MONTHS_IN_YEAR + lumpGross
    var ss = breakdown.ssCents * MONTHS_IN_YEAR + lumpSs
    val other = breakdown.otherDeductionsCents * MONTHS_IN_YEAR + lumpOther
    var irs = breakdown.irsCents * MONTHS_IN_YEAR + (lumpGross - lumpSs - lumpOther - lumpNets.sum())
    val irsPaidPpm = profile.irsBasePpm.toLong() * (PPM_SCALE - profile.irsJovemExemptPpm) / PPM_SCALE
    breakdown.extras.forEach { paid ->
        val extra = profile.extras.find { it.id == paid.extraId } ?: return@forEach
        val ssPpm = if (extra.deductions == SalaryExtraDeductions.IRS_SS) profile.ssPpm.toLong() else 0L
        val irsPpm = if (extra.deductions == SalaryExtraDeductions.NONE) 0L else irsPaidPpm
        val g = grossOf(paid.totalCents, ssPpm + irsPpm, extra.feePpm.toLong())
        gross += g
        ss += g.ppm(ssPpm)
        irs += g.ppm(irsPpm)
    }
    return YearTaxes(gross, ss, other, irs)
}

// The gross that leaves [net] after the withholding [taxPpm] and then the withdrawal fee [feePpm].
private fun grossOf(
    net: Long,
    taxPpm: Long,
    feePpm: Long,
): Long {
    val keep = BigDecimal(PPM_SCALE - taxPpm).multiply(BigDecimal(PPM_SCALE - feePpm))
    if (keep.signum() <= 0) return net
    return BigDecimal(net)
        .multiply(BigDecimal(PPM_SCALE).multiply(BigDecimal(PPM_SCALE)))
        .divide(keep, RATE_SCALE, HALF_UP)
        .setScale(0, HALF_UP)
        .toLong()
}

private fun Long.ppm(ratePpm: Long): Long = this * ratePpm / PPM_SCALE
