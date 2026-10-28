package com.joaoeoneves.fintrack.domain.model

/** How a [SalaryExtra.amount] is read. */
enum class SalaryExtraAmountType {
    /** A yearly total in cents. */
    FIXED_CENTS,

    /** A multiple of the base salary in ppm (1_000_000 = 1x). */
    MULTIPLE_OF_BASE_PPM,

    /** A share of the annual gross (base x 14) in ppm. */
    PERCENT_OF_ANNUAL_GROSS_PPM,
}

/** The tax taken from a gross [SalaryExtra] before it is paid. */
enum class SalaryExtraDeductions { NONE, IRS, IRS_SS }

/** Whether a [SalaryExtra.amount] is the year's total or the amount of each payment. */
enum class SalaryExtraAmountScope {
    /** The year's total, split over the months. */
    TOTAL,

    /** Paid in each selected month. */
    PER_PAYMENT,
}

/**
 * A user-defined yearly extra (bonus, profit share, flexible benefits...) added to the salary profile. The total
 * for the year is split equally over [months], or with [amountScope] PER_PAYMENT [amount] is paid in each month.
 * [amount] is cents for FIXED_CENTS and ppm otherwise; [feePpm] is a
 * withdrawal fee taken after [deductions]. In NET mode only FIXED_CENTS extras count, as already-net amounts.
 */
data class SalaryExtra(
    val id: String,
    val name: String,
    val amountType: SalaryExtraAmountType,
    val amount: Long,
    val months: Set<Int>,
    val deductions: SalaryExtraDeductions = SalaryExtraDeductions.NONE,
    val feePpm: Int = 0,
    val amountScope: SalaryExtraAmountScope = SalaryExtraAmountScope.TOTAL,
)

const val MAX_SALARY_EXTRAS = 10
const val MAX_EXTRA_NAME_LENGTH = 50

/** Largest multiple of the base salary an extra can be, in ppm: 10x. */
const val MAX_EXTRA_MULTIPLE_PPM = 10_000_000

/** Largest share of the annual gross an extra can be, in ppm: 100%. */
const val MAX_EXTRA_PERCENT_PPM = PPM_SCALE

/** Month an extra is paid in when nothing else says (legacy profit share / bonus). */
const val DEFAULT_EXTRA_PAY_MONTH = 3

private const val EXTRA_PAYMENTS_STEP = 3
private const val LEGACY_FLEX_PAYMENTS = 4

val SALARY_EXTRA_ID_PATTERN = Regex("^[A-Za-z0-9]{1,20}$")

/** The inclusive range of valid amounts for [this] type (cents or ppm). */
val SalaryExtraAmountType.amountRange: LongRange
    get() =
        when (this) {
            SalaryExtraAmountType.FIXED_CENTS -> 1L..MAX_AMOUNT_CENTS
            SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM -> 1L..MAX_EXTRA_MULTIPLE_PPM.toLong()
            SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM -> 1L..MAX_EXTRA_PERCENT_PPM.toLong()
        }

private const val EXTRA_KEY_PREFIX = "profile_extra_"

/** The managed key of the income line of extra [id] paid in [month] (1..12). */
fun managedExtraKey(
    id: String,
    month: Int,
): String = "$EXTRA_KEY_PREFIX${id}_$month"

/** The (extra id, month) a [managedExtraKey] was built from, or `null` for any other key. */
fun parseManagedExtraKey(key: String): Pair<String, Int>? {
    val rest = key.removePrefix(EXTRA_KEY_PREFIX).takeIf { it.length < key.length } ?: return null
    val id = rest.substringBeforeLast('_', "")
    val month = rest.substringAfterLast('_').toIntOrNull()
    val valid = SALARY_EXTRA_ID_PATTERN.matches(id) && month != null && month in 1..MAX_MONTH_OF_YEAR
    return if (valid) id to month else null
}

/** The fixed English names given to the extras migrated from the legacy fields; the app passes localized ones. */
data class LegacyExtraNames(
    val flex: String = "Flexible benefits",
    val profitShare: String = "Profit share",
    val bonus: String = "Bonus",
)

const val LEGACY_FLEX_ID = "flex"
const val LEGACY_PROFIT_SHARE_ID = "profitshare"
const val LEGACY_BONUS_ID = "bonus"

/** The flex / profit-share / bonus fields a salary profile had before extras were user-defined. */
data class LegacySalaryExtras(
    val flexPpm: Int? = null,
    val flexAnnualCents: Long? = null,
    val flexFirstMonth: Int = 1,
    val flexWithdrawalFeePpm: Int = 0,
    val profitShareMultiplierPpm: Int? = null,
    val profitShareMonth: Int? = null,
    val profitShareMonths: Set<Int> = emptySet(),
    val bonusCents: Long? = null,
    val bonusMonth: Int? = null,
) {
    val hasAny: Boolean
        get() =
            flexPpm != null || flexAnnualCents != null || profitShareMultiplierPpm != null || bonusCents != null
}

/** The extras equivalent to these legacy fields in [mode]; flex stays quarterly, the bonus keeps its deductions. */
fun LegacySalaryExtras.toExtras(
    mode: SalaryInputMode,
    names: LegacyExtraNames = LegacyExtraNames(),
): List<SalaryExtra> {
    val gross = mode == SalaryInputMode.GROSS
    val flexMonths =
        List(LEGACY_FLEX_PAYMENTS) { (flexFirstMonth - 1 + it * EXTRA_PAYMENTS_STEP) % MAX_MONTH_OF_YEAR + 1 }
    val flex =
        when {
            gross && (flexPpm ?: 0) > 0 ->
                SalaryExtra(
                    LEGACY_FLEX_ID,
                    names.flex,
                    SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM,
                    flexPpm!!.toLong(),
                    flexMonths.toSet(),
                    feePpm = flexWithdrawalFeePpm,
                )
            !gross && (flexAnnualCents ?: 0L) > 0 ->
                SalaryExtra(
                    LEGACY_FLEX_ID,
                    names.flex,
                    SalaryExtraAmountType.FIXED_CENTS,
                    flexAnnualCents!!,
                    flexMonths.toSet(),
                )
            else -> null
        }
    val profitShare =
        profitShareMultiplierPpm?.takeIf { gross && it > 0 }?.let {
            SalaryExtra(
                LEGACY_PROFIT_SHARE_ID,
                names.profitShare,
                SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM,
                it.toLong(),
                profitShareMonths
                    .ifEmpty { setOfNotNull(profitShareMonth) }
                    .ifEmpty { setOf(DEFAULT_EXTRA_PAY_MONTH) },
            )
        }
    val bonus =
        bonusCents?.takeIf { it > 0 }?.let {
            SalaryExtra(
                LEGACY_BONUS_ID,
                names.bonus,
                SalaryExtraAmountType.FIXED_CENTS,
                it,
                setOf(bonusMonth ?: DEFAULT_EXTRA_PAY_MONTH),
                if (gross) SalaryExtraDeductions.IRS_SS else SalaryExtraDeductions.NONE,
            )
        }
    return listOfNotNull(flex, profitShare, bonus)
}
