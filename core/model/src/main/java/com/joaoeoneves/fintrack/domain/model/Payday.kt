package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Months older than this many are not caught up on payday: only the latest ones are recorded. */
const val PAYDAY_CATCH_UP_MONTHS = 3

/** How many days before payday the dashboard starts offering the expected income. */
const val PAYDAY_LEAD_DAYS = 7L

private const val PAYDAY_ID_PREFIX = "pay_"
private val PAYDAY_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM")

/** Deterministic id of the income recorded for planned line [lineId] in [month]: `pay_<lineId>_<yyyyMM>`. */
fun paydayIncomeId(
    lineId: String,
    month: YearMonth,
): String = "$PAYDAY_ID_PREFIX${lineId}_${PAYDAY_MONTH_FORMAT.format(month)}"

/** Whether [id] is the id of an income recorded from a planned line (see [paydayIncomeId]). */
fun isPaydayIncomeId(id: String): Boolean = id.startsWith(PAYDAY_ID_PREFIX)

/** The day pay is expected in [month]: its last working day (weekends and national holidays excluded). */
fun paydayOf(month: YearMonth): LocalDate = lastWorkingDayOf(month)

/**
 * The income for this line in [month], dated at noon of [date] in [zone] so the local day is stable across zones;
 * `null` when it pays nothing that month. Works for any line, managed or manual; the source is the line's label.
 */
fun PlannedIncome.paydayIncome(
    month: YearMonth,
    workingDays: Int,
    date: LocalDate,
    zone: ZoneId,
    amountCents: Long = amountIn(month, workingDays),
): Income? =
    if (amountCents <= 0L) {
        null
    } else {
        Income(
            id = paydayIncomeId(id, month),
            source = label,
            amountCents = amountCents,
            date = date.atTime(LocalTime.NOON).atZone(zone).toInstant(),
            note = null,
        )
    }

/**
 * The months whose payday has come and gone since [recordedThrough] (exclusive), oldest first, at most the last
 * [cap]. Empty while the cursor is `null` (the automation was never switched on).
 */
fun paydayMonthsDue(
    recordedThrough: YearMonth?,
    today: LocalDate,
    cap: Int = PAYDAY_CATCH_UP_MONTHS,
): List<YearMonth> {
    if (recordedThrough == null) return emptyList()
    val current = YearMonth.from(today)
    return generateSequence(recordedThrough.plusMonths(1)) { it.plusMonths(1) }
        .takeWhile { it <= current }
        .filter { paydayOf(it) <= today }
        .toList()
        .takeLast(cap)
}

/** The cursor to start from when the automation is switched on [today]: this month if payday is over, else last. */
fun initialPaydayCursor(today: LocalDate): YearMonth {
    val current = YearMonth.from(today)
    return if (paydayOf(current) <= today) current else current.minusMonths(1)
}

/**
 * The payday incomes already recorded for every paying line of this row in [month], or `null` when the row pays
 * nothing or any of its lines is missing one (a partly received row counts as not received).
 */
fun IncomeRow.receivedIncomes(
    month: YearMonth,
    workingDays: Int,
    incomes: Collection<Income>,
): List<Income>? {
    val byId = incomes.associateBy { it.id }
    val paying = lines.filter { it.amountIn(month, workingDays) > 0L }
    val found = paying.mapNotNull { byId[paydayIncomeId(it.id, month)] }
    return found.takeIf { paying.isNotEmpty() && found.size == paying.size }
}

/** The day the row was received on (the latest of its incomes' local days), or `null` when not received. */
fun IncomeRow.receivedOn(
    month: YearMonth,
    workingDays: Int,
    incomes: Collection<Income>,
    zone: ZoneId,
): LocalDate? = receivedIncomes(month, workingDays, incomes)?.maxOf { it.date }?.atZone(zone)?.toLocalDate()

/**
 * The expected income the dashboard offers: the [rows] of [month] not yet received, shown from [PAYDAY_LEAD_DAYS]
 * before [payday] to the end of the month. [workingDays] is the month's effective count.
 */
data class ExpectedIncome(
    val month: YearMonth,
    val workingDays: Int,
    val payday: LocalDate,
    val rows: List<IncomeRow>,
)
