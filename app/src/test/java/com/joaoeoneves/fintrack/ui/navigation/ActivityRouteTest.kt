package com.joaoeoneves.fintrack.ui.navigation

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.encode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ActivityRouteTest {
    @Test
    fun currentMonth_encodesPeriodAndCategory() {
        assertEquals(
            Activity(period = "current", category = BuiltInCategories.FOOD.id.value),
            activityRouteFor(Period.CurrentMonth, BuiltInCategories.FOOD.id),
        )
    }

    @Test
    fun month_usesPeriodCodec() {
        val period = Period.Month(YearMonth.of(2026, 9))

        assertEquals(period.encode(), activityRouteFor(period, CategoryId("gym")).period)
        assertEquals("gym", activityRouteFor(period, CategoryId("gym")).category)
    }

    @Test
    fun custom_roundTripsToken() {
        val period = Period.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15))

        assertEquals("custom:2026-10-01:2026-10-15", activityRouteFor(period, CategoryId("x")).period)
    }
}
