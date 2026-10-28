package com.joaoeoneves.fintrack.ui.common

import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.encode
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** The route argument for a new transaction opened while [this] period is viewed: only a past month carries one. */
fun Period.newTransactionArg(): String? = (this as? Period.Month)?.encode()

/**
 * The default date of a new transaction: the last day (at noon) of the past month it was opened from, else [now].
 * [period] is the encoded token from [newTransactionArg]; anything that is not a past month means today.
 */
fun defaultNewTransactionDate(
    period: String?,
    now: Instant,
    zone: ZoneId,
): Instant =
    when (val decoded = decodePeriod(period, now, zone)) {
        is Period.Month ->
            decoded.yearMonth
                .atEndOfMonth()
                .atTime(LocalTime.NOON)
                .atZone(zone)
                .toInstant()
        else -> now
    }
