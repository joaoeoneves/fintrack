package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PayDatesTest {
    private val oct = YearMonth.of(2026, 10)

    private fun line(
        id: String = "p1",
        payDay: Int? = null,
        managedKey: String? = null,
    ) = PlannedIncome(id, "Line $id", 100_000L, 0, managedKey = managedKey, payDay = payDay)

    // --- payDateIn ---

    @Test
    fun payDateIn_null_isTheLastWorkingDay() {
        assertEquals(LocalDate.of(2026, 10, 30), line().payDateIn(oct))
        // Jan 2027 ends on a Sunday.
        assertEquals(LocalDate.of(2027, 1, 29), line().payDateIn(YearMonth.of(2027, 1)))
    }

    @Test
    fun payDateIn_null_matchesLastWorkingDayForEveryMonthOfTheYear() {
        // lastWorkingDayOf skips weekends and national holidays; the null payDay must never differ from it.
        for (m in 1..12) {
            val month = YearMonth.of(2026, m)
            assertEquals(lastWorkingDayOf(month), line().payDateIn(month))
        }
    }

    @Test
    fun payDateIn_fixedDay_isClampedToTheMonthLength() {
        assertEquals(LocalDate.of(2026, 2, 28), line(payDay = 31).payDateIn(YearMonth.of(2026, 2)))
        assertEquals(LocalDate.of(2028, 2, 29), line(payDay = 31).payDateIn(YearMonth.of(2028, 2)))
        assertEquals(LocalDate.of(2026, 4, 30), line(payDay = 30).payDateIn(YearMonth.of(2026, 4)))
        assertEquals(LocalDate.of(2026, 10, 31), line(payDay = 31).payDateIn(oct))
    }

    @Test
    fun payDateIn_fixedDay_isNotMovedOffWeekends() {
        // Oct 3 2026 is a Saturday, Oct 4 a Sunday.
        assertEquals(LocalDate.of(2026, 10, 3), line(payDay = 3).payDateIn(oct))
        assertEquals(LocalDate.of(2026, 10, 4), line(payDay = 4).payDateIn(oct))
    }

    @Test
    fun rowPayDate_isTheLatestOfItsActiveLines() {
        val a = line("a", payDay = 5, managedKey = MANAGED_NET_SALARY)
        val b = line("b", payDay = 20, managedKey = MANAGED_NET_SALARY)
        val row = incomeRowsIn(listOf(a, b), oct, 21).first()

        assertEquals(LocalDate.of(2026, 10, 20), row.payDateIn(oct))
    }

    // --- initialLineCursor ---

    @Test
    fun initialCursor_dayFive_beforeAndOnAfter() {
        assertEquals(YearMonth.of(2026, 9), initialLineCursor(line(payDay = 5), LocalDate.of(2026, 10, 4)))
        assertEquals(oct, initialLineCursor(line(payDay = 5), LocalDate.of(2026, 10, 5)))
        assertEquals(oct, initialLineCursor(line(payDay = 5), LocalDate.of(2026, 10, 28)))
    }

    @Test
    fun initialCursor_lastWorkingDay_beforeAndOnAfter() {
        assertEquals(YearMonth.of(2026, 9), initialLineCursor(line(), LocalDate.of(2026, 10, 29)))
        assertEquals(oct, initialLineCursor(line(), LocalDate.of(2026, 10, 30)))
        assertEquals(YearMonth.of(2026, 12), initialLineCursor(line(), LocalDate.of(2027, 1, 10)))
    }

    @Test
    fun initialCursor_leavesNothingDueToday() {
        for (payDay in listOf(null, 1, 5, 31)) {
            for (day in listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), LocalDate.of(2027, 1, 31))) {
                val l = line(payDay = payDay)
                assertTrue(lineMonthsDue(l, initialLineCursor(l, day), day).isEmpty())
            }
        }
    }

    // --- lineMonthsDue ---

    @Test
    fun monthsDue_nullCursor_isEmpty() {
        assertTrue(lineMonthsDue(line(), null, LocalDate.of(2026, 12, 31)).isEmpty())
    }

    @Test
    fun monthsDue_dayFive_versusLastWorkingDay_onTheSameToday() {
        val today = LocalDate.of(2026, 10, 10)
        val cursor = YearMonth.of(2026, 9)

        assertEquals(listOf(oct), lineMonthsDue(line(payDay = 5), cursor, today))
        assertTrue(lineMonthsDue(line(), cursor, today).isEmpty())
    }

    @Test
    fun monthsDue_isOldestFirst_andCappedToThree() {
        val today = LocalDate.of(2026, 10, 30)
        val due = lineMonthsDue(line(), YearMonth.of(2026, 1), today)

        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), oct), due)
        assertEquals(listOf(oct), lineMonthsDue(line(), YearMonth.of(2026, 1), today, cap = 1))
        assertEquals(10, lineMonthsDue(line(), YearMonth.of(2025, 12), today, cap = 100).size)
    }

    @Test
    fun monthsDue_cursorAtOrAfterToday_isEmpty() {
        val today = LocalDate.of(2026, 10, 30)

        assertTrue(lineMonthsDue(line(), oct, today).isEmpty())
        assertTrue(lineMonthsDue(line(), YearMonth.of(2027, 5), today).isEmpty())
    }

    @Test
    fun monthsDue_acrossYearEnd() {
        val due = lineMonthsDue(line(), YearMonth.of(2026, 11), LocalDate.of(2027, 1, 29))

        assertEquals(listOf(YearMonth.of(2026, 12), YearMonth.of(2027, 1)), due)
    }
}
