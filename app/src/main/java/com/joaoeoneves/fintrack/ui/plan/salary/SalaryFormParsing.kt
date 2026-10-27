package com.joaoeoneves.fintrack.ui.plan.salary

import com.joaoeoneves.fintrack.domain.model.DEFAULT_CHRISTMAS_SUBSIDY_MONTH
import com.joaoeoneves.fintrack.domain.model.DEFAULT_HOLIDAY_SUBSIDY_MONTH
import com.joaoeoneves.fintrack.domain.model.DEFAULT_SS_PPM
import com.joaoeoneves.fintrack.domain.model.MAX_PROFIT_SHARE_PPM
import com.joaoeoneves.fintrack.domain.model.PPM_SCALE
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.ui.expense.addedit.parseAmountCents
import java.math.BigDecimal
import java.math.RoundingMode

private val DECIMAL_PATTERN = Regex("""^(\d+([.,]\d*)?|[.,]\d+)$""")
private const val PPM_PER_PERCENT = PPM_SCALE / 100
internal const val PERCENT_SCALE = 4
internal const val MULTIPLIER_SCALE = 6

/** A parsed optional text field: left blank, a value, or not understood. */
internal sealed interface Parsed<out T> {
    data object Empty : Parsed<Nothing>

    data object Invalid : Parsed<Nothing>

    data class Value<T>(
        val value: T,
    ) : Parsed<T>
}

/** A parsed form: the profile it describes, the fields that did not parse and whether the required amount is set. */
internal data class SalaryFormResult(
    val profile: SalaryProfile,
    val invalid: Set<SalaryField>,
    val hasRequiredAmount: Boolean,
)

/** An optional amount; blank and zero both mean "not set". */
internal fun parseCents(text: String): Parsed<Long> {
    val trimmed = text.trim()
    return when {
        trimmed.isEmpty() || decimalOrNull(trimmed)?.signum() == 0 -> Parsed.Empty
        else -> parseAmountCents(trimmed).fold({ Parsed.Value(it) }, { Parsed.Invalid })
    }
}

/** [text] as a decimal number when it is plain digits with an optional '.' or ',' fraction, else `null`. */
private fun decimalOrNull(text: String): BigDecimal? =
    if (DECIMAL_PATTERN.matches(text)) {
        BigDecimal(text.replace(',', '.').let { if (it.startsWith(".")) "0$it" else it })
    } else {
        null
    }

/** A percentage of 0..[maxPercent] with up to four decimals as ppm ("11.5" is 115000); zero is a value. */
internal fun parsePercentPpm(
    text: String,
    maxPercent: Int = 100,
): Parsed<Int> = parseScaled(text, PERCENT_SCALE, maxPercent * PPM_PER_PERCENT, zeroIsEmpty = false)

/** A multiplier of 0..10 with up to six decimals as ppm ("0.5" is 500000); blank and zero mean "not set". */
internal fun parseMultiplierPpm(text: String): Parsed<Int> {
    val max = MAX_PROFIT_SHARE_PPM
    return parseScaled(text, MULTIPLIER_SCALE, max, zeroIsEmpty = true)
}

private fun parseScaled(
    text: String,
    scale: Int,
    maxPpm: Int,
    zeroIsEmpty: Boolean,
): Parsed<Int> {
    val trimmed = text.trim()
    val ppm = decimalOrNull(trimmed)?.movePointRight(scale)?.setScale(0, RoundingMode.HALF_UP)
    return when {
        trimmed.isEmpty() -> Parsed.Empty
        ppm == null || ppm > BigDecimal(maxPpm) -> Parsed.Invalid
        ppm.signum() == 0 && zeroIsEmpty -> Parsed.Empty
        else -> Parsed.Value(ppm.toInt())
    }
}

/** The form's active fields (GROSS or NET) as a profile; fields of the other mode are left out. */
internal fun SalaryForm.toResult(): SalaryFormResult {
    val reader = FieldReader()
    val profile = if (mode == SalaryInputMode.GROSS) grossProfile(reader) else netProfile(reader)
    val required = if (mode == SalaryInputMode.GROSS) profile.baseGrossCents else profile.netMonthlyCents
    return SalaryFormResult(profile, reader.invalid, hasRequiredAmount = required != null)
}

private fun SalaryForm.grossProfile(reader: FieldReader): SalaryProfile =
    SalaryProfile(
        inputMode = SalaryInputMode.GROSS,
        baseGrossCents = reader.cents(SalaryField.BASE, base),
        subsidySpreadPpm = spreadPpm,
        irsBasePpm = reader.percent(SalaryField.IRS_BASE, irsBase) ?: 0,
        irsSubsidiesPpm = reader.percent(SalaryField.IRS_SUBSIDIES, irsSubsidies),
        ssPpm = reader.percent(SalaryField.SS, ss) ?: DEFAULT_SS_PPM,
        irsJovemExemptPpm = reader.percent(SalaryField.EXEMPT, exempt) ?: 0,
        mealRatePerDayCents = reader.cents(SalaryField.MEAL, meal),
        holidaySubsidyMonth = subsidyMonthPair().first,
        christmasSubsidyMonth = subsidyMonthPair().second,
        flexPpm = reader.percent(SalaryField.FLEX_PERCENT, flexPercent),
        flexFirstMonth = flexFirstMonth,
        flexWithdrawalFeePpm = reader.percent(SalaryField.FLEX_FEE, flexFee) ?: 0,
        bonusCents = reader.cents(SalaryField.BONUS, bonus),
    ).withExtras(this, reader, gross = true)

private fun SalaryForm.netProfile(reader: FieldReader): SalaryProfile =
    SalaryProfile(
        inputMode = SalaryInputMode.NET,
        netMonthlyCents = reader.cents(SalaryField.NET_MONTHLY, netMonthly),
        netHolidayLumpCents = reader.cents(SalaryField.NET_HOLIDAY, netHoliday),
        netChristmasLumpCents = reader.cents(SalaryField.NET_CHRISTMAS, netChristmas),
        mealRatePerDayCents = reader.cents(SalaryField.MEAL, meal),
        holidaySubsidyMonth = subsidyMonthPair().first,
        christmasSubsidyMonth = subsidyMonthPair().second,
        flexAnnualCents = reader.cents(SalaryField.FLEX_ANNUAL, flexAnnual),
        flexFirstMonth = flexFirstMonth,
        bonusCents = reader.cents(SalaryField.BONUS, bonus),
    ).withExtras(this, reader, gross = false)

private fun SalaryProfile.withExtras(
    form: SalaryForm,
    reader: FieldReader,
    gross: Boolean,
): SalaryProfile {
    val multiplier = if (gross) reader.multiplier(SalaryField.PROFIT_SHARE, form.profitMultiplier) else null
    return copy(
        profitShareMultiplierPpm = multiplier,
        profitShareMonths = if (multiplier != null) form.profitMonths.toSet() else emptySet(),
        bonusMonth = form.bonusMonth.takeIf { bonusCents != null },
    )
}

/**
 * The (earlier, later) subsidy months: the two picked ones in order, both the same month when only one is
 * picked, the defaults when none.
 */
internal fun SalaryForm.subsidyMonthPair(): Pair<Int, Int> {
    val months = subsidyMonths.distinct().sorted()
    return Pair(
        months.firstOrNull() ?: DEFAULT_HOLIDAY_SUBSIDY_MONTH,
        months.lastOrNull() ?: DEFAULT_CHRISTMAS_SUBSIDY_MONTH,
    )
}

/** Collects the fields that fail to parse while the form is read. */
private class FieldReader {
    val invalid = mutableSetOf<SalaryField>()

    fun cents(
        field: SalaryField,
        text: String,
    ): Long? = parseCents(text).orNull(field)

    fun percent(
        field: SalaryField,
        text: String,
    ): Int? = parsePercentPpm(text).orNull(field)

    fun multiplier(
        field: SalaryField,
        text: String,
    ): Int? = parseMultiplierPpm(text).orNull(field)

    private fun <T> Parsed<T>.orNull(field: SalaryField): T? =
        when (this) {
            is Parsed.Value -> value
            Parsed.Empty -> null
            Parsed.Invalid -> {
                invalid += field
                null
            }
        }
}
