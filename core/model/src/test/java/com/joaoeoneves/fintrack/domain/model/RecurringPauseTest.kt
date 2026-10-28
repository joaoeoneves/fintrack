package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [paused] / [resumed]. Rent: monthly, day 15, from 2026-01-01; resumed on 2026-03-20 unless stated. */
class RecurringPauseTest {
    private val today = day(2026, 3, 20)
    private val jan15 = day(2026, 1, 15)
    private val feb15 = day(2026, 2, 15)
    private val mar15 = day(2026, 3, 15)

    @Test
    fun paused_marksInactiveAndRemembersTheDay() {
        val item = recurringItem(handledThrough = jan15).paused(today)

        assertFalse(item.active)
        assertEquals(today, item.pausedAt)
        assertEquals(jan15, item.handledThrough)
    }

    @Test
    fun resumed_nothingPendingBeforeThePause_movesTheCursorAndRecordsTheGap() {
        val paused = recurringItem(handledThrough = feb15).paused(day(2026, 2, 20))

        val resumed = paused.resumed(today)

        assertTrue(resumed.active)
        assertNull(resumed.pausedAt)
        assertEquals(mar15, resumed.handledThrough)
        assertEquals(setOf(mar15), resumed.skipped)
    }

    @Test
    fun resumed_aPendingOccurrenceBeforeThePause_staysPending() {
        val paused = recurringItem(handledThrough = jan15).paused(day(2026, 3, 1))

        val resumed = paused.resumed(today)

        assertEquals(jan15, resumed.handledThrough)
        assertEquals(setOf(mar15), resumed.skipped)
        assertEquals(feb15, resumed.firstPendingOccurrence())
        assertNull(resumed.pausedAt)

        val afterConfirming = resumed.copy(handledThrough = feb15)
        assertEquals(day(2026, 4, 15), afterConfirming.firstPendingOccurrence())
        assertEquals(0, afterConfirming.overdueCount(today))
    }

    @Test
    fun resumed_keepsExistingSkippedDates() {
        val paused = recurringItem(handledThrough = feb15, skipped = setOf(jan15)).paused(day(2026, 2, 20))

        assertEquals(setOf(jan15, mar15), paused.resumed(today).skipped)
    }

    @Test
    fun resumed_gapOccurrencesAtOrBeforeTheCursorAreNotRecorded() {
        val paused = recurringItem(handledThrough = mar15).paused(day(2026, 2, 1))

        val resumed = paused.resumed(today)

        assertEquals(mar15, resumed.handledThrough)
        assertTrue(resumed.skipped.isEmpty())
    }

    @Test
    fun resumed_aGapOlderThanTheLookback_isOnlyRecordedWithinTheWindow() {
        val paused = recurringItem(startDate = day(2024, 1, 1), handledThrough = day(2024, 12, 15)).paused(day(2024, 12, 20))

        val resumed = paused.resumed(today)

        assertEquals(mar15, resumed.handledThrough)
        assertEquals(12, resumed.skipped.size)
        assertEquals(day(2025, 4, 15), resumed.skipped.min())
    }

    @Test
    fun resumed_legacyWithoutPausedAt_neverMovesTheCursorBack() {
        val future = recurringItem(handledThrough = day(2026, 4, 15), active = false)

        val resumed = future.resumed(today)

        assertEquals(day(2026, 4, 15), resumed.handledThrough)
        assertTrue(resumed.skipped.isEmpty())
        assertTrue(resumed.active)
    }

    @Test
    fun resumed_legacyWithoutPausedAt_recordsTheInWindowDatesBeforeToday() {
        val legacy = recurringItem(handledThrough = jan15, active = false)

        val resumed = legacy.resumed(today)

        assertEquals(mar15, resumed.handledThrough)
        assertEquals(setOf(feb15, mar15), resumed.skipped)
    }

    @Test
    fun resumed_legacyWithoutACursor_recordsFromTheStart() {
        val legacy = recurringItem(handledThrough = null, active = false)

        val resumed = legacy.resumed(today)

        assertEquals(mar15, resumed.handledThrough)
        assertEquals(setOf(jan15, feb15, mar15), resumed.skipped)
    }

    @Test
    fun resumed_legacyBeforeTheFirstOccurrence_changesNoCursor() {
        val legacy = recurringItem(startDate = day(2026, 6, 1), handledThrough = null, active = false)

        val resumed = legacy.resumed(today)

        assertNull(resumed.handledThrough)
        assertTrue(resumed.skipped.isEmpty())
        assertTrue(resumed.active)
    }

    @Test
    fun resumed_sameDayPauseAndResume_recordsNothingNew() {
        val item = recurringItem(handledThrough = mar15)

        val resumed = item.paused(today).resumed(today)

        assertEquals(item.copy(active = true, pausedAt = null), resumed)
    }

    @Test
    fun resumed_sameDayWithAnOlderPending_keepsItPending() {
        val resumed = recurringItem(handledThrough = feb15).paused(today).resumed(today)

        assertEquals(feb15, resumed.handledThrough)
        assertTrue(resumed.skipped.isEmpty())
        assertEquals(mar15, resumed.firstPendingOccurrence())
    }

    @Test
    fun resumed_afterTheEndDate_recordsNothingPastIt() {
        val item = recurringItem(handledThrough = feb15, endDate = day(2026, 2, 28)).paused(day(2026, 2, 20))

        val resumed = item.resumed(today)

        assertEquals(feb15, resumed.handledThrough)
        assertTrue(resumed.skipped.isEmpty())
        assertNull(resumed.pausedAt)
    }

    @Test
    fun resumed_withAPendingOccurrenceBeforeTheEndDateAndPause_keepsItPending() {
        val item = recurringItem(handledThrough = jan15, endDate = day(2026, 2, 28)).paused(day(2026, 2, 20))

        val resumed = item.resumed(today)

        assertEquals(jan15, resumed.handledThrough)
        assertEquals(feb15, resumed.firstPendingOccurrence())
    }
}
