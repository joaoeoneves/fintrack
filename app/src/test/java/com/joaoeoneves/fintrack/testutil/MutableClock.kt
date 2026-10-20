package com.joaoeoneves.fintrack.testutil

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** A [Clock] whose [instant] tests can move forward, e.g. to simulate a month rollover. */
class MutableClock(
    var now: Instant,
    private val zoneId: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now
}
