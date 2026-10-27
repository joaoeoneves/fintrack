package com.joaoeoneves.fintrack.ui.common.period

import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AsMonthPeriodTest {
    @Test
    fun currentMonth_isKept() {
        assertEquals(Period.CurrentMonth, Period.CurrentMonth.asMonthPeriod())
    }

    @Test
    fun pastMonth_isKept() {
        val month = Period.Month(YearMonth.of(2026, 3))

        assertEquals(month, month.asMonthPeriod())
    }

    @Test
    fun everyRollingRange_fallsBackToCurrentMonth() {
        TimeRange.entries.forEach { range ->
            assertEquals(Period.CurrentMonth, Period.Rolling(range).asMonthPeriod())
        }
    }

    @Test
    fun customRange_fallsBackToCurrentMonth() {
        val custom = Period.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))

        assertEquals(Period.CurrentMonth, custom.asMonthPeriod())
    }

    @Test
    fun singleDayCustomRange_fallsBackToCurrentMonth() {
        val day = LocalDate.of(2026, 9, 10)

        assertEquals(Period.CurrentMonth, Period.Custom(day, day).asMonthPeriod())
    }
}
