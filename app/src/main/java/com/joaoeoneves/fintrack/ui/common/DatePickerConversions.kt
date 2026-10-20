package com.joaoeoneves.fintrack.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Converts a stored [Instant] into the millis Material3's DatePicker expects: the picker works in
 * UTC midnights, so the date the user sees in [zone] is re-expressed as that calendar day's start
 * in UTC. Without this, an instant late in the evening west of UTC (or early morning east of it)
 * would preselect the wrong day.
 */
fun Instant.toDatePickerMillis(zone: ZoneId): Long =
    atZone(zone)
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

/**
 * Converts a DatePicker selection ([pickedUtcMillis], a UTC midnight) back into an [Instant] on
 * the picked calendar day in [zone], keeping [original]'s local time of day so changing only the
 * date doesn't shift the stored time.
 */
fun datePickerMillisToInstant(
    pickedUtcMillis: Long,
    original: Instant,
    zone: ZoneId,
): Instant {
    val pickedDate: LocalDate = Instant.ofEpochMilli(pickedUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val timeOfDay = original.atZone(zone).toLocalTime()
    return ZonedDateTime.of(pickedDate, timeOfDay, zone).toInstant()
}
