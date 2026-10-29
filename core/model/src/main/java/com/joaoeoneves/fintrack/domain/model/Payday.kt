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
 * The incomes already recorded for every paying line of this row in [month], or `null` when the row pays nothing
 * or any of its lines is missing one (a partly received row counts as not received). A line migrated from a
 * recurring income also matches that item's old `rec_<itemId>_<yyyyMMdd>` incomes dated in [month].
 */
fun IncomeRow.receivedIncomes(
    month: YearMonth,
    workingDays: Int,
    incomes: Collection<Income>,
): List<Income>? {
    val byId = incomes.associateBy { it.id }
    val funding = IncomeFunding.unshifted(month, workingDays)
    val paying = lines.filter { funding.amountIn(it, month) > 0L }
    val found = paying.mapNotNull { byId[paydayIncomeId(it.id, month)] ?: legacyRecurringIncome(it, month, incomes) }
    return found.takeIf { paying.isNotEmpty() && found.size == paying.size }
}

private val LEGACY_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE
private const val LEGACY_DATE_LENGTH = 8

/** The old recurring-income income (`rec_<itemId>_<yyyyMMdd>`) of a migrated [line] dated in [month], if any. */
fun legacyRecurringIncome(
    line: PlannedIncome,
    month: YearMonth,
    incomes: Collection<Income>,
): Income? {
    val prefix = line.legacyRecurringItemId()?.let { occurrenceIdPrefix(it) } ?: return null
    return incomes.firstOrNull { income ->
        val suffix = income.id.removePrefix(prefix)
        income.id.startsWith(prefix) &&
            suffix.length == LEGACY_DATE_LENGTH &&
            runCatching { YearMonth.from(LocalDate.parse(suffix, LEGACY_DATE_FORMAT)) }.getOrNull() == month
    }
}

/** The day the row was received on (the latest of its incomes' local days), or `null` when not received. */
fun IncomeRow.receivedOn(
    month: YearMonth,
    workingDays: Int,
    incomes: Collection<Income>,
    zone: ZoneId,
): LocalDate? = receivedIncomes(month, workingDays, incomes)?.maxOf { it.date }?.atZone(zone)?.toLocalDate()

/**
 * The expected income the dashboard offers: the [rows] of [month] not yet received, each shown from
 * [PAYDAY_LEAD_DAYS] before its own pay date ([IncomeRow.payDateIn]) to the end of the month. [workingDays] is the
 * month's effective count. [payday] is only a summary: the earliest pay date among [rows] (the month's last working
 * day while there are none).
 */
data class ExpectedIncome(
    val month: YearMonth,
    val workingDays: Int,
    val payday: LocalDate,
    val rows: List<IncomeRow>,
    val entries: List<ExpectedIncomeEntry> = emptyList(),
    val funding: IncomeFunding = IncomeFunding.unshifted(month, workingDays),
)

/** One row offered on the dashboard: [row] funds [planMonth]'s plan and is paid on [payDate] (maybe last month). */
data class ExpectedIncomeEntry(
    val row: IncomeRow,
    val planMonth: YearMonth,
    val payDate: LocalDate,
)

/**
 * The payday incomes among [incomes] recorded from a generated salary line whose source is still a default label
 * of some language ([knownDefaults]) but not the one in [labels], renamed to [labels]. Anything else (manual
 * lines, extras, a source the user edited) is left alone, so the result is empty once everything is repaired.
 */
fun relabelPaydayIncomes(
    incomes: Collection<Income>,
    labels: SalaryLineLabels,
    knownDefaults: List<SalaryLineLabels>,
): List<Income> =
    incomes.mapNotNull { income ->
        val key = paydayLineId(income.id)?.takeIf { isPaydayIncomeId(income.id) }
        val wanted = key?.let { labels.labelFor(it) }
        val stale = wanted != null && income.source != wanted && knownDefaults.any { it.labelFor(key) == income.source }
        if (stale) income.copy(source = wanted) else null
    }

// `pay_<lineId>_<yyyyMM>` back to the line id.
private fun paydayLineId(id: String): String? =
    id.removePrefix(PAYDAY_ID_PREFIX).substringBeforeLast('_', missingDelimiterValue = "").takeIf { it.isNotEmpty() }
