package ptech.joaoe.agenticusage.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for [currentCalendarMonthRange], a pure function. Mirrors [TimeRangeTest]'s
 * conventions: fixed [now] values deliberately *not* at midnight (to prove the boundary is
 * computed from the calendar date, not the instant itself), a fixed `ZoneId.of("UTC")` for
 * determinism, and explicit month/year-boundary and leap-year cases.
 */
class CurrentCalendarMonthRangeTest {
    private val zone: ZoneId = ZoneId.of("UTC")

    private fun startOfDayUtc(dateIso: String): Instant = Instant.parse("${dateIso}T00:00:00Z")

    @Test
    fun midMonth_rangeIsFirstOfMonthThroughFirstOfNextMonth() {
        // 2026-07-14 is comfortably mid-month.
        val now = Instant.parse("2026-07-14T15:30:00Z")

        val range = currentCalendarMonthRange(now = now, zone = zone)

        assertEquals(startOfDayUtc("2026-07-01"), range.startInclusive)
        assertEquals(startOfDayUtc("2026-08-01"), range.endExclusive)
    }

    @Test
    fun firstOfMonth_atMidnight_isStartInclusive() {
        val now = startOfDayUtc("2026-07-01")

        val range = currentCalendarMonthRange(now = now, zone = zone)

        assertEquals(startOfDayUtc("2026-07-01"), range.startInclusive)
        assertEquals(startOfDayUtc("2026-08-01"), range.endExclusive)
    }

    @Test
    fun firstOfMonth_justBeforeMidnightUtc_stillCountsAsThatDay() {
        // One second before midnight on July 1st is still July 1st (23:59:59 on June 30 in UTC).
        val lastMomentOfJune = Instant.parse("2026-06-30T23:59:59Z")

        val range = currentCalendarMonthRange(now = lastMomentOfJune, zone = zone)

        assertEquals(startOfDayUtc("2026-06-01"), range.startInclusive)
        assertEquals(startOfDayUtc("2026-07-01"), range.endExclusive)
    }

    @Test
    fun startOfNextMonth_atExactMidnight_isExcludedFromCurrentMonth() {
        // The instant returned as `endExclusive` for June, if used as `now`, should itself report
        // July as the current month (i.e. endExclusive is a genuinely exclusive boundary).
        val juneRange = currentCalendarMonthRange(now = Instant.parse("2026-06-15T00:00:00Z"), zone = zone)
        val julyRangeUsingJuneEndExclusiveAsNow = currentCalendarMonthRange(now = juneRange.endExclusive, zone = zone)

        assertEquals(startOfDayUtc("2026-07-01"), julyRangeUsingJuneEndExclusiveAsNow.startInclusive)
        assertEquals(startOfDayUtc("2026-08-01"), julyRangeUsingJuneEndExclusiveAsNow.endExclusive)
    }

    @Test
    fun lastInstantOfMonth_justBeforeRollover_isStillInThatMonth() {
        val justBeforeMidnight = Instant.parse("2026-07-31T23:59:59.999Z")

        val range = currentCalendarMonthRange(now = justBeforeMidnight, zone = zone)

        assertEquals(startOfDayUtc("2026-07-01"), range.startInclusive)
        assertEquals(startOfDayUtc("2026-08-01"), range.endExclusive)
    }

    // ---- month-length edge cases: 28/29/30/31-day months, including leap-year February. ----

    @Test
    fun februaryNonLeapYear_endsOnDay28() {
        val now = Instant.parse("2026-02-10T12:00:00Z")

        val range = currentCalendarMonthRange(now = now, zone = zone)

        assertEquals(startOfDayUtc("2026-02-01"), range.startInclusive)
        assertEquals(startOfDayUtc("2026-03-01"), range.endExclusive)
    }

    @Test
    fun februaryLeapYear_endsOnDay29() {
        val now = Instant.parse("2028-02-10T12:00:00Z")

        val range = currentCalendarMonthRange(now = now, zone = zone)

        assertEquals(startOfDayUtc("2028-02-01"), range.startInclusive)
        assertEquals(startOfDayUtc("2028-03-01"), range.endExclusive)
    }

    @Test
    fun december_rollsOverIntoJanuaryOfNextYear() {
        val now = Instant.parse("2026-12-25T09:00:00Z")

        val range = currentCalendarMonthRange(now = now, zone = zone)

        assertEquals(startOfDayUtc("2026-12-01"), range.startInclusive)
        assertEquals(startOfDayUtc("2027-01-01"), range.endExclusive)
    }

    // ---- zone sensitivity: the same `now` instant should produce different boundaries in a
    // different zone, since the calendar month is computed by converting `now` to a date in `zone`.

    @Test
    fun differentZone_shiftsWhichCalendarMonthIsCurrent() {
        // 2026-06-30T23:00:00Z is 2026-07-01 08:00 in Tokyo (+9) -- a different calendar month.
        val now = Instant.parse("2026-06-30T23:00:00Z")
        val tokyo = ZoneId.of("Asia/Tokyo")

        val utcRange = currentCalendarMonthRange(now = now, zone = zone)
        val tokyoRange = currentCalendarMonthRange(now = now, zone = tokyo)

        assertEquals(startOfDayUtc("2026-06-01"), utcRange.startInclusive)
        assertEquals(startOfDayUtc("2026-07-01"), utcRange.endExclusive)

        // Tokyo's "now" is already July 1st, so its month range is July, expressed as UTC instants
        // (Tokyo midnight == UTC-9h).
        assertEquals(Instant.parse("2026-06-30T15:00:00Z"), tokyoRange.startInclusive) // 2026-07-01T00:00 JST - 9h
        assertEquals(Instant.parse("2026-07-31T15:00:00Z"), tokyoRange.endExclusive) // 2026-08-01T00:00 JST - 9h
    }

    @Test
    fun defaultParameters_useSystemDefaultZoneAndCurrentInstant_doesNotThrow() {
        // Smoke test for the default-argument overload actually used in production call sites.
        val range = currentCalendarMonthRange()

        assertTrue(range.startInclusive.isBefore(range.endExclusive))
        assertTrue(!range.startInclusive.isAfter(Instant.now()))
        assertTrue(range.endExclusive.isAfter(Instant.now()))
    }
}
