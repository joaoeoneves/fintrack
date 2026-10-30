package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What the planned income lines pay in a month: every line counts in the month it is paid in (income is attributed
 * by its date, there is no "salary pays the next month" rule). [workingDaysOf] gives a month's effective working
 * days.
 */
class IncomeMonth(
    private val workingDaysOf: (YearMonth) -> Int,
) {
    /** What [line] pays in [month]. */
    fun amountIn(
        line: PlannedIncome,
        month: YearMonth,
    ): Long = line.asOf(month)?.amountIn(month, workingDaysOf(month)) ?: 0L

    /** The day [line] is paid in [month]. */
    fun payDateOf(
        line: PlannedIncome,
        month: YearMonth,
    ): LocalDate = line.payDateIn(month)

    /** The working days of [month] this uses. */
    fun workingDays(month: YearMonth): Int = workingDaysOf(month)

    companion object {
        /** [workingDays] for [month] only. */
        fun forMonth(
            month: YearMonth,
            workingDays: Int,
        ): IncomeMonth = IncomeMonth { if (it == month) workingDays else 0 }

        /** The effective working days of each month of [settings]. */
        fun of(settings: List<MonthSettings>): IncomeMonth =
            IncomeMonth { month ->
                val current = settings.firstOrNull { it.month == month }
                effectiveWorkingDays(month, current?.daysOff ?: 0)
            }
    }
}

/**
 * The month's income rows like [incomeRowsIn] with each line's amount from [income]. A managed line whose amount in
 * [month] is 0 (one the salary profile ended, which keeps a 0 version from then on) has no row there; a manual line
 * of 0 stays so it can still be edited.
 */
fun incomeRowsIn(
    lines: List<PlannedIncome>,
    month: YearMonth,
    income: IncomeMonth,
): List<IncomeRow> =
    rowsOf(lines.asOf(month).filterNot { it.isManaged && it.amountCents == 0L }) { component, extraId, group ->
        IncomeRow(component, group, group.sumOf { income.amountIn(it, month) }, extraId = extraId)
    }

/** The lines of this row that pay something in [month]. */
fun IncomeRow.payingLines(
    month: YearMonth,
    income: IncomeMonth,
): List<PlannedIncome> = lines.filter { income.amountIn(it, month) > 0L }

/**
 * The day this row is paid for [month]: the latest pay date of its paying lines (any line when none pays).
 * Without paying lines this is the row's own [IncomeRow.payDateIn].
 */
fun IncomeRow.payDateIn(
    month: YearMonth,
    income: IncomeMonth,
): LocalDate =
    payingLines(month, income).ifEmpty { lines }.maxOfOrNull { income.payDateOf(it, month) }
        ?: paydayOf(month)

/** The single day every paying row of [rows] is paid for [month]; `null` when they differ or none pays. */
fun commonPayDateIn(
    rows: List<IncomeRow>,
    month: YearMonth,
    income: IncomeMonth,
): LocalDate? =
    rows
        .filter { it.cents > 0L }
        .map { it.payDateIn(month, income) }
        .distinct()
        .singleOrNull()

/** The paying lines of this row for [month] keyed by the month they are paid in (always [month]). */
fun IncomeRow.payingLinesByPayMonth(
    month: YearMonth,
    income: IncomeMonth,
): Map<YearMonth, List<PlannedIncome>> {
    val paying = payingLines(month, income)
    return if (paying.isEmpty()) emptyMap() else mapOf(month to paying)
}

/**
 * The incomes already recorded for every paying line of this row for [month] (attributed with the one rule of
 * [lineOfIncome] against [allLines]), or `null` when the row pays nothing or any paying line has none.
 */
fun IncomeRow.receivedIncomes(
    month: YearMonth,
    income: IncomeMonth,
    incomes: Collection<Income>,
    allLines: List<PlannedIncome> = lines,
): List<Income>? {
    val attributed = attributeIncomes(month, incomes, allLines)
    val paying = payingLines(month, income)
    val found = paying.map { attributed[it.id].orEmpty() }
    return found.takeIf { paying.isNotEmpty() && found.all { it.isNotEmpty() } }?.flatten()
}

/** The day this row was received (the latest of its incomes' local days), or `null` when not received. */
fun IncomeRow.receivedOn(
    month: YearMonth,
    income: IncomeMonth,
    incomes: Collection<Income>,
    zone: ZoneId,
    allLines: List<PlannedIncome> = lines,
): LocalDate? = receivedIncomes(month, income, incomes, allLines)?.maxOf { it.date }?.atZone(zone)?.toLocalDate()

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
