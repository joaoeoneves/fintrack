package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/** Whether the salary profile is typed as a gross salary with tax rates or as the net amounts received. */
enum class SalaryInputMode { GROSS, NET }

/** Where the user works; only wording and set-up presets depend on it, never the math. */
enum class EmploymentSector { PRIVATE, PUBLIC }

/** How the meal allowance is paid: loaded on a meal card, or added to the salary. */
enum class MealPayment { CARD, WITH_SALARY }

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

/** Vacation days a year assumed when the profile gives none. */
const val DEFAULT_VACATION_DAYS = 22

/** Highest valid [SalaryProfile.vacationDaysPerYear]. */
const val MAX_VACATION_DAYS = 60

/** The vacation days a year to plan with: the profile's own or [DEFAULT_VACATION_DAYS]. */
val SalaryProfile?.vacationDays: Int get() = this?.vacationDaysPerYear ?: DEFAULT_VACATION_DAYS

const val DEFAULT_HOLIDAY_SUBSIDY_MONTH = 6
const val DEFAULT_CHRISTMAS_SUBSIDY_MONTH = 12

/** `plannedIncome.managedKey` values of the lines generated from the salary profile. */
const val MANAGED_NET_SALARY = "profile_net_salary"
const val MANAGED_MEAL = "profile_meal"
const val MANAGED_HOLIDAY = "profile_holiday"
const val MANAGED_CHRISTMAS = "profile_christmas"
const val MANAGED_DUODECIMOS = "profile_duodecimos"

private val FIXED_MANAGED_KEYS =
    setOf(MANAGED_NET_SALARY, MANAGED_DUODECIMOS, MANAGED_MEAL, MANAGED_HOLIDAY, MANAGED_CHRISTMAS)

// A subsidy line whose schedule (month of year) changed: `<fixed key>_<yyyyMM it starts>`, see scheduledManagedKey.
private val SCHEDULED_KEY_PATTERN = Regex("^(${MANAGED_HOLIDAY}|${MANAGED_CHRISTMAS})_(\\d{4})(0[1-9]|1[0-2])$")

/**
 * The key of the line that carries a subsidy's new schedule from [from]: the old line keeps its own id (and the
 * incomes recorded under it) and is ended, so earlier months keep the old schedule. [baseKey] is [MANAGED_HOLIDAY]
 * or [MANAGED_CHRISTMAS].
 */
fun scheduledManagedKey(
    baseKey: String,
    from: YearMonth,
): String = "${baseKey}_%04d%02d".format(from.year, from.monthValue)

/** The fixed key a [key] stands for: a scheduled subsidy key maps to its holiday/Christmas key, any other is itself. */
fun baseManagedKey(key: String): String = SCHEDULED_KEY_PATTERN.matchEntire(key)?.groupValues?.get(1) ?: key

/** Whether [key] is a `managedKey` the app can generate: a fixed one, a scheduled subsidy or an extra's key. */
fun isValidManagedKey(key: String): Boolean =
    key in FIXED_MANAGED_KEYS || SCHEDULED_KEY_PATTERN.matches(key) || parseManagedExtraKey(key) != null

/** Default (English) labels of the generated lines; the UI passes localized ones. Existing lines keep theirs. */
data class SalaryLineLabels(
    val netSalary: String = "Net salary",
    val duodecimos: String = "Subsidies (twelfths)",
    val meal: String = "Meal allowance",
    val holidaySubsidy: String = "Holiday subsidy",
    val christmasSubsidy: String = "Christmas subsidy",
)

/**
 * The user's salary set-up, a singleton from which the plan's income lines are generated (see
 * [salaryProfileLines]). Every rate is an integer in ppm (1_000_000 = 100%) and every amount is in cents. Only
 * the base ([baseGrossCents], GROSS mode) or [netMonthlyCents] (NET mode) is needed; everything else is optional.
 *
 * GROSS mode: [subsidySpreadPpm] is the share of the holiday and Christmas subsidies paid in twelfths with the
 * monthly salary (0, 50% or 100%); [irsBasePpm] / [irsSubsidiesPpm] (default: the base rate) are the withholding
 * rates, the subsidy one for the monthly twelfths only (the lump sums use the base rate);
 * [irsJovemExemptPpm] is the exempt share of the IRS (IRS paid = rate x (1 - exempt)).
 * [otherDeductionsPpm] is a further flat rate taken from the gross like SS.
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
    /** Vacation days a year (0..[MAX_VACATION_DAYS]); `null` means [DEFAULT_VACATION_DAYS]. */
    val vacationDaysPerYear: Int? = null,
    /** Legacy: no longer written nor used to generate lines (the meal allowance is paid every month). */
    val mealSkipMonths: Set<Int> = emptySet(),
    val holidaySubsidyMonth: Int = DEFAULT_HOLIDAY_SUBSIDY_MONTH,
    val christmasSubsidyMonth: Int = DEFAULT_CHRISTMAS_SUBSIDY_MONTH,
    val extras: List<SalaryExtra> = emptyList(),
    /** `null` means [EmploymentSector.PRIVATE]. */
    val sector: EmploymentSector? = null,
    val mealPayment: MealPayment = MealPayment.CARD,
    /** ADSE, SAMS, union dues and similar, withheld like SS (monthly pay and subsidy lumps, not extras). */
    val otherDeductionsPpm: Int = 0,
    /** The yearly raise takes effect in February: a new salary's default "valid from" is then February, not now. */
    val raiseInFebruary: Boolean = false,
) {
    /** The subsidy IRS rate actually used: the explicit one or the base rate. */
    val effectiveIrsSubsidiesPpm: Int get() = irsSubsidiesPpm ?: irsBasePpm
}

/**
 * The default "valid from" month of a new salary: with [raiseInFebruary] February of [now]'s year (in January
 * that is the coming month; from February on the raise is backdated to this year's February), otherwise [now].
 */
fun defaultEffectiveFrom(
    now: YearMonth,
    raiseInFebruary: Boolean,
): YearMonth = if (raiseInFebruary) YearMonth.of(now.year, 2) else now
