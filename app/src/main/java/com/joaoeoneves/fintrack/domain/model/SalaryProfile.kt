package com.joaoeoneves.fintrack.domain.model

/** Whether the salary profile is typed as a gross salary with tax rates or as the net amounts received. */
enum class SalaryInputMode { GROSS, NET }

/** Highest value of a rate in parts per million (100%). */
const val PPM_SCALE = 1_000_000

/** Largest profit-share multiplier (of the base salary) in ppm: 10x. */
const val MAX_PROFIT_SHARE_PPM = 10_000_000

/** The three "duodecimos" choices as a share of the two subsidies spread over the year (ppm). */
const val SPREAD_NONE_PPM = 0
const val SPREAD_HALF_PPM = 500_000
const val SPREAD_FULL_PPM = PPM_SCALE

/** The valid [SalaryProfile.subsidySpreadPpm] values. */
val SUBSIDY_SPREADS_PPM: List<Int> = listOf(SPREAD_NONE_PPM, SPREAD_HALF_PPM, SPREAD_FULL_PPM)

/** Default social-security rate (11%). */
const val DEFAULT_SS_PPM = 110_000

/** Default month (August) the meal allowance is not paid in. */
const val DEFAULT_MEAL_SKIP_MONTH = 8

const val DEFAULT_HOLIDAY_SUBSIDY_MONTH = 6
const val DEFAULT_CHRISTMAS_SUBSIDY_MONTH = 12
const val DEFAULT_FLEX_FIRST_MONTH = 1

/** Month a profit share or bonus is paid in when the profile does not say. */
const val DEFAULT_EXTRA_PAY_MONTH = 3

/** `plannedIncome.managedKey` values of the lines generated from the salary profile. */
const val MANAGED_NET_SALARY = "profile_net_salary"
const val MANAGED_MEAL = "profile_meal"
const val MANAGED_HOLIDAY = "profile_holiday"
const val MANAGED_CHRISTMAS = "profile_christmas"
const val MANAGED_PROFIT_SHARE = "profile_profit_share"
const val MANAGED_BONUS = "profile_bonus"

/** Number of quarterly flexible-benefit payments. */
const val FLEX_PAYMENTS = 4

/** The managed key of the flexible-benefit payment [index] (0..3). */
fun managedFlexKey(index: Int): String = "profile_flex_q${index + 1}"

/** Every key a managed line can carry. */
val MANAGED_KEYS: List<String> =
    listOf(MANAGED_NET_SALARY, MANAGED_MEAL, MANAGED_HOLIDAY, MANAGED_CHRISTMAS, MANAGED_PROFIT_SHARE, MANAGED_BONUS) +
        List(FLEX_PAYMENTS, ::managedFlexKey)

/** Default (English) labels of the generated lines; the UI passes localized ones. Existing lines keep theirs. */
data class SalaryLineLabels(
    val netSalary: String = "Net salary",
    val meal: String = "Meal allowance",
    val holiday: String = "Holiday subsidy",
    val christmas: String = "Christmas subsidy",
    val flex: String = "Flexible benefits",
    val profitShare: String = "Profit share",
    val bonus: String = "Bonus",
)

/**
 * The user's salary set-up, a singleton from which the plan's income lines are generated (see
 * [salaryProfileLines]). Every rate is an integer in ppm (1_000_000 = 100%) and every amount is in cents. Only
 * the base ([baseGrossCents], GROSS mode) or [netMonthlyCents] (NET mode) is needed; everything else is optional.
 *
 * GROSS mode: [subsidySpreadPpm] is the share of the holiday and Christmas subsidies paid in twelfths with the
 * monthly salary (0, 50% or 100%); [irsBasePpm] / [irsSubsidiesPpm] (default: the base rate) are the withholding
 * rates; [irsJovemExemptPpm] is the exempt share of the IRS (IRS paid = rate x (1 - exempt)).
 * NET mode: the net amounts as received, no tax fields. [flexPpm] applies to GROSS, [flexAnnualCents] to NET.
 */
data class SalaryProfile(
    val inputMode: SalaryInputMode = SalaryInputMode.GROSS,
    val baseGrossCents: Long? = null,
    val subsidySpreadPpm: Int = SPREAD_HALF_PPM,
    val irsBasePpm: Int = 0,
    val irsSubsidiesPpm: Int? = null,
    val ssPpm: Int = DEFAULT_SS_PPM,
    val irsJovemExemptPpm: Int = 0,
    val netMonthlyCents: Long? = null,
    val netHolidayLumpCents: Long? = null,
    val netChristmasLumpCents: Long? = null,
    val mealRatePerDayCents: Long? = null,
    val mealSkipMonths: Set<Int> = setOf(DEFAULT_MEAL_SKIP_MONTH),
    val holidaySubsidyMonth: Int = DEFAULT_HOLIDAY_SUBSIDY_MONTH,
    val christmasSubsidyMonth: Int = DEFAULT_CHRISTMAS_SUBSIDY_MONTH,
    val flexPpm: Int? = null,
    val flexAnnualCents: Long? = null,
    val flexFirstMonth: Int = DEFAULT_FLEX_FIRST_MONTH,
    val flexWithdrawalFeePpm: Int = 0,
    val profitShareMultiplierPpm: Int? = null,
    val profitShareMonth: Int? = null,
    val bonusCents: Long? = null,
    val bonusMonth: Int? = null,
) {
    /** The subsidy IRS rate actually used: the explicit one or the base rate. */
    val effectiveIrsSubsidiesPpm: Int get() = irsSubsidiesPpm ?: irsBasePpm
}
