package com.joaoeoneves.fintrack.domain.model

import java.time.Instant
import java.time.ZoneId

// Month counts backing the THREE_MONTHS/SIX_MONTHS range options below.
private const val THREE_MONTHS_COUNT = 3L
private const val SIX_MONTHS_COUNT = 6L

/**
 * A rolling time-range filter, always ending "today" (inclusive) in a given time zone. Wrapped by
 * [Period.Rolling]; calendar months are the primary period and are modelled by [Period] directly.
 */
enum class TimeRange {
    THREE_MONTHS,
    SIX_MONTHS,
    ONE_YEAR,
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
                THREE_MONTHS -> today.minusMonths(THREE_MONTHS_COUNT)
                SIX_MONTHS -> today.minusMonths(SIX_MONTHS_COUNT)
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
