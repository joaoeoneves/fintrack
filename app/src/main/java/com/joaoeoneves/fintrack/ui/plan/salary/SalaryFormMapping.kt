package com.joaoeoneves.fintrack.ui.plan.salary

import com.joaoeoneves.fintrack.domain.model.DEFAULT_SS_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryExtra
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.ui.expense.addedit.toAmountInputText
import java.math.BigDecimal

/** The form showing [profile], with amounts formatted for [decimalSeparator]. */
internal fun SalaryProfile.toForm(decimalSeparator: Char): SalaryForm {
    fun cents(value: Long?) = value?.toAmountInputText(decimalSeparator).orEmpty()

    fun percent(ppm: Int?) = ppm?.takeIf { it > 0 }?.let { formatScaled(it, PERCENT_SCALE, decimalSeparator) }.orEmpty()
    return SalaryForm(
        mode = inputMode,
        base = cents(baseGrossCents),
        spreadPpm = subsidySpreadPpm,
        irsBase = percent(irsBasePpm),
        irsSubsidies = percent(irsSubsidiesPpm),
        ss = if (ssPpm == DEFAULT_SS_PPM) "" else formatScaled(ssPpm, PERCENT_SCALE, decimalSeparator),
        exempt = percent(irsJovemExemptPpm),
        netMonthly = cents(netMonthlyCents),
        netHoliday = cents(netHolidayLumpCents),
        netChristmas = cents(netChristmasLumpCents),
        meal = cents(mealRatePerDayCents),
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
        amountText =
            when (amountType) {
                SalaryExtraAmountType.FIXED_CENTS -> amount.toAmountInputText(decimalSeparator)
                SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM ->
                    formatScaled(amount.toInt(), MULTIPLIER_SCALE, decimalSeparator)
                SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM ->
                    formatScaled(amount.toInt(), PERCENT_SCALE, decimalSeparator)
            },
        months = months.sorted(),
        deductions = deductions,
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
