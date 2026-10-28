package com.joaoeoneves.fintrack.ui.plan.salary

import com.joaoeoneves.fintrack.domain.model.DEFAULT_SS_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryExtra
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import java.math.BigDecimal

/** The form showing [profile], with rates formatted for [decimalSeparator]. */
internal fun SalaryProfile.toForm(decimalSeparator: Char): SalaryForm {
    fun percent(ppm: Int?) = ppm?.takeIf { it > 0 }?.let { formatScaled(it, PERCENT_SCALE, decimalSeparator) }.orEmpty()
    return SalaryForm(
        mode = inputMode,
        base = baseGrossCents ?: 0L,
        spreadPpm = subsidySpreadPpm,
        irsBase = percent(irsBasePpm),
        irsSubsidies = percent(irsSubsidiesPpm),
        ss = if (ssPpm == DEFAULT_SS_PPM) "" else formatScaled(ssPpm, PERCENT_SCALE, decimalSeparator),
        exempt = percent(irsJovemExemptPpm),
        netMonthly = netMonthlyCents ?: 0L,
        netHoliday = netHolidayLumpCents ?: 0L,
        netChristmas = netChristmasLumpCents ?: 0L,
        meal = mealRatePerDayCents ?: 0L,
        subsidyMonths = listOf(holidaySubsidyMonth, christmasSubsidyMonth).distinct().sorted(),
        extras = extras,
    )
}

/** The editor form for [this] extra, with the value and fee formatted for [decimalSeparator]. */
internal fun SalaryExtra.toExtraForm(decimalSeparator: Char): SalaryExtraForm =
    SalaryExtraForm(
        editingId = id,
        name = name,
        amountType = amountType,
        amountCents = if (amountType == SalaryExtraAmountType.FIXED_CENTS) amount else 0L,
        amountText =
            when (amountType) {
                SalaryExtraAmountType.FIXED_CENTS -> ""
                SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM ->
                    formatScaled(amount.toInt(), MULTIPLIER_SCALE, decimalSeparator)
                SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM ->
                    formatScaled(amount.toInt(), PERCENT_SCALE, decimalSeparator)
            },
        months = months.sorted(),
        deductions = deductions,
        amountScope = amountScope,
        feeText = feePpm.takeIf { it > 0 }?.let { formatScaled(it, PERCENT_SCALE, decimalSeparator) }.orEmpty(),
    )

private fun formatScaled(
    ppm: Int,
    scale: Int,
    decimalSeparator: Char,
): String =
    BigDecimal(ppm)
        .movePointLeft(scale)
        .stripTrailingZeros()
        .toPlainString()
        .replace('.', decimalSeparator)
