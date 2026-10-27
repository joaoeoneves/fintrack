package com.joaoeoneves.fintrack.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Unit tests for [toDatePickerMillis] / [datePickerMillisToInstant]. Every test passes an explicit
 * [ZoneId] so results never depend on the machine's default time zone.
 */
class DatePickerConversionsTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val newYork = ZoneId.of("America/New_York")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun utcMidnightMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun localInstant(
        dateTime: String,
        zone: ZoneId,
    ): Instant = LocalDateTime.parse(dateTime).atZone(zone).toInstant()

    // ---- toDatePickerMillis ----

    @Test
    fun toDatePickerMillis_lisbonSummerJustAfterLocalMidnight_usesLocalDateNotPreviousUtcDay() {
        // 00:30 WEST (UTC+1) is still the previous day in UTC.
        val instant = localInstant("2026-07-15T00:30", lisbon)
        assertEquals(Instant.parse("2026-07-14T23:30:00Z"), instant)

        assertEquals(utcMidnightMillis(LocalDate.of(2026, 7, 15)), instant.toDatePickerMillis(lisbon))
    }

    @Test
    fun toDatePickerMillis_newYorkLateEvening_usesLocalDateNotNextUtcDay() {
        // 23:30 EST (UTC-5) is already the next day in UTC.
        val instant = localInstant("2026-01-15T23:30", newYork)
        assertEquals(Instant.parse("2026-01-16T04:30:00Z"), instant)

        assertEquals(utcMidnightMillis(LocalDate.of(2026, 1, 15)), instant.toDatePickerMillis(newYork))
    }

    @Test
    fun toDatePickerMillis_tokyoEarlyMorning_usesLocalDate() {
        val instant = localInstant("2026-05-02T08:00", tokyo) // 2026-05-01T23:00Z

        assertEquals(utcMidnightMillis(LocalDate.of(2026, 5, 2)), instant.toDatePickerMillis(tokyo))
    }

    @Test
    fun toDatePickerMillis_utcZone_isUtcMidnightOfSameDay() {
        val instant = Instant.parse("2026-03-10T17:45:12Z")

        assertEquals(utcMidnightMillis(LocalDate.of(2026, 3, 10)), instant.toDatePickerMillis(ZoneOffset.UTC))
    }

    @Test
    fun toDatePickerMillis_resultIsAlwaysExactUtcMidnight() {
        val instant = localInstant("2026-10-20T13:37:42.123", newYork)

        val millis = instant.toDatePickerMillis(newYork)

        assertEquals(0L, Math.floorMod(millis, 86_400_000L))
    }

    // ---- datePickerMillisToInstant ----

    @Test
    fun datePickerMillisToInstant_localMidnightOriginal_pickingSameDate_returnsExactOriginal() {
        val original = LocalDate.of(2026, 7, 15).atStartOfDay(lisbon).toInstant()

        val result = datePickerMillisToInstant(original.toDatePickerMillis(lisbon), original, lisbon)

        assertEquals(original, result)
    }

    @Test
    fun datePickerMillisToInstant_pickingSameDate_isIdentity_acrossZonesAndTimes() {
        val zones = listOf(lisbon, newYork, tokyo, ZoneId.of("Pacific/Kiritimati"), ZoneId.of("Pacific/Pago_Pago"))
        val times = listOf("2026-01-15T00:00", "2026-01-15T00:30", "2026-07-15T12:00", "2026-07-15T23:59:59.999")
        for (zone in zones) {
            for (time in times) {
                val original = localInstant(time, zone)
                val result = datePickerMillisToInstant(original.toDatePickerMillis(zone), original, zone)
                assertEquals("$time in $zone", original, result)
            }
        }
    }

    @Test
    fun datePickerMillisToInstant_pickingDifferentDate_keepsLocalTimeOfDay() {
        val original = localInstant("2026-01-15T23:30", newYork)

        val result = datePickerMillisToInstant(utcMidnightMillis(LocalDate.of(2026, 1, 20)), original, newYork)

        val local = result.atZone(newYork)
        assertEquals(LocalDate.of(2026, 1, 20), local.toLocalDate())
        assertEquals(LocalTime.of(23, 30), local.toLocalTime())
    }

    @Test
    fun datePickerMillisToInstant_pickingDateAcrossDstChange_keepsLocalTimeNotUtcOffset() {
        // Original in EST (UTC-5); picked date is after the 2026-03-08 switch to EDT (UTC-4).
        val original = localInstant("2026-01-15T23:30", newYork)

        val result = datePickerMillisToInstant(utcMidnightMillis(LocalDate.of(2026, 3, 20)), original, newYork)

        assertEquals(Instant.parse("2026-03-21T03:30:00Z"), result)
        assertEquals(LocalTime.of(23, 30), result.atZone(newYork).toLocalTime())
    }

    @Test
    fun datePickerMillisToInstant_lisbonEarlyMorning_landsOnPickedLocalDate() {
        val original = localInstant("2026-07-15T00:30", lisbon)

        val result = datePickerMillisToInstant(utcMidnightMillis(LocalDate.of(2026, 8, 1)), original, lisbon)

        assertEquals(localInstant("2026-08-01T00:30", lisbon), result)
    }

    @Test
    fun datePickerMillisToInstant_timeInDstGap_resolvesForwardWithoutThrowing() {
        // 02:30 doesn't exist in New York on 2026-03-08 (clocks jump 02:00 -> 03:00 EST->EDT).
        val original = localInstant("2026-03-07T02:30", newYork)

        val result = datePickerMillisToInstant(utcMidnightMillis(LocalDate.of(2026, 3, 8)), original, newYork)

        val local = result.atZone(newYork)
        assertEquals(LocalDate.of(2026, 3, 8), local.toLocalDate())
        assertEquals(LocalTime.of(3, 30), local.toLocalTime())
    }

    @Test
    fun datePickerMillisToInstant_lisbonDstGap_resolvesWithoutThrowing() {
        // 01:30 doesn't exist in Lisbon on 2026-03-29 (clocks jump 01:00 -> 02:00 WET->WEST).
        val original = localInstant("2026-03-28T01:30", lisbon)

        val result = datePickerMillisToInstant(utcMidnightMillis(LocalDate.of(2026, 3, 29)), original, lisbon)

        assertEquals(LocalDate.of(2026, 3, 29), result.atZone(lisbon).toLocalDate())
    }

    @Test
    fun datePickerMillisToInstant_timeInDstOverlap_staysOnPickedDateAndTime() {
        // 01:30 occurs twice in New York on 2026-11-01 (EDT->EST fall back).
        val original = localInstant("2026-10-31T01:30", newYork)

        val result = datePickerMillisToInstant(utcMidnightMillis(LocalDate.of(2026, 11, 1)), original, newYork)

        val local = result.atZone(newYork)
        assertEquals(LocalDate.of(2026, 11, 1), local.toLocalDate())
        assertEquals(LocalTime.of(1, 30), local.toLocalTime())
    }

    // ---- utcMillisToLocalDate / LocalDate.toUtcMillis (date-range picker) ----

    @Test
    fun utcMillisToLocalDate_utcMidnight_isThatDate() {
        assertEquals(LocalDate.of(2026, 10, 15), utcMillisToLocalDate(utcMidnightMillis(LocalDate.of(2026, 10, 15))))
    }

    @Test
    fun utcMillisToLocalDate_lastMillisOfUtcDay_isStillThatDate() {
        val lastMillis = utcMidnightMillis(LocalDate.of(2026, 10, 16)) - 1

        assertEquals(LocalDate.of(2026, 10, 15), utcMillisToLocalDate(lastMillis))
    }

    @Test
    fun toUtcMillis_isUtcMidnight() {
        assertEquals(Instant.parse("2026-10-15T00:00:00Z").toEpochMilli(), LocalDate.of(2026, 10, 15).toUtcMillis())
    }

    @Test
    fun toUtcMillis_utcMillisToLocalDate_roundTrip_acrossEdgeDates() {
        val dates =
            listOf(
                LocalDate.of(1970, 1, 1),
                LocalDate.of(1969, 12, 31),
                LocalDate.of(2028, 2, 29),
                LocalDate.of(2026, 3, 29),
                LocalDate.of(2026, 10, 25),
                LocalDate.of(2026, 12, 31),
            )
        for (date in dates) {
            assertEquals(date, utcMillisToLocalDate(date.toUtcMillis()))
        }
    }

    @Test
    fun toUtcMillis_ignoresDefaultZone() {
        val original = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(utcMidnightMillis(LocalDate.of(2026, 10, 15)), LocalDate.of(2026, 10, 15).toUtcMillis())
            val millis = utcMidnightMillis(LocalDate.of(2026, 10, 15))
            assertEquals(LocalDate.of(2026, 10, 15), utcMillisToLocalDate(millis))
        } finally {
            java.util.TimeZone.setDefault(original)
        }
    }
}
