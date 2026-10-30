package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** The pure logic behind the days-off dialog: month range, per-type stepper limits, Carnival shortcut. */
class DaysOffSectionsTest {
    private val oct = YearMonth.of(2026, 10)

    @Test
    fun monthsBetween_sameMonth_isJustTheStart() {
        assertEquals(listOf(oct), monthsBetween(oct, oct))
    }

    @Test
    fun monthsBetween_untilBeforeStart_isJustTheStart() {
        assertEquals(listOf(oct), monthsBetween(oct, oct.minusMonths(3)))
    }

    @Test
    fun monthsBetween_isInclusive_andCrossesTheYear() {
        assertEquals(
            listOf(oct, YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1)),
            monthsBetween(oct, YearMonth.of(2027, 1)),
        )
    }

    @Test
    fun monthsBetween_isCappedAt24Months() {
        val months = monthsBetween(oct, oct.plusMonths(60))

        assertEquals(24, months.size)
        assertEquals(oct.plusMonths(23), months.last())
        assertEquals(24, monthsBetween(oct, oct.plusMonths(23)).size)
    }

    @Test
    fun counts_eachMaxIsWhatTheOtherTwoLeave() {
        val counts = DaysOffCounts(vacation = 10, sick = 5, other = 3)

        assertEquals(18, counts.total)
        assertEquals(MAX_DAYS_OFF - 8, counts.maxVacation)
        assertEquals(MAX_DAYS_OFF - 13, counts.maxSick)
        assertEquals(MAX_DAYS_OFF - 15, counts.maxOther)
    }

    @Test
    fun counts_empty_everyMaxIsTheFullLimit() {
        val counts = DaysOffCounts()

        assertEquals(MAX_DAYS_OFF, counts.maxVacation)
        assertEquals(MAX_DAYS_OFF, counts.maxSick)
        assertEquals(MAX_DAYS_OFF, counts.maxOther)
    }

    @Test
    fun withCarnival_onAddsOne_offTakesOne() {
        assertEquals(3, withCarnival(2, on = true, max = 10))
        assertEquals(1, withCarnival(2, on = false, max = 10))
    }

    @Test
    fun withCarnival_staysWithinZeroAndMax() {
        assertEquals(0, withCarnival(0, on = false, max = 10))
        assertEquals(5, withCarnival(5, on = true, max = 5))
        assertEquals(0, withCarnival(0, on = true, max = -2))
    }
}
