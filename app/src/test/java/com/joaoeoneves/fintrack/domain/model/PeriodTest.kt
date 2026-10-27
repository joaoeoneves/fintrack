package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Pure tests for [Period]'s instant ranges, month navigation and [Period.ofMonth] normalization,
 * plus [YearMonth.toInstantRange] (which replaced `currentCalendarMonthRange`). Everything is
 * anchored on a fixed [now] (2026-10-20 12:00 UTC) and an explicit zone, never the system clock.
 */
class PeriodTest {
    private val utc: ZoneId = ZoneId.of("UTC")
    private val lisbon: ZoneId = ZoneId.of("Europe/Lisbon")
    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")
    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    private val october: YearMonth = YearMonth.of(2026, 10)
    private val september: YearMonth = YearMonth.of(2026, 9)

    private fun utcMidnight(dateIso: String): Instant = Instant.parse("${dateIso}T00:00:00Z")

    // ---- YearMonth.toInstantRange (folded in from the old currentCalendarMonthRange tests) ----

    @Test
    fun yearMonthRange_isFirstOfMonthThroughFirstOfNextMonth() {
        val range = YearMonth.of(2026, 7).toInstantRange(utc)

        assertEquals(utcMidnight("2026-07-01"), range.startInclusive)
        assertEquals(utcMidnight("2026-08-01"), range.endExclusive)
    }

    @Test
    fun yearMonthRange_lastInstantOfMonthIsInside_firstOfNextMonthIsExcluded() {
        val range = YearMonth.of(2026, 7).toInstantRange(utc)
        val lastInstant = Instant.parse("2026-07-31T23:59:59.999Z")

        assertTrue(lastInstant >= range.startInclusive && lastInstant < range.endExclusive)
        assertFalse(utcMidnight("2026-08-01") < range.endExclusive)
    }

    @Test
    fun yearMonthRange_februaryNonLeap_endsAfterDay28() {
        assertEquals(utcMidnight("2026-03-01"), YearMonth.of(2026, 2).toInstantRange(utc).endExclusive)
    }

    @Test
    fun yearMonthRange_februaryLeap_endsAfterDay29() {
        val range = YearMonth.of(2028, 2).toInstantRange(utc)

        assertEquals(utcMidnight("2028-02-01"), range.startInclusive)
        assertEquals(utcMidnight("2028-03-01"), range.endExclusive)
        assertEquals(Duration.ofDays(29), Duration.between(range.startInclusive, range.endExclusive))
    }

    @Test
    fun yearMonthRange_december_rollsIntoJanuaryOfNextYear() {
        val range = YearMonth.of(2026, 12).toInstantRange(utc)

        assertEquals(utcMidnight("2026-12-01"), range.startInclusive)
        assertEquals(utcMidnight("2027-01-01"), range.endExclusive)
    }

    @Test
    fun yearMonthRange_inTokyo_isExpressedAsShiftedUtcInstants() {
        val range = YearMonth.of(2026, 7).toInstantRange(tokyo)

        assertEquals(Instant.parse("2026-06-30T15:00:00Z"), range.startInclusive)
        assertEquals(Instant.parse("2026-07-31T15:00:00Z"), range.endExclusive)
    }

    @Test
    fun yearMonthRange_lisbonMarch_springForward_isOneHourShorterThan31Days() {
        // DST starts 2026-03-29 in Lisbon: March 1 00:00 is UTC+0, April 1 00:00 is UTC+1.
        val range = YearMonth.of(2026, 3).toInstantRange(lisbon)

        assertEquals(Instant.parse("2026-03-01T00:00:00Z"), range.startInclusive)
        assertEquals(Instant.parse("2026-03-31T23:00:00Z"), range.endExclusive)
        assertEquals(Duration.ofDays(31).minusHours(1), Duration.between(range.startInclusive, range.endExclusive))
    }

    @Test
    fun yearMonthRange_lisbonOctober_fallBack_isOneHourLongerThan31Days() {
        // DST ends 2026-10-25 in Lisbon: October 1 00:00 is UTC+1, November 1 00:00 is UTC+0.
        val range = YearMonth.of(2026, 10).toInstantRange(lisbon)

        assertEquals(Instant.parse("2026-09-30T23:00:00Z"), range.startInclusive)
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), range.endExclusive)
        assertEquals(Duration.ofDays(31).plusHours(1), Duration.between(range.startInclusive, range.endExclusive))
    }

    // ---- Period.toInstantRange ----

    @Test
    fun currentMonth_coversTheCalendarMonthContainingNow() {
        val range = Period.CurrentMonth.toInstantRange(now, utc)

        assertEquals(utcMidnight("2026-10-01"), range.startInclusive)
        assertEquals(utcMidnight("2026-11-01"), range.endExclusive)
    }

    @Test
    fun currentMonth_resolvesToNewMonth_onceNowPassesMonthEnd() {
        val lastSecond = Period.CurrentMonth.toInstantRange(Instant.parse("2026-10-31T23:59:59Z"), utc)
        val nextMonth = Period.CurrentMonth.toInstantRange(utcMidnight("2026-11-01"), utc)

        assertEquals(october.toInstantRange(utc), lastSecond)
        assertEquals(YearMonth.of(2026, 11).toInstantRange(utc), nextMonth)
    }

    @Test
    fun currentMonth_dependsOnZone_sameInstantIsNextMonthInTokyo() {
        val instant = Instant.parse("2026-10-31T15:00:00Z") // 2026-11-01 00:00 JST

        assertEquals(october, Period.CurrentMonth.anchorMonth(instant, utc))
        assertEquals(YearMonth.of(2026, 11), Period.CurrentMonth.anchorMonth(instant, tokyo))
    }

    @Test
    fun currentMonth_decemberToJanuary_rollsYear() {
        val range = Period.CurrentMonth.toInstantRange(Instant.parse("2026-12-31T23:59:59Z"), utc)

        assertEquals(utcMidnight("2027-01-01"), range.endExclusive)
        assertEquals(
            YearMonth.of(2027, 1).toInstantRange(utc),
            Period.CurrentMonth.toInstantRange(utcMidnight("2027-01-01"), utc),
        )
    }

    @Test
    fun month_rangeIgnoresNow() {
        val range = Period.Month(september).toInstantRange(now, utc)

        assertEquals(utcMidnight("2026-09-01"), range.startInclusive)
        assertEquals(utcMidnight("2026-10-01"), range.endExclusive)
    }

    @Test
    fun custom_endIsInclusive_rangeEndsAtStartOfFollowingDay() {
        val range = Period.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15)).toInstantRange(now, utc)

        assertEquals(utcMidnight("2026-10-01"), range.startInclusive)
        assertEquals(utcMidnight("2026-10-16"), range.endExclusive)
    }

    @Test
    fun custom_singleDay_coversExactlyThatDay() {
        val day = LocalDate.of(2026, 10, 5)
        val range = Period.Custom(day, day).toInstantRange(now, utc)

        assertEquals(utcMidnight("2026-10-05"), range.startInclusive)
        assertEquals(utcMidnight("2026-10-06"), range.endExclusive)
    }

    @Test
    fun custom_acrossLisbonFallBack_usesLocalMidnights() {
        val range =
            Period.Custom(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25)).toInstantRange(now, lisbon)

        assertEquals(Instant.parse("2026-10-23T23:00:00Z"), range.startInclusive) // WEST, UTC+1
        assertEquals(Instant.parse("2026-10-26T00:00:00Z"), range.endExclusive) // WET, UTC+0
    }

    @Test
    fun rolling_delegatesToTimeRange_forEveryRange() {
        for (range in TimeRange.entries) {
            assertEquals(range.toInstantRange(now, lisbon), Period.Rolling(range).toInstantRange(now, lisbon))
        }
    }

    // ---- anchorMonth ----

    @Test
    fun anchorMonth_monthPeriodsHaveOne_rollingAndCustomDoNot() {
        assertEquals(october, Period.CurrentMonth.anchorMonth(now, utc))
        assertEquals(september, Period.Month(september).anchorMonth(now, utc))
        assertNull(Period.Rolling(TimeRange.ONE_YEAR).anchorMonth(now, utc))
        assertNull(Period.Custom(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)).anchorMonth(now, utc))
    }

    @Test
    fun currentYearMonth_isMonthContainingNow() {
        assertEquals(october, currentYearMonth(now, utc))
    }

    // ---- previous / next / canGoToNextMonth ----

    @Test
    fun previousMonth_fromCurrentMonth_isLastMonth() {
        assertEquals(Period.Month(september), Period.CurrentMonth.previousMonth(now, utc))
    }

    @Test
    fun previousMonth_fromJanuary_isDecemberOfPreviousYear() {
        assertEquals(
            Period.Month(YearMonth.of(2025, 12)),
            Period.Month(YearMonth.of(2026, 1)).previousMonth(now, utc),
        )
    }

    @Test
    fun nextMonth_fromLastMonth_isCurrentMonth_notMonthOfThisMonth() {
        assertEquals(Period.CurrentMonth, Period.Month(september).nextMonth(now, utc))
    }

    @Test
    fun nextMonth_fromOlderMonth_isFollowingPastMonth() {
        assertEquals(Period.Month(september), Period.Month(YearMonth.of(2026, 8)).nextMonth(now, utc))
        assertEquals(Period.Month(YearMonth.of(2026, 1)), Period.Month(YearMonth.of(2025, 12)).nextMonth(now, utc))
    }

    @Test
    fun nextMonth_fromCurrentMonth_staysCurrentMonth() {
        assertEquals(Period.CurrentMonth, Period.CurrentMonth.nextMonth(now, utc))
    }

    @Test
    fun previousAndNext_onRollingAndCustom_returnReceiverUnchanged() {
        val rolling = Period.Rolling(TimeRange.SIX_MONTHS)
        val custom = Period.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15))

        assertEquals(rolling, rolling.previousMonth(now, utc))
        assertEquals(rolling, rolling.nextMonth(now, utc))
        assertEquals(custom, custom.previousMonth(now, utc))
        assertEquals(custom, custom.nextMonth(now, utc))
    }

    @Test
    fun canGoToNextMonth_onlyForPastMonths() {
        assertTrue(Period.Month(september).canGoToNextMonth(now, utc))
        assertTrue(Period.Month(YearMonth.of(2020, 1)).canGoToNextMonth(now, utc))
        assertFalse(Period.CurrentMonth.canGoToNextMonth(now, utc))
        assertFalse(Period.Rolling(TimeRange.THREE_MONTHS).canGoToNextMonth(now, utc))
        assertFalse(Period.Custom(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)).canGoToNextMonth(now, utc))
    }

    @Test
    fun canGoToNextMonth_falseForUnnormalizedMonthOfCurrentOrFutureMonth() {
        assertFalse(Period.Month(october).canGoToNextMonth(now, utc))
        assertFalse(Period.Month(YearMonth.of(2027, 1)).canGoToNextMonth(now, utc))
    }

    @Test
    fun repeatedNext_fromAYearAgo_endsAtCurrentMonthAndStops() {
        var period: Period = Period.Month(YearMonth.of(2025, 10))
        repeat(12) { period = period.nextMonth(now, utc) }

        assertEquals(Period.CurrentMonth, period)
        assertEquals(Period.CurrentMonth, period.nextMonth(now, utc))
    }

    // ---- ofMonth ----

    @Test
    fun ofMonth_pastMonth_isMonth() {
        assertEquals(Period.Month(september), Period.ofMonth(september, now, utc))
    }

    @Test
    fun ofMonth_currentMonth_normalizesToCurrentMonth() {
        assertEquals(Period.CurrentMonth, Period.ofMonth(october, now, utc))
    }

    @Test
    fun ofMonth_futureMonth_isClampedToCurrentMonth() {
        assertEquals(Period.CurrentMonth, Period.ofMonth(YearMonth.of(2026, 11), now, utc))
        assertEquals(Period.CurrentMonth, Period.ofMonth(YearMonth.of(2030, 1), now, utc))
    }

    @Test
    fun ofMonth_respectsZone_atMonthBoundary() {
        val instant = Instant.parse("2026-10-31T15:00:00Z") // still October in UTC, November in Tokyo

        assertEquals(Period.CurrentMonth, Period.ofMonth(october, instant, utc))
        assertEquals(Period.Month(october), Period.ofMonth(october, instant, tokyo))
    }
}
