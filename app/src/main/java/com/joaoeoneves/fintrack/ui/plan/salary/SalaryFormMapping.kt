package com.joaoeoneves.fintrack.ui.plan.salary

import com.joaoeoneves.fintrack.domain.model.DEFAULT_SS_PPM
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
        mealSkip = mealSkipMonths,
        holidayMonth = holidaySubsidyMonth,
        christmasMonth = christmasSubsidyMonth,
        flexPercent = percent(flexPpm),
        flexAnnual = cents(flexAnnualCents),
        flexFirstMonth = flexFirstMonth,
        flexFee = percent(flexWithdrawalFeePpm),
        profitMultiplier =
            profitShareMultiplierPpm?.let { formatScaled(it, MULTIPLIER_SCALE, decimalSeparator) }.orEmpty(),
        profitMonth = profitShareMonth ?: SalaryForm().profitMonth,
        bonus = cents(bonusCents),
        bonusMonth = bonusMonth ?: SalaryForm().bonusMonth,
    )
}

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
