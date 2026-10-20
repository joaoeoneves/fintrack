package com.joaoeoneves.fintrack.ui.common.period

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.testutil.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** [PeriodSelection]: token storage in SavedStateHandle, month navigation and recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PeriodSelectionTest {
    private val clock = MutableClock(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)

    private fun selection(
        handle: SavedStateHandle = SavedStateHandle(),
        initial: String = "current",
    ) = PeriodSelection(handle, KEY, initial, clock)

    @Test
    fun token_defaultsToInitialToken_withoutWritingIt() {
        val handle = SavedStateHandle()
        val selection = selection(handle, initial = "rolling:SIX_MONTHS")

        assertEquals("rolling:SIX_MONTHS", selection.token.value)
    }

    @Test
    fun select_storesEncodedTokenUnderKey() {
        val handle = SavedStateHandle()
        val selection = selection(handle)

        selection.select(Period.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15)))

        assertEquals("custom:2026-10-01:2026-10-15", handle.get<String>(KEY))
        assertEquals("custom:2026-10-01:2026-10-15", selection.token.value)
    }

    @Test
    fun select_currentOrFutureMonth_isNormalizedToCurrent() {
        val selection = selection()

        selection.select(Period.Month(YearMonth.of(2026, 9)))
        assertEquals("month:2026-09", selection.token.value)

        selection.select(Period.Month(YearMonth.of(2026, 10)))
        assertEquals("current", selection.token.value)

        selection.select(Period.Month(YearMonth.of(2027, 2)))
        assertEquals("current", selection.token.value)
    }

    @Test
    fun previous_thenNext_walksMonthsAndStopsAtCurrent() {
        val selection = selection()

        selection.previous()
        assertEquals("month:2026-09", selection.token.value)
        selection.previous()
        assertEquals("month:2026-08", selection.token.value)
        selection.next()
        assertEquals("month:2026-09", selection.token.value)
        selection.next()
        assertEquals("current", selection.token.value)
        selection.next()
        assertEquals("current", selection.token.value)
    }

    @Test
    fun previous_acrossYearBoundary() {
        val selection = selection(initial = "month:2026-01")

        selection.previous()

        assertEquals("month:2025-12", selection.token.value)
    }

    @Test
    fun previousAndNext_onRollingOrCustom_keepSelection() {
        val selection = selection()
        selection.select(Period.Rolling(TimeRange.ONE_YEAR))

        selection.previous()
        selection.next()

        assertEquals("rolling:ONE_YEAR", selection.token.value)
    }

    @Test
    fun previous_fromGarbageInitialToken_treatsItAsCurrentMonth() {
        val selection = selection(initial = "nonsense")

        selection.previous()

        assertEquals("month:2026-09", selection.token.value)
    }

    @Test
    fun selection_survivesRecreation_withSameHandle_andWinsOverInitialToken() {
        val handle = SavedStateHandle()
        selection(handle).previous()

        val recreated = selection(handle, initial = "rolling:THREE_MONTHS")

        assertEquals("month:2026-09", recreated.token.value)
    }

    @Test
    fun storedCurrent_staysCurrent_afterRollover_whilePreviousUsesNewMonth() {
        val handle = SavedStateHandle()
        selection(handle).select(Period.CurrentMonth)

        clock.now = Instant.parse("2026-11-01T00:00:00Z")
        val recreated = selection(handle)
        assertEquals("current", recreated.token.value)

        recreated.previous()
        assertEquals("month:2026-10", recreated.token.value)
    }

    private companion object {
        const val KEY = "period"
    }
}
