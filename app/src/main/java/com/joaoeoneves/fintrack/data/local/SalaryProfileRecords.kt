package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.EmploymentSector
import com.joaoeoneves.fintrack.domain.model.LegacySalaryExtras
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_EXTRA_MULTIPLE_PPM
import com.joaoeoneves.fintrack.domain.model.MAX_EXTRA_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MAX_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.MAX_SALARY_EXTRAS
import com.joaoeoneves.fintrack.domain.model.MIN_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.MealPayment
import com.joaoeoneves.fintrack.domain.model.PPM_SCALE
import com.joaoeoneves.fintrack.domain.model.SALARY_EXTRA_ID_PATTERN
import com.joaoeoneves.fintrack.domain.model.SUBSIDY_SPREADS_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryExtra
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountScope
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryExtraDeductions
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.amountRange
import com.joaoeoneves.fintrack.domain.model.toExtras

/**
 * Lenient mapping: an unknown input mode falls back to GROSS. A record with no extras but with legacy
 * flex / profit-share / bonus fields (a v8/v9 file or an older backup) gets the equivalent extras.
 */
internal fun LocalSalaryProfile.toDomain(): SalaryProfile {
    val mode = SalaryInputMode.entries.find { it.name == inputMode } ?: SalaryInputMode.GROSS
    val legacy =
        LegacySalaryExtras(
            flexPpm = flexPpm,
            flexAnnualCents = flexAnnualCents,
            flexFirstMonth = flexFirstMonth,
            flexWithdrawalFeePpm = flexWithdrawalFeePpm,
            profitShareMultiplierPpm = profitShareMultiplierPpm,
            profitShareMonth = profitShareMonth,
            profitShareMonths = profitShareMonths.toSet(),
            bonusCents = bonusCents,
            bonusMonth = bonusMonth,
        )
    return SalaryProfile(
        inputMode = mode,
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
        sector = EmploymentSector.entries.find { it.name == sector },
        mealPayment = MealPayment.entries.find { it.name == mealPayment } ?: MealPayment.CARD,
        otherDeductionsPpm = otherDeductionsPpm.coerceIn(0, PPM_SCALE),
        mealSkipMonths = mealSkipMonths.toSet(),
        holidaySubsidyMonth = holidaySubsidyMonth,
        christmasSubsidyMonth = christmasSubsidyMonth,
        extras =
            if (extras.isEmpty() && legacy.hasAny) {
                legacy.toExtras(mode)
            } else {
                extras.mapNotNull { it.toDomainOrNull() }
            },
    )
}

private fun LocalSalaryExtra.toDomainOrNull(): SalaryExtra? {
    val type = SalaryExtraAmountType.entries.find { it.name == amountType }
    val deduction = SalaryExtraDeductions.entries.find { it.name == deductions }
    val scope = SalaryExtraAmountScope.entries.find { it.name == amountScope }
    if (type == null || deduction == null || scope == null) return null
    return SalaryExtra(id, name, type, amount, months.toSet(), deduction, feePpm, scope)
}

/** Writes the extras only; the legacy fields stay at their defaults (they are read-only for old files). */
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
        sector = sector?.name,
        mealPayment = mealPayment.name,
        otherDeductionsPpm = otherDeductionsPpm,
        mealSkipMonths = mealSkipMonths.sorted(),
        holidaySubsidyMonth = holidaySubsidyMonth,
        christmasSubsidyMonth = christmasSubsidyMonth,
        extras =
            extras.map {
                LocalSalaryExtra(
                    id = it.id,
                    name = it.name,
                    amountType = it.amountType.name,
                    amount = it.amount,
                    months = it.months.sorted(),
                    deductions = it.deductions.name,
                    feePpm = it.feePpm,
                    amountScope = it.amountScope.name,
                )
            },
    )

/** Mirror of the firestore.rules check; throws [IllegalArgumentException] for a violation. */
internal fun validateSalaryProfile(profile: LocalSalaryProfile) {
    require(SalaryInputMode.entries.any { it.name == profile.inputMode }) { "Invalid input mode ${profile.inputMode}" }
    require(profile.sector == null || EmploymentSector.entries.any { it.name == profile.sector }) {
        "Invalid sector ${profile.sector}"
    }
    require(MealPayment.entries.any { it.name == profile.mealPayment }) {
        "Invalid meal payment ${profile.mealPayment}"
    }
    require(profile.subsidySpreadPpm in SUBSIDY_SPREADS_PPM) { "Subsidy spread must be 0, 50% or 100%" }
    val rates =
        listOfNotNull(
            profile.irsBasePpm,
            profile.irsSubsidiesPpm,
            profile.ssPpm,
            profile.irsJovemExemptPpm,
            profile.otherDeductionsPpm,
            profile.flexPpm,
            profile.flexWithdrawalFeePpm,
        )
    require(rates.all { it in 0..PPM_SCALE }) { "Rates must be 0..100%" }
    require(profile.profitShareMultiplierPpm?.let { it in 0..MAX_EXTRA_MULTIPLE_PPM } != false) {
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
    require(profile.profitShareMonths.size <= MAX_MONTH_OF_YEAR && profile.profitShareMonths.all { it in range }) {
        "Profit share months must be at most 12 values of 1..12"
    }
    validateExtras(profile.extras)
}

private fun validateExtras(extras: List<LocalSalaryExtra>) {
    require(extras.size <= MAX_SALARY_EXTRAS) { "At most $MAX_SALARY_EXTRAS extras" }
    require(extras.map { it.id }.toSet().size == extras.size) { "Extra ids must be unique" }
    extras.forEach { extra ->
        require(SALARY_EXTRA_ID_PATTERN.matches(extra.id)) { "Extra id must be 1..20 letters or digits" }
        require(extra.name.isNotBlank() && extra.name.codePointCount(0, extra.name.length) <= MAX_EXTRA_NAME_LENGTH) {
            "Extra name must be 1..$MAX_EXTRA_NAME_LENGTH characters"
        }
        val type =
            requireNotNull(SalaryExtraAmountType.entries.find { it.name == extra.amountType }) {
                "Invalid extra amount type ${extra.amountType}"
            }
        require(extra.amount in type.amountRange) { "Extra amount must be within ${type.amountRange}" }
        require(
            extra.months.isNotEmpty() &&
                extra.months.toSet().size == extra.months.size &&
                extra.months.all { it in MIN_MONTH_OF_YEAR..MAX_MONTH_OF_YEAR },
        ) {
            "Extra months must be distinct values of 1..12"
        }
        require(SalaryExtraDeductions.entries.any { it.name == extra.deductions }) { "Invalid extra deductions" }
        require(SalaryExtraAmountScope.entries.any { it.name == extra.amountScope }) { "Invalid extra amount scope" }
        require(extra.feePpm in 0..PPM_SCALE) { "Extra fee must be 0..100%" }
    }
}
