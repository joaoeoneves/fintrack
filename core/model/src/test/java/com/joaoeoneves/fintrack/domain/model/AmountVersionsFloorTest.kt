package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

/** [withVersion] and [amountAt]: editing in the middle of a history, the version cap and a zero amount. */
class AmountVersionsFloorTest {
    private val base = YearMonth.of(2024, 1)

    private fun m(offset: Int) = base.plusMonths(offset.toLong())

    private fun history(count: Int) = (0 until count).map { AmountVersion(m(it), (it + 1) * 100L) }

    @Test
    fun editingFromAnEarlierMonth_keepsTheLaterVersions() {
        val versions = listOf(AmountVersion(m(0), 100), AmountVersion(m(6), 600))

        val edited = withVersion(versions, m(3), 300)

        assertEquals(listOf(m(0), m(3), m(6)), edited.map { it.from })
        assertEquals(600L, edited.amountAt(m(8), null))
        assertEquals(300L, edited.amountAt(m(4), null))
    }

    @Test
    fun editingAnExistingMonth_replacesOnlyThatVersion() {
        val edited = withVersion(history(3), m(1), 999)

        assertEquals(listOf(100L, 999L, 300L), edited.map { it.amountCents })
    }

    @Test
    fun atTheCap_nothingIsFolded() {
        val edited = withVersion(history(MAX_AMOUNT_VERSIONS - 1), m(40), 1)

        assertEquals(MAX_AMOUNT_VERSIONS, edited.size)
        assertEquals(100L, edited.amountAt(m(0), null))
    }

    @Test
    fun overTheCap_foldsTheOldestIntoAFloor_soEarlyMonthsKeepTheDroppedAmount() {
        val edited = withVersion(history(MAX_AMOUNT_VERSIONS), m(40), 5_000)

        assertEquals(MAX_AMOUNT_VERSIONS, edited.size)
        // The two oldest (100 from m0, 200 from m1) fold into one floor from m0 holding the newest dropped amount.
        assertEquals(AmountVersion(m(0), 200), edited.first())
        assertEquals(200L, edited.amountAt(m(0), null))
        assertEquals(200L, edited.amountAt(m(1), null))
        assertEquals(300L, edited.amountAt(m(2), null))
        assertEquals(5_000L, edited.amountAt(m(40), null))
    }

    @Test
    fun repeatedEditsPastTheCap_neverGrowBeyondIt() {
        var versions = emptyList<AmountVersion>()
        repeat(30) { versions = withVersion(versions, m(it), (it + 1) * 10L) }

        assertEquals(MAX_AMOUNT_VERSIONS, versions.size)
        assertEquals(300L, versions.amountAt(m(29), null))
        assertEquals(versions.first().amountCents, versions.amountAt(m(0), null))
    }

    @Test
    fun zeroVersion_isKept_andIsDistinctFromNoAmount() {
        val versions = withVersion(listOf(AmountVersion(m(0), 100)), m(2), 0)

        assertEquals(0L, versions.amountAt(m(3), 777))
        assertEquals(100L, versions.amountAt(m(1), 777))
        assertNull(withVersion(versions, m(5), null).amountAt(m(6), 777))
    }
}
