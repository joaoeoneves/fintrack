package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** A second managed line of a key under an unexpected id is deleted (no history) or ended (history). */
class ManagedDuplicateLineTest {
    private val jan = YearMonth.of(2026, 1)
    private val feb = YearMonth.of(2026, 2)
    private val mar = YearMonth.of(2026, 3)
    private val profile = SalaryProfile(baseGrossCents = 100_000, irsBasePpm = 100_000, mealRatePerDayCents = 900)
    private val stored = salaryProfileLines(profile, emptyList(), effectiveFrom = jan)
    private val currentMeal = stored.single { it.id == MANAGED_MEAL }

    private fun duplicate(startMonth: YearMonth?): PlannedIncome {
        return currentMeal.copy(id = "stray", startMonth = startMonth, versions = emptyList())
    }

    @Test
    fun duplicateWithoutHistory_isDeleted() {
        val existing = stored.map { if (it.id == MANAGED_MEAL) it.copy(startMonth = feb) else it } + duplicate(jan)

        val lines = salaryProfileLines(profile, existing, effectiveFrom = jan)

        assertEquals(listOf("stray"), staleManagedIds(profile, existing, jan))
        assertTrue(lines.none { it.id == "stray" })
        assertEquals(feb, lines.single { it.id == MANAGED_MEAL }.startMonth)
    }

    @Test
    fun duplicateWithHistory_isEnded_keepingEarlierMonths() {
        val existing = stored + duplicate(null)

        val lines = salaryProfileLines(profile, existing, effectiveFrom = mar)
        val stray = lines.single { it.id == "stray" }

        assertEquals(900L, stray.asOf(feb)?.amountCents)
        assertEquals(0L, stray.asOf(mar)?.amountCents)
        assertTrue("stray" !in staleManagedIds(profile, existing, mar))
    }

    @Test
    fun currentAndScheduledLines_areNeverTreatedAsDuplicates() {
        val moved = profile.copy(holidaySubsidyMonth = 7)
        val withScheduled = salaryProfileLines(moved, stored, effectiveFrom = mar)

        val again = salaryProfileLines(moved, withScheduled, effectiveFrom = mar)

        assertTrue(staleManagedIds(moved, withScheduled, mar).isEmpty())
        assertTrue(again.all { line -> withScheduled.any { it.id == line.id } })
        assertEquals(0L, withScheduled.single { it.id == MANAGED_HOLIDAY }.asOf(mar)?.amountCents)
    }
}
