package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MAX_WORKING_DAYS
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
        val record = localMonthSettingsOf(YearMonth.of(2026, 3), 19)

        assertEquals(LocalMonthSettings("2026-03", 19), record)
        assertEquals(MonthSettings(YearMonth.of(2026, 3), 19), record.toDomainOrNull())
    }

    @Test
    fun unparsableMonth_mapsToNull() {
        assertNull(LocalMonthSettings("bad", 5).toDomainOrNull())
    }
}
