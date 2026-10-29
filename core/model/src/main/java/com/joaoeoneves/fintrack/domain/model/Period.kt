package com.joaoeoneves.fintrack.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The period the dashboard and the expense/income lists are filtered by. Calendar months are the
 * primary period ([CurrentMonth] and past [Month]s, navigated with previous/next arrows); rolling
 * windows and custom date ranges are secondary choices.
 *
 * [CurrentMonth] is kept distinct from `Month(<this month>)` so a stored selection keeps meaning
 * "this month" after the calendar rolls over, instead of silently pinning the old month.
 */
sealed interface Period {
    /** The calendar month containing "now", re-resolved every time it is evaluated. */
    data object CurrentMonth : Period

    /** A specific, always past, calendar month. Build via [Period.ofMonth] to keep that invariant. */
    data class Month(
        val yearMonth: YearMonth,
    ) : Period

    /** A whole calendar year (January to December), see [previousYear] / [nextYear]. */
    data class Year(
        val year: Int,
    ) : Period

    /** A rolling window ending today, see [TimeRange]. */
    data class Rolling(
        val range: TimeRange,
    ) : Period

    /** An arbitrary date range, both ends inclusive. */
    data class Custom(
        val start: LocalDate,
        val endInclusive: LocalDate,
    ) : Period

    companion object {
        /**
         * Maps [yearMonth] to a month period: the current month (relative to [now] in [zone]) and
         * any future month become [CurrentMonth]; past months become [Month].
         */
        fun ofMonth(
            yearMonth: YearMonth,
            now: Instant,
            zone: ZoneId,
            monthsAhead: Int = 0,
        ): Period {
            val current = currentYearMonth(now, zone)
            return when {
                yearMonth > current && yearMonth <= current.plusMonths(monthsAhead.toLong()) -> Month(yearMonth)
                yearMonth >= current -> CurrentMonth
                else -> Month(yearMonth)
            }
        }
    }
}

/** The calendar month containing [now] in [zone]. */
fun currentYearMonth(
    now: Instant,
    zone: ZoneId,
): YearMonth = YearMonth.from(now.atZone(zone))

/**
 * The `[first-of-month 00:00, first-of-next-month 00:00)` instant range for this month in [zone].
 */
fun YearMonth.toInstantRange(zone: ZoneId): InstantRange =
    InstantRange(
        startInclusive = atDay(1).atStartOfDay(zone).toInstant(),
        endExclusive = plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
    )

/**
 * The `[startInclusive, endExclusive)` instant range this period covers, relative to [now] in
 * [zone]. Months span whole calendar months, custom ranges span `[start 00:00, endInclusive+1 00:00)`
 * and rolling windows delegate to [TimeRange.toInstantRange].
 */
fun Period.toInstantRange(
    now: Instant,
    zone: ZoneId,
): InstantRange =
    when (this) {
        Period.CurrentMonth -> currentYearMonth(now, zone).toInstantRange(zone)
        is Period.Month -> yearMonth.toInstantRange(zone)
        is Period.Year ->
            InstantRange(
                startInclusive = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant(),
                endExclusive = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant(),
            )
        is Period.Rolling -> range.toInstantRange(now, zone)
        is Period.Custom ->
            InstantRange(
                startInclusive = start.atStartOfDay(zone).toInstant(),
                endExclusive = endInclusive.plusDays(1).atStartOfDay(zone).toInstant(),
            )
    }

/** The calendar month a month period represents, or `null` for rolling/custom periods. */
fun Period.anchorMonth(
    now: Instant,
    zone: ZoneId,
): YearMonth? =
    when (this) {
        Period.CurrentMonth -> currentYearMonth(now, zone)
        is Period.Month -> yearMonth
        is Period.Year, is Period.Rolling, is Period.Custom -> null
    }

/** The calendar year a [Period.Year] represents, `null` for any other period. */
val Period.yearOrNull: Int? get() = (this as? Period.Year)?.year

/** The year before this [Period.Year]; any other period is returned unchanged. */
fun Period.previousYear(): Period = if (this is Period.Year) Period.Year(year - 1) else this

/** The year after this [Period.Year], never past the current year; any other period is returned unchanged. */
fun Period.nextYear(
    now: Instant,
    zone: ZoneId,
): Period = if (this is Period.Year && year < currentYearMonth(now, zone).year) Period.Year(year + 1) else this

/** Whether [nextYear] would move forward: a [Period.Year] strictly before the current year. */
fun Period.canGoToNextYear(
    now: Instant,
    zone: ZoneId,
): Boolean = this is Period.Year && year < currentYearMonth(now, zone).year

/** The month before this month period. Rolling/custom periods are returned unchanged. */
fun Period.previousMonth(
    now: Instant,
    zone: ZoneId,
): Period = anchorMonth(now, zone)?.let { Period.Month(it.minusMonths(1)) } ?: this

/**
 * The month after this month period, normalized so reaching the current month yields
 * [Period.CurrentMonth] and never going past it. Rolling/custom periods are returned unchanged.
 */
fun Period.nextMonth(
    now: Instant,
    zone: ZoneId,
    monthsAhead: Int = 0,
): Period =
    anchorMonth(now, zone)
        ?.takeIf { it < currentYearMonth(now, zone).plusMonths(monthsAhead.toLong()) }
        ?.let { Period.ofMonth(it.plusMonths(1), now, zone, monthsAhead) }
        ?: this

/**
 * Whether [nextMonth] would move forward, i.e. this is a month strictly before the current one (or before
 * [monthsAhead] months after it, for planning screens).
 */
fun Period.canGoToNextMonth(
    now: Instant,
    zone: ZoneId,
    monthsAhead: Int = 0,
): Boolean = anchorMonth(now, zone)?.let { it < currentYearMonth(now, zone).plusMonths(monthsAhead.toLong()) } == true
