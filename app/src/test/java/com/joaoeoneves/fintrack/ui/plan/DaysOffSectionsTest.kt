package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** The pure logic behind the days-off dialog: month range, per-type stepper limits. */
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
    fun counts_eachMaxIsWhatTheOtherLeaves() {
        val counts = DaysOffCounts(vacation = 10, other = 3)

        assertEquals(13, counts.total)
        assertEquals(MAX_DAYS_OFF - 3, counts.maxVacation)
        assertEquals(MAX_DAYS_OFF - 10, counts.maxOther)
    }

    @Test
    fun counts_empty_everyMaxIsTheFullLimit() {
        val counts = DaysOffCounts()

        assertEquals(MAX_DAYS_OFF, counts.maxVacation)
        assertEquals(MAX_DAYS_OFF, counts.maxOther)
    }
}
