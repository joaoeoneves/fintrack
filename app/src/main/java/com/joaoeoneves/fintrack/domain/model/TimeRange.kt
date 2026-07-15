package com.joaoeoneves.fintrack.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

/**
 * A rolling time-range filter, always ending "today" (inclusive) in a given time zone.
 */
@Serializable
enum class TimeRange(
    val label: String,
) {
    ONE_WEEK("1W"),
    ONE_MONTH("1M"),
    THREE_MONTHS("3M"),
    SIX_MONTHS("6M"),
    ONE_YEAR("1Y"),
    ;

    /**
     * Computes the `[startInclusive, endExclusive)` instant range represented by this filter,
     * anchored on [now] in [zone]. "Today" is always fully included regardless of the time of day.
     */
    fun toInstantRange(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): InstantRange {
        val today = now.atZone(zone).toLocalDate()
        val startDate =
            when (this) {
                ONE_WEEK -> today.minusWeeks(1)
                ONE_MONTH -> today.minusMonths(1)
                THREE_MONTHS -> today.minusMonths(3)
                SIX_MONTHS -> today.minusMonths(6)
                ONE_YEAR -> today.minusYears(1)
            }
        val endExclusiveDate = today.plusDays(1)
        return InstantRange(
            startInclusive = startDate.atStartOfDay(zone).toInstant(),
            endExclusive = endExclusiveDate.atStartOfDay(zone).toInstant(),
        )
    }
}

data class InstantRange(
    val startInclusive: Instant,
    val endExclusive: Instant,
)
