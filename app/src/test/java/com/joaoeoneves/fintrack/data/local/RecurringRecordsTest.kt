package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [LocalRecurringItem] mapping (Robolectric because dropping a bad record logs through `android.util.Log`). */
@RunWith(RobolectricTestRunner::class)
class RecurringRecordsTest {
    private val full =
        recurringItem(
            id = "k",
            frequency = RecurringFrequency.YEARLY,
            dayOfMonth = 29,
            monthOfYear = 2,
            note = "n",
            endDate = day(2030, 1, 1),
            handledThrough = day(2026, 2, 28),
            active = false,
        )

    @Test
    fun roundTrip_keepsEveryField() {
        assertEquals(full, full.toLocal().toDomainOrNull())
    }

    @Test
    fun roundTrip_nullOptionals_incomeWithoutCategory() {
        val income = recurringItem(type = RecurringType.INCOME)

        val local = income.toLocal()

        assertNull(local.categoryId)
        assertNull(local.endDate)
        assertNull(local.handledThrough)
        assertNull(local.monthOfYear)
        assertEquals(income, local.toDomainOrNull())
    }

    @Test
    fun toLocal_writesEnumNamesAndIsoDates() {
        val local = full.toLocal()

        assertEquals("EXPENSE", local.type)
        assertEquals("YEARLY", local.frequency)
        assertEquals("2026-01-01", local.startDate)
        assertEquals("2030-01-01", local.endDate)
    }

    @Test
    fun badEnumOrDate_isDropped() {
        val ok = full.toLocal()

        assertNull(ok.copy(type = "BOGUS").toDomainOrNull())
        assertNull(ok.copy(frequency = "WEEKLY").toDomainOrNull())
        assertNull(ok.copy(startDate = "2026-02-30").toDomainOrNull())
        assertNull(ok.copy(endDate = "soon").toDomainOrNull())
        assertNull(ok.copy(handledThrough = "").toDomainOrNull())
    }

    @Test
    fun inconsistentSchedule_isDropped() {
        val ok = full.toLocal()

        assertNull(ok.copy(dayOfMonth = 32).toDomainOrNull())
        assertNull(ok.copy(monthOfYear = null).toDomainOrNull())
        assertNull(ok.copy(frequency = "MONTHLY").toDomainOrNull())
    }

    @Test
    fun validate_acceptsAWellFormedItem() {
        validateRecurring(full.toLocal(), setOf("FOOD"))
    }

    @Test
    fun validate_rejectsBlankIdAndUnparsableDates() {
        val ok = recurringItem().toLocal()
        val known = setOf("FOOD")

        assertThrows(IllegalArgumentException::class.java) { validateRecurring(ok.copy(id = ""), known) }
        assertThrows(IllegalArgumentException::class.java) { validateRecurring(ok.copy(startDate = "x"), known) }
        assertThrows(IllegalArgumentException::class.java) { validateRecurring(ok.copy(handledThrough = "x"), known) }
        assertThrows(IllegalArgumentException::class.java) { validateRecurring(ok.copy(type = "BOGUS"), known) }
    }

    @Test
    fun validate_rejectsEndBeforeStart_andUnknownFrequency() {
        val ok = recurringItem().toLocal()
        val known = setOf("FOOD")

        assertThrows(IllegalArgumentException::class.java) {
            validateRecurring(ok.copy(endDate = "2025-12-31"), known)
        }
        assertThrows(IllegalArgumentException::class.java) { validateRecurring(ok.copy(frequency = "DAILY"), known) }
        assertTrue(runCatching { validateRecurring(ok.copy(endDate = "2026-01-01"), known) }.isSuccess)
    }
}
