package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

private const val MONTHS_IN_YEAR = 12

/**
 * The extra days off assumed in each month of [year] with no vacation days recorded (sick and other days do not
 * count): the [vacationDays] not already recorded as vacation in the year's other months, spread evenly over those
 * free months
 * (earlier months take the remainder) and never above a month's working days. Months not in the map assume none.
 */
fun assumedVacationByMonth(
    year: Int,
    settings: List<MonthSettings>,
    vacationDays: Int,
): Map<YearMonth, Int> {
    val byMonth = settings.associateBy { it.month }
    val months = (1..MONTHS_IN_YEAR).map { YearMonth.of(year, it) }
    val recorded = months.sumOf { byMonth[it]?.vacationDays ?: 0 }
    val free = months.filter { (byMonth[it]?.vacationDays ?: 0) == 0 }
    val remaining = maxOf(0, vacationDays - recorded)
    if (free.isEmpty() || remaining == 0) return emptyMap()
    val each = remaining / free.size
    val extra = remaining % free.size
    return free
        .mapIndexed { index, month -> month to minOf(each + if (index < extra) 1 else 0, workingDaysIn(month)) }
        .filter { it.second > 0 }
        .toMap()
}

/** What [salaryYearEstimateOf] adds up: the planned income [lines], the months' [settings] and the assumed vacation. */
data class YearEstimateInput(
    val lines: List<PlannedIncome>,
    val settings: List<MonthSettings>,
    val vacationDays: Int = DEFAULT_VACATION_DAYS,
)

/**
 * The year summary of [profile] for [year] worked out month by month from the planned income [lines] the profile
 * produces (so it honours "valid from", the start month, the amount history, the days off already set and the
 * assumed [vacationDays] in months with none), the same way the Income Year view adds the months up. The meal
 * allowance, the monthly net, the lumps and the extras come from the lines; the tax figures stay the typical-year
 * ones of [salaryYearSummaryOf]. It is an estimate: the vacation days are an assumption.
 */
fun salaryYearEstimateOf(
    profile: SalaryProfile,
    breakdown: SalaryBreakdown,
    year: Int,
    input: YearEstimateInput,
): SalaryYearSummary {
    val (lines, settings, vacationDays) = input
    val assumed = assumedVacationByMonth(year, settings, vacationDays)
    val byMonth = settings.associateBy { it.month }
    val income =
        IncomeMonth { month ->
            val current = byMonth[month]
            effectiveWorkingDays(month, (current?.daysOff ?: 0) + (assumed[month] ?: 0))
        }
    val months = (1..MONTHS_IN_YEAR).map { YearMonth.of(year, it) }
    val managed = lines.filter { it.isManaged }

    fun yearOf(line: PlannedIncome) = months.map { it to income.amountIn(line, it) }.filter { it.second > 0L }

    val monthly = managed.filter { it.managedKey in MONTHLY_KEYS }.sumOf { line -> yearOf(line).sumOf { it.second } }
    val meal = managed.filter { it.managedKey == MANAGED_MEAL }.sumOf { line -> yearOf(line).sumOf { it.second } }
    val lumps =
        listOf(YearPaymentKind.HOLIDAY to MANAGED_HOLIDAY, YearPaymentKind.CHRISTMAS to MANAGED_CHRISTMAS)
            .mapNotNull { (kind, key) ->
                managed.firstOrNull { it.managedKey == key }?.let { paymentOf(kind, "", yearOf(it)) }
            }
    val extras =
        managed
            .filter { it.salaryComponent == SalaryComponent.EXTRA }
            .groupBy { it.salaryExtraId }
            .values
            .mapNotNull { group ->
                paymentOf(YearPaymentKind.EXTRA, group.first().label, group.flatMap(::yearOf).sortedBy { it.first })
            }
    val payments = lumps + extras
    return salaryYearSummaryOf(profile, breakdown, year).copy(
        payments = payments,
        mealYearCents = meal,
        netYearCents = monthly + meal + payments.sumOf { it.totalCents },
    )
}

private val MONTHLY_KEYS = setOf(MANAGED_NET_SALARY, MANAGED_DUODECIMOS)

private fun paymentOf(
    kind: YearPaymentKind,
    name: String,
    paid: List<Pair<YearMonth, Long>>,
): YearPayment? =
    paid.takeIf { it.isNotEmpty() }?.let { months ->
        YearPayment(
            kind = kind,
            name = name,
            months = months.map { it.first.monthValue },
            eachCents = months.first().second,
            totalCents = months.sumOf { it.second },
        )
    }
