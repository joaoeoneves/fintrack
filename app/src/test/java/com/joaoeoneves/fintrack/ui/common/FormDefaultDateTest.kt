package com.joaoeoneves.fintrack.ui.common

import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.model.encode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/** The period argument of the add forms and the default date of a new transaction. */
class FormDefaultDateTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-28T09:30:00Z")

    private fun pastMonth(month: YearMonth) = Period.ofMonth(month, now, zone)

    @Test
    fun newTransactionArg_isNullForCurrentMonthAndNonMonthPeriods() {
        assertNull(Period.CurrentMonth.newTransactionArg())
        assertNull(Period.Rolling(TimeRange.entries.first()).newTransactionArg())
        assertNull(Period.Custom(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)).newTransactionArg())
    }

    @Test
    fun newTransactionArg_forAPastMonth_isItsEncodedToken() {
        val period = pastMonth(YearMonth.of(2026, 8))

        assertEquals("month:2026-08", period.newTransactionArg())
        assertEquals(period.encode(), period.newTransactionArg())
    }

    @Test
    fun monthNormalizedToCurrent_carriesNoArgument() {
        assertNull(pastMonth(YearMonth.of(2026, 10)).newTransactionArg())
    }

    @Test
    fun default_withoutPeriod_isNow() {
        assertEquals(now, defaultNewTransactionDate(null, now, zone))
    }

    @Test
    fun default_pastMonth_isItsLastDayAtNoon() {
        assertEquals(
            Instant.parse("2026-08-31T12:00:00Z"),
            defaultNewTransactionDate("month:2026-08", now, zone),
        )
    }

    @Test
    fun default_pastFebruary_usesTheRealMonthLength() {
        assertEquals(Instant.parse("2026-02-28T12:00:00Z"), defaultNewTransactionDate("month:2026-02", now, zone))
        assertEquals(Instant.parse("2024-02-29T12:00:00Z"), defaultNewTransactionDate("month:2024-02", now, zone))
    }

    @Test
    fun default_noonIsInTheGivenZone() {
        val lisbon = ZoneId.of("Europe/Lisbon") // UTC+1 in winter, +0 in summer
        val result = defaultNewTransactionDate("month:2026-01", now, lisbon)

        assertEquals(LocalDate.of(2026, 1, 31), result.atZone(lisbon).toLocalDate())
        assertEquals(12, result.atZone(lisbon).hour)
    }

    @Test
    fun default_currentOrFutureMonth_isNow() {
        assertEquals(now, defaultNewTransactionDate("month:2026-10", now, zone))
        assertEquals(now, defaultNewTransactionDate("month:2027-03", now, zone))
        assertEquals(now, defaultNewTransactionDate(Period.CurrentMonth.encode(), now, zone))
    }

    @Test
    fun default_malformedOrNonMonthTokens_areNow() {
        assertEquals(now, defaultNewTransactionDate("garbage", now, zone))
        assertEquals(now, defaultNewTransactionDate("month:nope", now, zone))
        assertEquals(now, defaultNewTransactionDate("", now, zone))
        assertEquals(now, defaultNewTransactionDate(Period.Rolling(TimeRange.entries.first()).encode(), now, zone))
    }
}
