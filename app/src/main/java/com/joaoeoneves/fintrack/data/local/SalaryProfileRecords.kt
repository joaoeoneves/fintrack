package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.MAX_PROFIT_SHARE_PPM
import com.joaoeoneves.fintrack.domain.model.MIN_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.PPM_SCALE
import com.joaoeoneves.fintrack.domain.model.SUBSIDY_SPREADS_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryProfile

/** Lenient mapping: an unknown input mode falls back to GROSS. */
internal fun LocalSalaryProfile.toDomain(): SalaryProfile =
    SalaryProfile(
        inputMode = SalaryInputMode.entries.find { it.name == inputMode } ?: SalaryInputMode.GROSS,
        baseGrossCents = baseGrossCents,
        subsidySpreadPpm = subsidySpreadPpm,
        irsBasePpm = irsBasePpm,
        irsSubsidiesPpm = irsSubsidiesPpm,
        ssPpm = ssPpm,
        irsJovemExemptPpm = irsJovemExemptPpm,
        netMonthlyCents = netMonthlyCents,
        netHolidayLumpCents = netHolidayLumpCents,
        netChristmasLumpCents = netChristmasLumpCents,
        mealRatePerDayCents = mealRatePerDayCents,
        mealSkipMonths = mealSkipMonths.toSet(),
        holidaySubsidyMonth = holidaySubsidyMonth,
        christmasSubsidyMonth = christmasSubsidyMonth,
        flexPpm = flexPpm,
        flexAnnualCents = flexAnnualCents,
        flexFirstMonth = flexFirstMonth,
        flexWithdrawalFeePpm = flexWithdrawalFeePpm,
        profitShareMultiplierPpm = profitShareMultiplierPpm,
        profitShareMonth = profitShareMonth,
        bonusCents = bonusCents,
        bonusMonth = bonusMonth,
    )

internal fun SalaryProfile.toLocal(): LocalSalaryProfile =
    LocalSalaryProfile(
        inputMode = inputMode.name,
        baseGrossCents = baseGrossCents,
        subsidySpreadPpm = subsidySpreadPpm,
        irsBasePpm = irsBasePpm,
        irsSubsidiesPpm = irsSubsidiesPpm,
        ssPpm = ssPpm,
        irsJovemExemptPpm = irsJovemExemptPpm,
        netMonthlyCents = netMonthlyCents,
        netHolidayLumpCents = netHolidayLumpCents,
        netChristmasLumpCents = netChristmasLumpCents,
        mealRatePerDayCents = mealRatePerDayCents,
        mealSkipMonths = mealSkipMonths.sorted(),
        holidaySubsidyMonth = holidaySubsidyMonth,
        christmasSubsidyMonth = christmasSubsidyMonth,
        flexPpm = flexPpm,
        flexAnnualCents = flexAnnualCents,
        flexFirstMonth = flexFirstMonth,
        flexWithdrawalFeePpm = flexWithdrawalFeePpm,
        profitShareMultiplierPpm = profitShareMultiplierPpm,
        profitShareMonth = profitShareMonth,
        bonusCents = bonusCents,
        bonusMonth = bonusMonth,
    )

/** Mirror of the firestore.rules check; throws [IllegalArgumentException] for a violation. */
internal fun validateSalaryProfile(profile: LocalSalaryProfile) {
    require(SalaryInputMode.entries.any { it.name == profile.inputMode }) { "Invalid input mode ${profile.inputMode}" }
    require(profile.subsidySpreadPpm in SUBSIDY_SPREADS_PPM) { "Subsidy spread must be 0, 50% or 100%" }
    val rates =
        listOfNotNull(
            profile.irsBasePpm,
            profile.irsSubsidiesPpm,
            profile.ssPpm,
            profile.irsJovemExemptPpm,
            profile.flexPpm,
            profile.flexWithdrawalFeePpm,
        )
    require(rates.all { it in 0..PPM_SCALE }) { "Rates must be 0..100%" }
    require(profile.profitShareMultiplierPpm?.let { it in 0..MAX_PROFIT_SHARE_PPM } != false) {
        "Profit share multiplier must be 0..10x"
    }
    val amounts =
        listOfNotNull(
            profile.baseGrossCents,
            profile.netMonthlyCents,
            profile.netHolidayLumpCents,
            profile.netChristmasLumpCents,
            profile.mealRatePerDayCents,
            profile.flexAnnualCents,
            profile.bonusCents,
        )
    require(amounts.all { it in 1..MAX_AMOUNT_CENTS }) { "Amounts must be 1..$MAX_AMOUNT_CENTS cents" }
    val months =
        listOfNotNull(
            profile.holidaySubsidyMonth,
            profile.christmasSubsidyMonth,
            profile.flexFirstMonth,
            profile.profitShareMonth,
            profile.bonusMonth,
        )
    val range = MIN_MONTH_OF_YEAR..MAX_MONTH_OF_YEAR
    require(months.all { it in range }) { "Months must be 1..12" }
    require(profile.mealSkipMonths.size <= MAX_MONTH_OF_YEAR && profile.mealSkipMonths.all { it in range }) {
        "Skipped months must be at most 12 values of 1..12"
    }
}
