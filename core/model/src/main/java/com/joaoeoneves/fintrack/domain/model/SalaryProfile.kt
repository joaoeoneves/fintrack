package com.joaoeoneves.fintrack.domain.model

/** Whether the salary profile is typed as a gross salary with tax rates or as the net amounts received. */
enum class SalaryInputMode { GROSS, NET }

/** Highest value of a rate in parts per million (100%). */
const val PPM_SCALE = 1_000_000

/** The three "duodecimos" choices as a share of the two subsidies spread over the year (ppm). */
const val SPREAD_NONE_PPM = 0
const val SPREAD_HALF_PPM = 500_000
const val SPREAD_FULL_PPM = PPM_SCALE

/** The valid [SalaryProfile.subsidySpreadPpm] values. */
val SUBSIDY_SPREADS_PPM: List<Int> = listOf(SPREAD_NONE_PPM, SPREAD_HALF_PPM, SPREAD_FULL_PPM)

/** Default social-security rate (11%). */
const val DEFAULT_SS_PPM = 110_000

const val DEFAULT_HOLIDAY_SUBSIDY_MONTH = 6
const val DEFAULT_CHRISTMAS_SUBSIDY_MONTH = 12

/** `plannedIncome.managedKey` values of the lines generated from the salary profile. */
const val MANAGED_NET_SALARY = "profile_net_salary"
const val MANAGED_MEAL = "profile_meal"
const val MANAGED_HOLIDAY = "profile_holiday"
const val MANAGED_CHRISTMAS = "profile_christmas"
const val MANAGED_DUODECIMOS = "profile_duodecimos"

/** Legacy managed keys (before extras were user-defined); only recognised so stale lines can be cleaned up. */
const val MANAGED_PROFIT_SHARE = "profile_profit_share"
const val MANAGED_BONUS = "profile_bonus"
private const val LEGACY_FLEX_PAYMENTS = 4

/** The legacy managed key of the flexible-benefit payment [index] (0..3). */
fun managedFlexKey(index: Int): String = "profile_flex_q${index + 1}"

/** The legacy managed key of the profit-share line paid in [month] (1..12). */
fun managedProfitShareKey(month: Int): String = "${MANAGED_PROFIT_SHARE}_$month"

private val FIXED_MANAGED_KEYS =
    setOf(MANAGED_NET_SALARY, MANAGED_DUODECIMOS, MANAGED_MEAL, MANAGED_HOLIDAY, MANAGED_CHRISTMAS)

private val LEGACY_MANAGED_KEYS: Set<String> =
    setOf(MANAGED_PROFIT_SHARE, MANAGED_BONUS) +
        List(LEGACY_FLEX_PAYMENTS, ::managedFlexKey) + List(MAX_MONTH_OF_YEAR) { managedProfitShareKey(it + 1) }

/** Whether [key] is a `managedKey` the app can generate: a fixed one, a legacy one or an extra's per-month key. */
fun isValidManagedKey(key: String): Boolean {
    val known = key in FIXED_MANAGED_KEYS || key in LEGACY_MANAGED_KEYS
    return known || parseManagedExtraKey(key) != null
}

/** Default (English) labels of the generated lines; the UI passes localized ones. Existing lines keep theirs. */
data class SalaryLineLabels(
    val netSalary: String = "Net salary",
    val duodecimos: String = "Subsidies (twelfths)",
    val meal: String = "Meal allowance",
    val subsidy: String = "Subsidy",
)

/**
 * The user's salary set-up, a singleton from which the plan's income lines are generated (see
 * [salaryProfileLines]). Every rate is an integer in ppm (1_000_000 = 100%) and every amount is in cents. Only
 * the base ([baseGrossCents], GROSS mode) or [netMonthlyCents] (NET mode) is needed; everything else is optional.
 *
 * GROSS mode: [subsidySpreadPpm] is the share of the holiday and Christmas subsidies paid in twelfths with the
 * monthly salary (0, 50% or 100%); [irsBasePpm] / [irsSubsidiesPpm] (default: the base rate) are the withholding
 * rates; [irsJovemExemptPpm] is the exempt share of the IRS (IRS paid = rate x (1 - exempt)).
 * NET mode: the net amounts as received, no tax fields. [extras] are the user-defined yearly extras.
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
    /** Legacy: no longer written nor used to generate lines (the meal allowance is paid every month). */
    val mealSkipMonths: Set<Int> = emptySet(),
    val holidaySubsidyMonth: Int = DEFAULT_HOLIDAY_SUBSIDY_MONTH,
    val christmasSubsidyMonth: Int = DEFAULT_CHRISTMAS_SUBSIDY_MONTH,
    val extras: List<SalaryExtra> = emptyList(),
) {
    /** The subsidy IRS rate actually used: the explicit one or the base rate. */
    val effectiveIrsSubsidiesPpm: Int get() = irsSubsidiesPpm ?: irsBasePpm
}
