package ptech.joaoe.agenticusage.domain.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * A spending limit, in cents, for a single [ExpenseCategory]. Always evaluated against the
 * current calendar month, independent of the dashboard's rolling [TimeRange] filter.
 */
data class Budget(
    val category: ExpenseCategory,
    val limitCents: Long,
)

/**
 * Computes the `[startInclusive, endExclusive)` instant range for the calendar month containing
 * [now] in [zone], i.e. `[first-of-month 00:00, first-of-next-month 00:00)`.
 */
fun currentCalendarMonthRange(
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): InstantRange {
    val yearMonth = YearMonth.from(now.atZone(zone))
    val startDate = yearMonth.atDay(1)
    val endExclusiveDate = yearMonth.plusMonths(1).atDay(1)
    return InstantRange(
        startInclusive = startDate.atStartOfDay(zone).toInstant(),
        endExclusive = endExclusiveDate.atStartOfDay(zone).toInstant(),
    )
}
