package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [selfHealCandidates] and [missingOccurrences]. Rent: monthly, day 15, from 2026-01-01, auto-post. */
class RecurringSelfHealTest {
    private val today = day(2026, 3, 20)
    private val jan15 = day(2026, 1, 15)
    private val feb15 = day(2026, 2, 15)
    private val mar15 = day(2026, 3, 15)

    private fun auto(
        handled: java.time.LocalDate? = mar15,
        startDate: java.time.LocalDate = day(2026, 1, 1),
    ) = recurringItem(handledThrough = handled, autoPost = true, startDate = startDate)

    @Test
    fun candidates_areTheOccurrencesUpToTheCursor() {
        assertEquals(listOf(jan15, feb15, mar15), auto().selfHealCandidates(today))
        assertEquals(listOf(jan15), auto(handled = feb15.minusDays(1)).selfHealCandidates(today))
    }

    @Test
    fun candidates_neverGoPastToday() {
        assertEquals(listOf(jan15, feb15), auto(handled = day(2026, 6, 15)).selfHealCandidates(day(2026, 3, 1)))
    }

    @Test
    fun candidates_neverGoPastTheEndDate() {
        val item = auto().copy(endDate = day(2026, 2, 28))

        assertEquals(listOf(jan15, feb15), item.selfHealCandidates(today))
    }

    @Test
    fun candidates_startAtTheStartDate() {
        assertEquals(listOf(mar15), auto(startDate = day(2026, 3, 1)).selfHealCandidates(today))
        assertTrue(auto(startDate = day(2026, 3, 16)).selfHealCandidates(today).isEmpty())
    }

    @Test
    fun candidates_stayWithinTheTwelveMonthLookback() {
        val item = auto(startDate = day(2024, 1, 1))

        val candidates = item.selfHealCandidates(today)

        assertEquals(12, candidates.size)
        assertEquals(day(2025, 4, 15), candidates.first())
        assertEquals(mar15, candidates.last())
    }

    @Test
    fun candidates_withoutACursor_areEmpty() {
        assertTrue(auto(handled = null).selfHealCandidates(today).isEmpty())
    }

    @Test
    fun candidates_areEmptyForInactiveManualAndIncomeItems() {
        assertTrue(auto().copy(active = false).selfHealCandidates(today).isEmpty())
        assertTrue(auto().copy(autoPost = false).selfHealCandidates(today).isEmpty())
        val income = auto().copy(type = RecurringType.INCOME, categoryId = null)
        assertTrue(income.selfHealCandidates(today).isEmpty())
    }

    @Test
    fun candidates_leaveOutTheSkippedPeriods_matchedByMonth() {
        val item = auto().copy(skipped = setOf(day(2026, 2, 10)))

        assertEquals(listOf(jan15, mar15), item.selfHealCandidates(today))
    }

    @Test
    fun candidates_yearly_leaveOutAYearWithASkippedDate() {
        val yearly =
            recurringItem(
                frequency = RecurringFrequency.YEARLY,
                startDate = day(2024, 1, 1),
                handledThrough = mar15,
                autoPost = true,
                skipped = setOf(day(2026, 3, 15)),
            )

        assertTrue(yearly.selfHealCandidates(today).isEmpty())
    }

    @Test
    fun missing_exactIdMatchIsPresent() {
        val item = auto()

        val missing = item.missingOccurrences(listOf(jan15, feb15, mar15), setOf(occurrenceTransactionId("r1", feb15)))

        assertEquals(listOf(jan15, mar15), missing)
    }

    @Test
    fun missing_aDifferentDayInTheSameMonthCountsAsPresent() {
        val missing = auto().missingOccurrences(listOf(feb15), setOf("rec_r1_20260210"))

        assertTrue(missing.isEmpty())
    }

    @Test
    fun missing_otherItemsAndUnrelatedIdsDoNotCount() {
        val missing = auto().missingOccurrences(listOf(feb15), setOf("rec_r2_20260215", "manual-1", "rec_r1_bad"))

        assertEquals(listOf(feb15), missing)
    }

    @Test
    fun missing_yearly_anyDayOfTheYearIsPresent() {
        val yearly = recurringItem(frequency = RecurringFrequency.YEARLY, startDate = day(2024, 1, 1))

        assertTrue(yearly.missingOccurrences(listOf(mar15), setOf("rec_r1_20260101")).isEmpty())
        assertEquals(listOf(mar15), yearly.missingOccurrences(listOf(mar15), setOf("rec_r1_20250315")))
    }
}
