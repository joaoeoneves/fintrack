package com.joaoeoneves.fintrack.ui.income

import com.joaoeoneves.fintrack.domain.model.MealCardBalance
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

/** [mealCardViewOf]: which periods show the meal card and where the plafond came from. */
class MealCardViewTest {
    private val today = YearMonth.of(2026, 10)
    private val balance = MealCardBalance(receivedCents = 20_000, spentCents = 5_000)

    @Test
    fun currentMonth_plafondComesFromThePreviousMonth() {
        val view = mealCardViewOf(Period.CurrentMonth, today, balance)

        assertEquals(MealCardView(20_000, 5_000, YearMonth.of(2026, 9)), view)
        assertEquals(15_000L, view?.balanceCents)
    }

    @Test
    fun pastMonth_plafondComesFromTheMonthBeforeIt_includingAcrossAYear() {
        val march = mealCardViewOf(Period.Month(YearMonth.of(2026, 3)), today, balance)
        val january = mealCardViewOf(Period.Month(YearMonth.of(2026, 1)), today, balance)

        assertEquals(YearMonth.of(2026, 2), march?.plafondFrom)
        assertEquals(YearMonth.of(2025, 12), january?.plafondFrom)
    }

    @Test
    fun futureMonth_hasNoCard() {
        assertNull(mealCardViewOf(Period.Month(YearMonth.of(2026, 11)), today, balance))
    }

    @Test
    fun year_hasNoPlafondFrom_andFutureYearHasNoCard() {
        val thisYear = mealCardViewOf(Period.Year(2026), today, balance)

        assertEquals(MealCardView(20_000, 5_000, null), thisYear)
        assertNull(mealCardViewOf(Period.Year(2027), today, balance))
    }

    @Test
    fun overspentCard_hasANegativeBalance() {
        val view = mealCardViewOf(Period.CurrentMonth, today, MealCardBalance(1_000, 1_500))

        assertEquals(-500L, view?.balanceCents)
    }

    @Test
    fun otherPeriods_haveNoCard() {
        assertNull(mealCardViewOf(Period.Rolling(TimeRange.THREE_MONTHS), today, balance))
    }
}
