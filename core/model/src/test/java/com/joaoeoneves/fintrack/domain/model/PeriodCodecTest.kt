package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Pure tests for [encode]/[decodePeriod]: exact tokens, round-trips and lenient decoding. */
class PeriodCodecTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")

    private fun decode(token: String?) = decodePeriod(token, now, zone)

    @Test
    fun encode_producesDocumentedTokens() {
        assertEquals("current", Period.CurrentMonth.encode())
        assertEquals("month:2026-09", Period.Month(YearMonth.of(2026, 9)).encode())
        assertEquals("rolling:THREE_MONTHS", Period.Rolling(TimeRange.THREE_MONTHS).encode())
        assertEquals(
            "custom:2026-10-01:2026-10-15",
            Period.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15)).encode(),
        )
    }

    @Test
    fun roundTrip_everyVariant() {
        val periods =
            listOf(
                Period.CurrentMonth,
                Period.Month(YearMonth.of(2026, 9)),
                Period.Month(YearMonth.of(1999, 12)),
                Period.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15)),
                Period.Custom(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5)),
                Period.Custom(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1)),
            ) + TimeRange.entries.map { Period.Rolling(it) }

        for (period in periods) {
            assertEquals(period.encode(), period, decode(period.encode()))
        }
    }

    @Test
    fun decode_nullOrBlank_isCurrentMonth() {
        assertEquals(Period.CurrentMonth, decode(null))
        assertEquals(Period.CurrentMonth, decode(""))
    }

    @Test
    fun decode_garbage_isCurrentMonth() {
        val bad =
            listOf(
                "garbage",
                "CURRENT",
                "month",
                "month:",
                "month:2026-13",
                "month:not-a-month",
                "month:2026-09:extra",
                "rolling",
                "rolling:",
                "rolling:three_months",
                "custom:2026-10-01",
                "custom:2026-10-01:nope",
                "custom:2026-10-01:2026-10-15:2026-10-20",
                "custom:2026-02-30:2026-03-01",
            )
        for (token in bad) {
            assertEquals(token, Period.CurrentMonth, decode(token))
        }
    }

    @Test
    fun decode_removedRollingRanges_fallBackToCurrentMonth() {
        // ONE_WEEK/ONE_MONTH used to exist; a token stored by an older build must not crash.
        assertEquals(Period.CurrentMonth, decode("rolling:ONE_WEEK"))
        assertEquals(Period.CurrentMonth, decode("rolling:ONE_MONTH"))
    }

    @Test
    fun decode_monthThatIsNowCurrent_normalizesToCurrentMonth() {
        assertEquals(Period.CurrentMonth, decode("month:2026-10"))
    }

    @Test
    fun decode_futureMonth_isCurrentMonth() {
        assertEquals(Period.CurrentMonth, decode("month:2026-11"))
        assertEquals(Period.CurrentMonth, decode("month:2099-01"))
    }

    @Test
    fun decode_storedPastMonth_becomesCurrentMonth_onceCalendarReachesIt() {
        val stored = Period.Month(YearMonth.of(2026, 10)).encode()

        assertEquals(Period.CurrentMonth, decodePeriod(stored, now, zone))
        val november = Instant.parse("2026-11-01T00:00:00Z")
        assertEquals(Period.Month(YearMonth.of(2026, 10)), decodePeriod(stored, november, zone))
    }

    @Test
    fun decode_reversedCustomRange_isSwapped() {
        assertEquals(
            Period.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15)),
            decode("custom:2026-10-15:2026-10-01"),
        )
    }

    @Test
    fun decode_current_staysCurrentMonth_acrossRollover() {
        assertEquals(Period.CurrentMonth, decodePeriod("current", Instant.parse("2027-01-01T00:00:00Z"), zone))
    }
}
