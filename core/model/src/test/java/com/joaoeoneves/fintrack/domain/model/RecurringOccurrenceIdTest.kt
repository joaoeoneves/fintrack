package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.day
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [parseOccurrenceId], the inverse of [occurrenceTransactionId]. */
class RecurringOccurrenceIdTest {
    @Test
    fun roundTrip_withOccurrenceTransactionId() {
        val date = day(2026, 3, 15)

        assertEquals(OccurrenceRef("abc123", date), parseOccurrenceId(occurrenceTransactionId("abc123", date)))
    }

    @Test
    fun underscoreInTheItemId_isKept() {
        val id = occurrenceTransactionId("my_item_1", day(2026, 12, 31))

        assertEquals(OccurrenceRef("my_item_1", day(2026, 12, 31)), parseOccurrenceId(id))
    }

    @Test
    fun badDate_isNull() {
        assertNull(parseOccurrenceId("rec_r1_20261340"))
        assertNull(parseOccurrenceId("rec_r1_notadate"))
        assertNull(parseOccurrenceId("rec_r1_2026031"))
    }

    @Test
    fun notAnOccurrenceId_isNull() {
        assertNull(parseOccurrenceId("abc_r1_20260315"))
        assertNull(parseOccurrenceId(""))
        assertNull(parseOccurrenceId("rec_"))
    }

    @Test
    fun missingItemId_isNull() {
        assertNull(parseOccurrenceId("rec__20260315"))
    }
}
