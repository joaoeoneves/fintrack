package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** [InstantRange.contains] and [InstantRange.encloses], both half-open `[start, end)`. */
class InstantRangeTest {
    private fun at(iso: String): Instant = Instant.parse(iso)

    private val october = InstantRange(at("2026-10-01T00:00:00Z"), at("2026-11-01T00:00:00Z"))

    @Test
    fun contains_startInclusive() {
        assertTrue(at("2026-10-01T00:00:00Z") in october)
    }

    @Test
    fun contains_endExclusive() {
        assertFalse(at("2026-11-01T00:00:00Z") in october)
    }

    @Test
    fun contains_justBeforeEnd_andJustBeforeStart() {
        assertTrue(at("2026-10-31T23:59:59.999Z") in october)
        assertFalse(at("2026-09-30T23:59:59.999Z") in october)
    }

    @Test
    fun encloses_sameRange() {
        assertTrue(october.encloses(october))
    }

    @Test
    fun encloses_innerRange() {
        assertTrue(october.encloses(InstantRange(at("2026-10-05T00:00:00Z"), at("2026-10-10T00:00:00Z"))))
    }

    @Test
    fun encloses_outerRange_isFalse() {
        assertFalse(InstantRange(at("2026-10-05T00:00:00Z"), at("2026-10-10T00:00:00Z")).encloses(october))
    }

    @Test
    fun encloses_overlapping_isFalse() {
        assertFalse(october.encloses(InstantRange(at("2026-10-15T00:00:00Z"), at("2026-11-15T00:00:00Z"))))
        assertFalse(october.encloses(InstantRange(at("2026-09-15T00:00:00Z"), at("2026-10-15T00:00:00Z"))))
    }

    @Test
    fun encloses_disjoint_isFalse() {
        assertFalse(october.encloses(InstantRange(at("2026-11-01T00:00:00Z"), at("2026-12-01T00:00:00Z"))))
    }
}
