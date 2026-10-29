@file:Suppress("TooManyFunctions") // The one place funding rules live.

package com.joaoeoneves.fintrack.domain.model

import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * First pay day of the month from which a salary-profile line pays for the NEXT month. A line paid on the last
 * working day (no fixed pay day) or on the 26th or later funds the following month's plan: the salary paid on the
 * last working day of September is what October is planned and spent on. A fixed pay day before the 26th stays in
 * its own month.
 */
const val NEXT_MONTH_PAY_DAY = 26

/**
 * Whether this line, when paid in month X, is the money for month X+1. Only the lines generated from the salary
 * profile (salary, meal allowance, subsidies) that pay every month qualify; manual lines and yearly or one-off
 * extras always belong to the month they are paid in. See [NEXT_MONTH_PAY_DAY].
 */
val PlannedIncome.paysForNextMonth: Boolean
    get() = isManaged && frequency == IncomeFrequency.MONTHLY && (payDay == null || payDay >= NEXT_MONTH_PAY_DAY)

/** Whether the salary pays for the next month: the user's choice, else on exactly when a salary profile exists. */
fun salaryShiftEnabled(
    preference: Boolean?,
    lines: List<PlannedIncome>,
): Boolean = preference ?: lines.any { it.isManaged }

/**
 * How the plan's income is attributed to months: the single place that knows about "salary pays the next month".
 * With [shift] on, a line that [paysForNextMonth] funds plan month M with what it pays in month M-1 (its amount,
 * working days, pay date and payday income all belong to M-1); every other line funds the month it pays in.
 * [workingDaysOf] gives a month's effective working days.
 */
class IncomeFunding(
    val shift: Boolean,
    private val workingDaysOf: (YearMonth) -> Int,
) {
    /** The month [line] is paid in when it funds [planMonth]. */
    fun payMonthOf(
        line: PlannedIncome,
        planMonth: YearMonth,
    ): YearMonth = if (shift && line.paysForNextMonth) planMonth.minusMonths(1) else planMonth

    /** What [line] adds to [planMonth]'s plan. */
    fun amountIn(
        line: PlannedIncome,
        planMonth: YearMonth,
    ): Long {
        val active = line.asOf(planMonth) ?: return 0L
        val pay = payMonthOf(active, planMonth)
        return active.amountIn(pay, workingDaysOf(pay))
    }

    /** The plan month [line], when paid in [payMonth], funds (the reverse of [payMonthOf]). */
    fun planMonthOf(
        line: PlannedIncome,
        payMonth: YearMonth,
    ): YearMonth = if (shift && line.paysForNextMonth) payMonth.plusMonths(1) else payMonth

    /** What [line] pays when paid in [payMonth] (the amount in force in the plan month it funds). */
    fun amountPaidIn(
        line: PlannedIncome,
        payMonth: YearMonth,
    ): Long = amountIn(line, planMonthOf(line, payMonth))

    /** The day [line] is paid for [planMonth] (in [payMonthOf]). */
    fun payDateOf(
        line: PlannedIncome,
        planMonth: YearMonth,
    ): LocalDate = line.payDateIn(payMonthOf(line, planMonth))

    /** The working days of [month] this funding uses. */
    fun workingDays(month: YearMonth): Int = workingDaysOf(month)

    companion object {
        /** No shift: every line funds the month it pays in, with [workingDays] for [month] only. */
        fun unshifted(
            month: YearMonth,
            workingDays: Int,
        ): IncomeFunding = IncomeFunding(false) { if (it == month) workingDays else 0 }

        /** A funding for the given [settings], shifted or not. */
        fun of(
            shift: Boolean,
            settings: List<MonthSettings>,
        ): IncomeFunding =
            IncomeFunding(shift) { month ->
                val current = settings.firstOrNull { it.month == month }
                effectiveWorkingDays(month, current?.workingDaysOverride, current?.daysOff ?: 0)
            }
    }
}

/** The month's income rows like [incomeRowsIn] with each line's amount attributed by [funding]. */
fun incomeRowsIn(
    lines: List<PlannedIncome>,
    month: YearMonth,
    funding: IncomeFunding,
): List<IncomeRow> =
    rowsOf(lines.asOf(month)) { component, extraId, group ->
        IncomeRow(component, group, group.sumOf { funding.amountIn(it, month) }, extraId = extraId)
    }

/** The lines of this row that pay something for [planMonth] under [funding]. */
fun IncomeRow.payingLines(
    planMonth: YearMonth,
    funding: IncomeFunding,
): List<PlannedIncome> = lines.filter { funding.amountIn(it, planMonth) > 0L }

/**
 * The day this row is paid for [planMonth]: the latest pay date of its paying lines (any line when none pays).
 * Without a shift this is [IncomeRow.payDateIn].
 */
fun IncomeRow.payDateIn(
    planMonth: YearMonth,
    funding: IncomeFunding,
): LocalDate =
    payingLines(planMonth, funding).ifEmpty { lines }.maxOfOrNull { funding.payDateOf(it, planMonth) }
        ?: paydayOf(planMonth)

/** The single day every paying row of [rows] is paid for [planMonth]; `null` when they differ or none pays. */
fun commonPayDateIn(
    rows: List<IncomeRow>,
    planMonth: YearMonth,
    funding: IncomeFunding,
): LocalDate? =
    rows
        .filter { it.cents > 0L }
        .map { it.payDateIn(planMonth, funding) }
        .distinct()
        .singleOrNull()

/**
 * The paying lines of this row for [planMonth] grouped by the month they are paid in, oldest first; what the
 * payday incomes are recorded and removed per (`pay_<lineId>_<yyyyMM>`).
 */
fun IncomeRow.payingLinesByPayMonth(
    planMonth: YearMonth,
    funding: IncomeFunding,
): Map<YearMonth, List<PlannedIncome>> {
    val byMonth = payingLines(planMonth, funding).groupBy { funding.payMonthOf(it, planMonth) }
    return byMonth.toSortedMap()
}

/**
 * The incomes already recorded for every paying line of this row for [planMonth], or `null` when the row pays
 * nothing or any paying line is missing one. Each line is looked up in its own pay month.
 */
fun IncomeRow.receivedIncomes(
    planMonth: YearMonth,
    funding: IncomeFunding,
    incomes: Collection<Income>,
): List<Income>? {
    val byId = incomes.associateBy { it.id }
    val paying = payingLines(planMonth, funding)
    val found =
        paying.mapNotNull {
            val payMonth = funding.payMonthOf(it, planMonth)
            byId[paydayIncomeId(it.id, payMonth)] ?: legacyRecurringIncome(it, payMonth, incomes)
        }
    return found.takeIf { paying.isNotEmpty() && found.size == paying.size }
}

/** The day this row was received (the latest of its incomes' local days), or `null` when not received. */
fun IncomeRow.receivedOn(
    planMonth: YearMonth,
    funding: IncomeFunding,
    incomes: Collection<Income>,
    zone: ZoneId,
): LocalDate? = receivedIncomes(planMonth, funding, incomes)?.maxOf { it.date }?.atZone(zone)?.toLocalDate()

/** The line id and pay month a payday income id (`pay_<lineId>_<yyyyMM>`) stands for; `null` for any other id. */
fun parsePaydayIncomeId(id: String): Pair<String, YearMonth>? {
    val body = id.takeIf(::isPaydayIncomeId)?.removePrefix(PAYDAY_ID_PREFIX_VALUE)
    val lineId = body?.substringBeforeLast('_', missingDelimiterValue = "")?.takeIf { it.isNotEmpty() }
    val suffix = body?.substringAfterLast('_')
    val month = suffix?.let { runCatching { YearMonth.parse(it, PAYDAY_MONTH_PARSER) }.getOrNull() }
    return if (lineId != null && month != null) lineId to month else null
}

private const val PAYDAY_ID_PREFIX_VALUE = "pay_"
private val PAYDAY_MONTH_PARSER = DateTimeFormatter.ofPattern("yyyyMM")

/**
 * The month [income] funds. With [funding] shifted, a salary paid at the end of a month funds the NEXT month:
 * - a payday income of a line that [paysForNextMonth] (`pay_<lineId>_<yyyyMM>`) funds the month after the one in
 *   its id;
 * - any other income (e.g. a manually recorded "Salário") is a salary payment when [isSalaryPayment]: its source
 *   matches (see [isSalaryPayment]: case/accent-insensitive, equal or whole-word prefix) the label of a salary-profile
 *   line that [paysForNextMonth] and it was
 *   received on the pay-day rule (the last working day of its month, or on day [NEXT_MONTH_PAY_DAY] or later in
 *   [zone]). It then funds the month after the one of its date.
 * Everything else (prizes, refunds, cashback, benefits on other days, any income of a non-salary source) funds the
 * month of its date in [zone]. Without a shift every income funds the month of its date.
 */
fun fundingMonthOf(
    income: Income,
    lines: List<PlannedIncome>,
    funding: IncomeFunding,
    zone: ZoneId,
): YearMonth {
    val dateMonth = YearMonth.from(income.date.atZone(zone))
    if (!funding.shift) return dateMonth
    val payday = parsePaydayIncomeId(income.id)
    val line = payday?.let { (lineId, _) -> lines.firstOrNull { it.id == lineId } }
    return when {
        payday != null && line != null -> if (line.paysForNextMonth) payday.second.plusMonths(1) else dateMonth
        isSalaryPayment(income, lines, zone) -> dateMonth.plusMonths(1)
        else -> dateMonth
    }
}

internal fun normalisedWords(text: String): List<String> =
    Normalizer
        .normalize(text.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }

/**
 * The line a [source] (as normalised words) belongs to: a label equal to it wins; otherwise a label the source is a
 * whole-word prefix of ("salario" of "salario liquido"). A label that is a prefix of the source never matches
 * ("salary bonus" is not "salary"). Among several qualifying labels the exact one, then the shortest, wins; a tie
 * there means no match.
 */
internal fun matchingLine(
    source: List<String>,
    lines: List<PlannedIncome>,
): PlannedIncome? {
    if (source.isEmpty()) return null
    val labelled = lines.map { it to normalisedWords(it.label) }.filter { (_, words) -> words.isNotEmpty() }
    val exact = labelled.filter { (_, words) -> words == source }
    val candidates = exact.ifEmpty { labelled.filter { (_, words) -> words.take(source.size) == source } }
    val shortest = candidates.minOfOrNull { (_, words) -> words.size }
    return candidates.filter { (_, words) -> words.size == shortest }.singleOrNull()?.first
}

/**
 * Whether [income] is a salary payment that funds the next month: its source matches the label of a salary-profile
 * line that [paysForNextMonth] (trimmed, case- and accent-insensitive, equal to the label or a whole-word prefix of
 * it: "Salário" matches "Salário líquido" but "Salary bonus" does not match "Salary"; see [matchingLine]), and it
 * was received on the last working day of its month or on
 * day [NEXT_MONTH_PAY_DAY] or later (in [zone]).
 */
fun isSalaryPayment(
    income: Income,
    lines: List<PlannedIncome>,
    zone: ZoneId,
): Boolean {
    val source = normalisedWords(income.source)
    val matches = matchingLine(source, lines)?.paysForNextMonth == true
    val date = income.date.atZone(zone).toLocalDate()
    return matches && (date.dayOfMonth >= NEXT_MONTH_PAY_DAY || date == lastWorkingDayOf(YearMonth.from(date)))
}

/**
 * When [income] counts for a period: a shifted salary counts from the first moment of the month it funds, any
 * other income at its own date. Used so period totals attribute income exactly like [fundingMonthOf].
 */
fun Income.fundingInstant(
    lines: List<PlannedIncome>,
    funding: IncomeFunding,
    zone: ZoneId,
): java.time.Instant {
    val month = fundingMonthOf(this, lines, funding, zone)
    return if (month == YearMonth.from(date.atZone(zone))) date else month.atDay(1).atStartOfDay(zone).toInstant()
}

/** The incomes among [this] that fund [month] (see [fundingMonthOf]). */
fun Collection<Income>.fundingIn(
    month: YearMonth,
    lines: List<PlannedIncome>,
    funding: IncomeFunding,
    zone: ZoneId,
): List<Income> = filter { fundingMonthOf(it, lines, funding, zone) == month }
