package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
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
    fun month_mustBeIsoYearMonth() {
        invalid { validateMonthSettings(LocalMonthSettings("2026-13", 5)) }
        invalid { validateMonthSettings(LocalMonthSettings("oct", 5)) }
        invalid { validateMonthSettings(LocalMonthSettings("", 5)) }
    }

    @Test
    fun daysOff_boundaries() {
        validateMonthSettings(LocalMonthSettings("2026-10", 1))
        validateMonthSettings(LocalMonthSettings("2026-10", MAX_DAYS_OFF))
        invalid { validateMonthSettings(LocalMonthSettings("2026-10", -1)) }
        invalid { validateMonthSettings(LocalMonthSettings("2026-10", MAX_DAYS_OFF + 1)) }
    }

    @Test
    fun zeroDaysOff_isNotAStorableRecord() {
        invalid { validateMonthSettings(LocalMonthSettings("2026-10", 0)) }
        invalid { validateMonthSettings(LocalMonthSettings("2026-10")) }
    }

    @Test
    fun mapping_roundTrips() {
        val record = MonthSettings(YearMonth.of(2026, 3), 4).toLocal()

        assertEquals(LocalMonthSettings("2026-03", 4), record)
        assertEquals(MonthSettings(YearMonth.of(2026, 3), 4), record.toDomainOrNull())
    }

    @Test
    fun unparsableMonth_mapsToNull() {
        assertNull(LocalMonthSettings("bad", 5).toDomainOrNull())
    }

    @Test
    fun monthSettings_isBlank_onlyWithoutDaysOff() {
        assertTrue(MonthSettings(YearMonth.of(2026, 3)).isBlank)
        assertFalse(MonthSettings(YearMonth.of(2026, 3), 1).isBlank)
    }

    @Test
    fun upgrade_dropsRecordsThatOnlyCarriedTheRemovedWorkingDays_keepsDaysOff() {
        val records = listOf(LocalMonthSettings("2026-09"), LocalMonthSettings("2026-10", 3))

        assertEquals(listOf(LocalMonthSettings("2026-10", 3)), records.upgradedTo23())
    }
}
