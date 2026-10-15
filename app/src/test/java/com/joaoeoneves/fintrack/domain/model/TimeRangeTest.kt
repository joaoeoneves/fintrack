package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for [TimeRange.toInstantRange], a pure function. All tests anchor on a fixed
 * [now] deliberately *not* at midnight, to prove "today" is fully included in [zone] regardless
 * of the time of day, and use a fixed [ZoneId.of] "UTC" for determinism (avoiding any dependency
 * on the JVM's default zone).
 */
class TimeRangeTest {
    private val zone: ZoneId = ZoneId.of("UTC")

    // 2026-07-14 is a Tuesday; 15:30 UTC is deliberately not midnight.
    private val now = Instant.parse("2026-07-14T15:30:00Z")

    private fun startOfDayUtc(dateIso: String): Instant = Instant.parse("${dateIso}T00:00:00Z")

    // "Today" (2026-07-14) must always be fully included: endExclusive is always the start of
    // the *next* day, regardless of the time-of-day portion of `now`.
    private val expectedEndExclusive = startOfDayUtc("2026-07-15")

    @Test
    fun oneWeek_rangeIsLast7DaysThroughEndOfToday() {
        val range = TimeRange.ONE_WEEK.toInstantRange(now = now, zone = zone)

        assertEquals(startOfDayUtc("2026-07-07"), range.startInclusive)
        assertEquals(expectedEndExclusive, range.endExclusive)
    }

    @Test
    fun oneMonth_rangeIsLast1MonthThroughEndOfToday() {
        val range = TimeRange.ONE_MONTH.toInstantRange(now = now, zone = zone)

        assertEquals(startOfDayUtc("2026-06-14"), range.startInclusive)
        assertEquals(expectedEndExclusive, range.endExclusive)
    }

    @Test
    fun threeMonths_rangeIsLast3MonthsThroughEndOfToday() {
        val range = TimeRange.THREE_MONTHS.toInstantRange(now = now, zone = zone)

        assertEquals(startOfDayUtc("2026-04-14"), range.startInclusive)
        assertEquals(expectedEndExclusive, range.endExclusive)
    }

    @Test
    fun sixMonths_rangeIsLast6MonthsThroughEndOfToday() {
        val range = TimeRange.SIX_MONTHS.toInstantRange(now = now, zone = zone)

        assertEquals(startOfDayUtc("2026-01-14"), range.startInclusive)
        assertEquals(expectedEndExclusive, range.endExclusive)
    }

    @Test
    fun oneYear_rangeIsLast1YearThroughEndOfToday() {
        val range = TimeRange.ONE_YEAR.toInstantRange(now = now, zone = zone)

        assertEquals(startOfDayUtc("2025-07-14"), range.startInclusive)
        assertEquals(expectedEndExclusive, range.endExclusive)
    }

    @Test
    fun today_isFullyIncluded_regardlessOfTimeOfDay() {
        // A `now` at 23:59:59 should still yield the same endExclusive as one at 00:00:01 --
        // i.e. the *entire* current day is captured, not just up to the current instant.
        val lateInDay = Instant.parse("2026-07-14T23:59:59Z")
        val earlyInDay = Instant.parse("2026-07-14T00:00:01Z")

        val lateRange = TimeRange.ONE_WEEK.toInstantRange(now = lateInDay, zone = zone)
        val earlyRange = TimeRange.ONE_WEEK.toInstantRange(now = earlyInDay, zone = zone)

        assertEquals(expectedEndExclusive, lateRange.endExclusive)
        assertEquals(expectedEndExclusive, earlyRange.endExclusive)
        assertEquals(lateRange.startInclusive, earlyRange.startInclusive)
    }

    @Test
    fun now_exactlyAtMidnight_stillIncludesFullDay() {
        val midnight = startOfDayUtc("2026-07-14")

        val range = TimeRange.ONE_WEEK.toInstantRange(now = midnight, zone = zone)

        assertEquals(startOfDayUtc("2026-07-07"), range.startInclusive)
        assertEquals(expectedEndExclusive, range.endExclusive)
    }

    // ---- month/year boundary edge cases: java.time's minusMonths/minusYears "clamps" the day
    // of month down to the last valid day of the target month rather than overflowing into the
    // following month (e.g. Mar 31 minus 1 month -> Feb 28/29, not Mar 3). Confirm that clamped
    // behavior directly since it's easy to get wrong when reasoning about it informally.

    @Test
    fun oneMonth_fromMarch31_clampsToFebruary28_nonLeapYear() {
        // 2026 is not a leap year, so February has 28 days.
        val marchNow = Instant.parse("2026-03-31T12:00:00Z")

        val range = TimeRange.ONE_MONTH.toInstantRange(now = marchNow, zone = zone)

        assertEquals(startOfDayUtc("2026-02-28"), range.startInclusive)
        assertEquals(startOfDayUtc("2026-04-01"), range.endExclusive)
    }

    @Test
    fun oneMonth_fromMarch31_clampsToFebruary29_leapYear() {
        // 2028 is a leap year, so February has 29 days.
        val marchNow = Instant.parse("2028-03-31T12:00:00Z")

        val range = TimeRange.ONE_MONTH.toInstantRange(now = marchNow, zone = zone)

        assertEquals(startOfDayUtc("2028-02-29"), range.startInclusive)
        assertEquals(startOfDayUtc("2028-04-01"), range.endExclusive)
    }

    @Test
    fun oneYear_fromFeb29LeapDay_clampsToFeb28NextYear() {
        val leapDayNow = Instant.parse("2028-02-29T09:00:00Z")

        val range = TimeRange.ONE_YEAR.toInstantRange(now = leapDayNow, zone = zone)

        assertEquals(startOfDayUtc("2027-02-28"), range.startInclusive)
        assertEquals(startOfDayUtc("2028-03-01"), range.endExclusive)
    }

    @Test
    fun sixMonths_acrossYearBoundary() {
        val janNow = Instant.parse("2026-01-15T08:00:00Z")

        val range = TimeRange.SIX_MONTHS.toInstantRange(now = janNow, zone = zone)

        assertEquals(startOfDayUtc("2025-07-15"), range.startInclusive)
        assertEquals(startOfDayUtc("2026-01-16"), range.endExclusive)
    }

    // ---- zone sensitivity: the same `now` instant should produce different boundaries in a
    // different zone, since "today" is computed by converting `now` to a LocalDate in `zone`.

    @Test
    fun differentZone_shiftsTodayAndThereforeTheWholeRange() {
        // 2026-07-14T15:30:00Z is 2026-07-15 01:30 in Tokyo (+9), i.e. a different calendar day.
        val tokyo = ZoneId.of("Asia/Tokyo")

        val utcRange = TimeRange.ONE_WEEK.toInstantRange(now = now, zone = zone)
        val tokyoRange = TimeRange.ONE_WEEK.toInstantRange(now = now, zone = tokyo)

        assertEquals(
            Instant.parse("2026-07-07T00:00:00Z"),
            utcRange.startInclusive,
        )
        // Tokyo's "today" is 2026-07-15, so its start-of-day boundaries are shifted by one day
        // relative to UTC, expressed as UTC instants (Tokyo midnight == UTC-9h).
        assertEquals(
            Instant.parse("2026-07-07T15:00:00Z"), // 2026-07-08T00:00 JST - 9h
            tokyoRange.startInclusive,
        )
        assertEquals(
            Instant.parse("2026-07-15T15:00:00Z"), // 2026-07-16T00:00 JST - 9h
            tokyoRange.endExclusive,
        )
    }

    @Test
    fun defaultParameters_useSystemDefaultZoneAndCurrentInstant_doesNotThrow() {
        // Smoke test for the default-argument overload actually used in production call sites.
        val range = TimeRange.ONE_MONTH.toInstantRange()

        assertTrue(range.startInclusive.isBefore(range.endExclusive))
    }
}
