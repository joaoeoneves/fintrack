package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.bonusExtra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** A managed line the profile stops paying is ended (kept, 0 from then on) when it has history, else deleted. */
class ManagedLineEndingTest {
    private val jan = YearMonth.of(2026, 1)
    private val feb = YearMonth.of(2026, 2)
    private val mar = YearMonth.of(2026, 3)
    private val profile =
        SalaryProfile(
            baseGrossCents = 100_000,
            irsBasePpm = 100_000,
            mealRatePerDayCents = 900,
            extras = listOf(bonusExtra()),
        )
    private val stored = salaryProfileLines(profile, emptyList(), effectiveFrom = jan)

    private fun List<PlannedIncome>.byKey(key: String) = single { it.managedKey == key }

    @Test
    fun zeroAmountWithHistory_isEnded_keepingEarlierMonths() {
        val noMeal = profile.copy(mealRatePerDayCents = null)

        val result = salaryProfileLines(noMeal, stored, effectiveFrom = mar)
        val meal = result.byKey(MANAGED_MEAL)

        assertEquals(900L, meal.asOf(feb)?.amountCents)
        assertEquals(0L, meal.asOf(mar)?.amountCents)
        assertFalse(MANAGED_MEAL in staleManagedIds(noMeal, stored, mar))
    }

    @Test
    fun zeroAmountWithoutHistory_isDeleted() {
        val noMeal = profile.copy(mealRatePerDayCents = null)

        assertTrue(salaryProfileLines(noMeal, stored, effectiveFrom = jan).none { it.managedKey == MANAGED_MEAL })
        assertEquals(listOf(MANAGED_MEAL), staleManagedIds(noMeal, stored, jan))
    }

    @Test
    fun removedExtraWithHistory_isEnded_notDeleted() {
        val noBonus = profile.copy(extras = emptyList())
        val key = managedExtraKey("bonus", 5)

        val result = salaryProfileLines(noBonus, stored, effectiveFrom = mar)

        assertEquals(10_000L, result.byKey(key).asOf(feb)?.amountCents)
        assertEquals(0L, result.byKey(key).asOf(mar)?.amountCents)
        assertTrue(staleManagedIds(noBonus, stored, mar).isEmpty())
    }

    @Test
    fun endingAnAlreadyEndedLine_changesNothing() {
        val noMeal = profile.copy(mealRatePerDayCents = null)
        val ended = salaryProfileLines(noMeal, stored, effectiveFrom = mar)

        val again = managedLinesSync(noMeal, ended, effectiveFrom = YearMonth.of(2026, 6))

        assertTrue(again.isEmpty)
    }

    @Test
    fun changingTheHolidayMonth_endsTheOldLine_andAddsAScheduledOne() {
        val moved = profile.copy(holidaySubsidyMonth = 7)

        val result = salaryProfileLines(moved, stored, effectiveFrom = mar)
        val old = result.byKey(MANAGED_HOLIDAY)
        val newKey = "${MANAGED_HOLIDAY}_202603"
        val added = result.byKey(newKey)

        assertEquals(6, old.monthOfYear)
        assertEquals(0L, old.asOf(mar)?.amountCents)
        assertTrue(old.asOf(feb)!!.amountCents > 0L)
        assertEquals(newKey, added.id)
        assertEquals(7, added.monthOfYear)
        assertEquals(mar, added.startMonth)
        assertNull(added.asOf(feb))
        assertNotNull(added.asOf(mar))
    }

    @Test
    fun changingTheHolidayMonth_appliedTwice_givesTheSameResult() {
        val moved = profile.copy(holidaySubsidyMonth = 7)
        val sync = managedLinesSync(moved, stored, effectiveFrom = mar)
        val written = sync.upserts.associateBy { it.id }
        val afterSync =
            stored.filter { it.id !in sync.deletes }.map { written[it.id] ?: it } +
                sync.upserts.filter { u -> stored.none { it.id == u.id } }

        assertTrue(managedLinesSync(moved, afterSync, effectiveFrom = mar).isEmpty)
        assertTrue(managedLinesSync(moved, afterSync, effectiveFrom = YearMonth.of(2026, 9)).isEmpty)
    }

    @Test
    fun scheduledKey_isValid_andMapsToItsBaseKey() {
        assertTrue(isValidManagedKey("${MANAGED_HOLIDAY}_202603"))
        assertTrue(isValidManagedKey("${MANAGED_CHRISTMAS}_202612"))
        assertFalse(isValidManagedKey("${MANAGED_HOLIDAY}_202613"))
        assertFalse(isValidManagedKey("${MANAGED_MEAL}_202603"))
        assertEquals(MANAGED_HOLIDAY, baseManagedKey("${MANAGED_HOLIDAY}_202603"))
        assertEquals(MANAGED_MEAL, baseManagedKey(MANAGED_MEAL))
        val scheduled = PlannedIncome("x", "H", 1, 0, managedKey = "${MANAGED_HOLIDAY}_202603")
        assertEquals(SalaryComponent.HOLIDAY, scheduled.salaryComponent)
    }
}
