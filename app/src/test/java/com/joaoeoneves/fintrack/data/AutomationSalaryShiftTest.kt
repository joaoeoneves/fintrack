package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.data.local.LocalAutomation
import com.joaoeoneves.fintrack.data.local.toDomain
import com.joaoeoneves.fintrack.data.local.toLocal
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSalaryShiftTest {
    @Test
    fun roundTrip_keepsTrueFalseAndNull() {
        for (value in listOf(true, false, null)) {
            val settings = AutomationSettings(salaryPaysNextMonth = value)
            assertEquals(value, settings.toLocal().salaryPaysNextMonth)
            assertEquals(settings, settings.toLocal().toDomain())
            assertEquals(value, LocalAutomation(salaryPaysNextMonth = value).toDomain().salaryPaysNextMonth)
        }
    }

    @Test
    fun toFirestoreMap_includesFieldOnlyWhenNonNull() {
        assertFalse(AutomationSettings().toFirestoreMap().containsKey("salaryPaysNextMonth"))
        assertEquals(true, AutomationSettings(salaryPaysNextMonth = true).toFirestoreMap()["salaryPaysNextMonth"])
        val off = AutomationSettings(salaryPaysNextMonth = false).toFirestoreMap()
        assertTrue(off.containsKey("salaryPaysNextMonth"))
        assertEquals(false, off["salaryPaysNextMonth"])
    }

    @Test
    fun toFirestoreMap_hasNoPaydayKeys() {
        val map = AutomationSettings(mapOf("p1" to java.time.YearMonth.of(2026, 9)), true).toFirestoreMap()

        assertFalse(map.containsKey("paydayEnabled"))
        assertFalse(map.containsKey("paydayThrough"))
        assertEquals(setOf("lineThrough", "salaryPaysNextMonth", "updatedAt"), map.keys)
    }
}
