package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // --- commonPayDateIn ---

    private fun rowsOf(vararg lines: PlannedIncome) = incomeRowsIn(lines.toList(), oct, 21)

    @Test
    fun commonPayDate_sameDayAcrossRows_isThatDay() {
        val rows = rowsOf(line("a", payDay = 15), line("b", payDay = 15))

        assertEquals(LocalDate.of(2026, 10, 15), commonPayDateIn(rows, oct))
    }

    @Test
    fun commonPayDate_singleRow_isItsDay() {
        assertEquals(LocalDate.of(2026, 10, 30), commonPayDateIn(rowsOf(line("a")), oct))
    }

    @Test
    fun commonPayDate_differentDays_isNull() {
        val rows = rowsOf(line("a", payDay = 5), line("b", payDay = 20))

        assertNull(commonPayDateIn(rows, oct))
    }

    @Test
    fun commonPayDate_noRows_isNull() {
        assertNull(commonPayDateIn(emptyList(), oct))
    }

    @Test
    fun commonPayDate_ignoresRowsThatPayNothing() {
        val zero = PlannedIncome("z", "Zero", 0L, 0, payDay = 3)
        val rows = rowsOf(line("a", payDay = 15), zero)

        assertEquals(LocalDate.of(2026, 10, 15), commonPayDateIn(rows, oct))
        assertNull(commonPayDateIn(rowsOf(zero), oct))
    }

    // --- payDayEarlierIfClosed ---

    private fun early(payDay: Int?): PlannedIncome = PlannedIncome("e", "Early", 100_000L, 0, payDay = payDay, payDayEarlierIfClosed = true)

    private fun d(
        y: Int,
        m: Int,
        day: Int,
    ) = LocalDate.of(y, m, day)

    @Test
    fun earlier_weekend_movesToFriday() {
        // Oct 3 2026 Saturday, Oct 4 Sunday; Oct 6 is a Tuesday and stays (Oct 5 is a holiday).
        assertEquals(d(2026, 10, 2), early(3).payDateIn(oct))
        assertEquals(d(2026, 10, 2), early(4).payDateIn(oct))
        assertEquals(d(2026, 10, 6), early(6).payDateIn(oct))
    }

    @Test
    fun earlier_holidaysOnWeekdays_moveBack() {
        assertEquals(d(2026, 12, 24), early(25).payDateIn(YearMonth.of(2026, 12)))
        assertEquals(d(2026, 12, 31), early(1).payDateIn(YearMonth.of(2027, 1)))
        // 25 April 2028 is a Tuesday.
        assertEquals(d(2028, 4, 24), early(25).payDateIn(YearMonth.of(2028, 4)))
    }

    @Test
    fun earlier_workingDay_isUnchanged() {
        assertEquals(d(2026, 10, 15), early(15).payDateIn(oct))
    }

    @Test
    fun earlier_day31_isClampedBeforeShifting() {
        // Feb 2026 has 28 days and the 28th is a Saturday; Oct 31 2026 is a Saturday too.
        assertEquals(d(2026, 2, 27), early(31).payDateIn(YearMonth.of(2026, 2)))
        assertEquals(d(2026, 10, 30), early(31).payDateIn(oct))
        // Apr 30 2028 is a Sunday.
        assertEquals(d(2028, 4, 28), early(31).payDateIn(YearMonth.of(2028, 4)))
    }

    @Test
    fun earlier_day1OnWeekend_shiftsIntoThePreviousMonth() {
        assertEquals(d(2026, 7, 31), early(1).payDateIn(YearMonth.of(2026, 8)))
        assertEquals(d(2026, 2, 27), early(1).payDateIn(YearMonth.of(2026, 3)))
        // Nov 1 2026 is a Sunday and All Saints' Day.
        assertEquals(d(2026, 10, 30), early(1).payDateIn(YearMonth.of(2026, 11)))
    }

    @Test
    fun earlier_nullPayDay_stillTheLastWorkingDay() {
        assertEquals(lastWorkingDayOf(oct), early(null).payDateIn(oct))
    }

    @Test
    fun flagOff_neverChangesAnyDate() {
        for (m in 1..12) {
            val month = YearMonth.of(2026, m)
            for (day in listOf(1, 3, 4, 25, 31)) {
                val expected = month.atDay(day.coerceAtMost(month.lengthOfMonth()))
                assertEquals(expected, line(payDay = day).payDateIn(month))
            }
        }
    }

    @Test
    fun previousWorkingDayOrSame_skipsHolidayWeekendRuns() {
        assertEquals(d(2026, 10, 2), previousWorkingDayOrSame(d(2026, 10, 4)))
        assertEquals(d(2026, 10, 2), previousWorkingDayOrSame(d(2026, 10, 2)))
        // Good Friday 2026-04-03 and the weekend after it: Easter Monday is not a national holiday.
        assertEquals(d(2026, 4, 2), previousWorkingDayOrSame(d(2026, 4, 5)))
    }

    @Test
    fun initialCursor_earlierIfClosed_countsNextMonthWhenItsShiftedDateHasPassed() {
        val l = early(1)

        // Nov 2026's pay day is Fri Oct 30.
        assertEquals(oct, initialLineCursor(l, d(2026, 10, 29)))
        assertEquals(YearMonth.of(2026, 11), initialLineCursor(l, d(2026, 10, 30)))
        assertEquals(YearMonth.of(2027, 1), initialLineCursor(l, d(2026, 12, 31)))
    }

    @Test
    fun monthsDue_earlierIfClosed_includesNextMonthOnceItsShiftedDateIsReached() {
        val l = early(1)
        val cursor = YearMonth.of(2026, 9)

        assertEquals(listOf(oct), lineMonthsDue(l, cursor, d(2026, 10, 29)))
        assertEquals(listOf(oct, YearMonth.of(2026, 11)), lineMonthsDue(l, cursor, d(2026, 10, 30)))
        assertTrue(lineMonthsDue(l, YearMonth.of(2026, 11), d(2026, 10, 30)).isEmpty())
    }

    @Test
    fun monthsDue_flagOff_neverReachesIntoNextMonth() {
        val l = line(payDay = 1)

        assertEquals(listOf(oct), lineMonthsDue(l, YearMonth.of(2026, 9), d(2026, 10, 31)))
    }

    @Test
    fun simulatedDailyRuns_payEveryMonthExactlyOnce_onItsPayDate() {
        val lines = listOf(early(1), early(31), early(25), early(null), line(payDay = 1), line())
        for (l in lines) {
            val start = d(2026, 6, 15)
            var cursor: YearMonth? = initialLineCursor(l, start)
            val paid = mutableListOf<YearMonth>()
            generateSequence(start) { it.plusDays(1) }.takeWhile { it <= d(2028, 2, 15) }.forEach { today ->
                val due = lineMonthsDue(l, cursor, today)
                due.forEach { month ->
                    assertEquals("${l.payDay}/${l.payDayEarlierIfClosed} $month", l.payDateIn(month), today)
                }
                paid += due
                if (due.isNotEmpty()) cursor = due.last()
            }
            assertEquals(paid.distinct(), paid)
            assertTrue(paid.zipWithNext().all { (a, b) -> a.plusMonths(1) == b })
            assertTrue(paid.size >= 20)
        }
    }

    @Test
    fun managedLine_inheritsPayDayEarlierIfClosedFromTheSalaryLine() {
        val salary =
            PlannedIncome(
                "s",
                "Salary",
                100_000L,
                0,
                managedKey = MANAGED_NET_SALARY,
                payDay = 1,
                payDayEarlierIfClosed = true,
            )
        val profile =
            SalaryProfile(baseGrossCents = 200_000, irsBasePpm = 100_000, mealRatePerDayCents = 600)
        val lines = salaryProfileLines(profile, listOf(salary))

        assertTrue(lines.size >= 3)
        assertTrue(lines.all { it.payDay == 1 && it.payDayEarlierIfClosed })
    }
}
