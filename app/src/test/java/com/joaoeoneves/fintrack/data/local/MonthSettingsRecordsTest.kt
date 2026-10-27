package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
import com.joaoeoneves.fintrack.domain.model.MAX_WORKING_DAYS
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class MonthSettingsRecordsTest {
    private fun invalid(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    @Test
    fun workingDays_boundaries() {
        validateMonthSettings(LocalMonthSettings("2026-10", 0))
        validateMonthSettings(LocalMonthSettings("2026-10", MAX_WORKING_DAYS))
        invalid { validateMonthSettings(LocalMonthSettings("2026-10", -1)) }
        invalid { validateMonthSettings(LocalMonthSettings("2026-10", MAX_WORKING_DAYS + 1)) }
    }

    @Test
    fun month_mustBeIsoYearMonth() {
        invalid { validateMonthSettings(LocalMonthSettings("2026-13", 5)) }
        invalid { validateMonthSettings(LocalMonthSettings("oct", 5)) }
        invalid { validateMonthSettings(LocalMonthSettings("", 5)) }
    }

    @Test
    fun mapping_roundTrips() {
        val record = localMonthSettingsOf(YearMonth.of(2026, 3), 19, 0)

        assertEquals(LocalMonthSettings("2026-03", 19), record)
        assertEquals(MonthSettings(YearMonth.of(2026, 3), 19), record.toDomainOrNull())
    }

    @Test
    fun unparsableMonth_mapsToNull() {
        assertNull(LocalMonthSettings("bad", 5).toDomainOrNull())
    }

    @Test
    fun workingDays_mayBeNull_whenDaysOffIsSet() {
        validateMonthSettings(LocalMonthSettings("2026-10", null, 3))
    }

    @Test
    fun daysOff_boundaries() {
        validateMonthSettings(LocalMonthSettings("2026-10", null, 1))
        validateMonthSettings(LocalMonthSettings("2026-10", null, MAX_DAYS_OFF))
        validateMonthSettings(LocalMonthSettings("2026-10", 10, 0))
        invalid { validateMonthSettings(LocalMonthSettings("2026-10", 10, -1)) }
        invalid { validateMonthSettings(LocalMonthSettings("2026-10", 10, MAX_DAYS_OFF + 1)) }
    }

    @Test
    fun atLeastOneFieldMustBeSet() {
        invalid { validateMonthSettings(LocalMonthSettings("2026-10", null, 0)) }
        invalid { validateMonthSettings(LocalMonthSettings("2026-10")) }
    }

    @Test
    fun bothFieldsTogether_areValid_andOutOfRangeWorkingDaysStillFails() {
        validateMonthSettings(LocalMonthSettings("2026-10", 12, 4))
        invalid { validateMonthSettings(LocalMonthSettings("2026-10", MAX_WORKING_DAYS + 1, 4)) }
    }

    @Test
    fun mapping_withDaysOff_roundTrips() {
        val record = localMonthSettingsOf(YearMonth.of(2026, 3), null, 4)
        val domain = MonthSettings(YearMonth.of(2026, 3), null, 4)

        assertEquals(LocalMonthSettings("2026-03", null, 4), record)
        assertEquals(domain, record.toDomainOrNull())
        assertEquals(record, domain.toLocal())
    }

    @Test
    fun monthSettings_isBlank_onlyWhenNothingIsSet() {
        assertTrue(MonthSettings(YearMonth.of(2026, 3), null).isBlank)
        assertFalse(MonthSettings(YearMonth.of(2026, 3), 0).isBlank)
        assertFalse(MonthSettings(YearMonth.of(2026, 3), null, 1).isBlank)
    }
}
