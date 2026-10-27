package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.InstantRange
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ChartSeriesTest {
    private val zone: ZoneId = ZoneOffset.UTC

    private fun at(
        month: Int,
        day: Int,
        hour: Int = 12,
    ): Instant = dayStart(month, day).plusHours(hour.toLong()).toInstant()

    private fun dayStart(
        month: Int,
        day: Int,
    ) = LocalDate.of(2026, month, day).atStartOfDay(zone)

    private fun expense(
        day: Int,
        cents: Long,
        month: Int = 10,
        refund: Boolean = false,
    ) = Expense("e$month-$day", "e", cents, BuiltInCategories.SHOPPING, at(month, day), null, refund)

    private fun income(
        day: Int,
        cents: Long,
        month: Int = 10,
    ) = Income("i$month-$day", "i", cents, at(month, day), null)

    private fun month(m: Int) =
        InstantRange(
            dayStart(m, 1).toInstant(),
            dayStart(m, 1).plusMonths(1).toInstant(),
        )

    @Test
    fun pastMonth_coversWholeMonth_cumulativeWithCarryForward() {
        val series =
            dailyNetSeries(
                expenses = listOf(expense(2, 300L, month = 9)),
                income = listOf(income(1, 1_000L, month = 9)),
                range = month(9),
                zone = zone,
                today = LocalDate.of(2026, 10, 20),
            )
        assertEquals(30, series.size)
        assertEquals(1_000L, series[0])
        assertEquals(700L, series[1])
        assertEquals(700L, series.last())
    }

    @Test
    fun currentMonth_stopsAtToday() {
        val series =
            dailyNetSeries(listOf(expense(3, 100L)), emptyList(), month(10), zone, LocalDate.of(2026, 10, 5))
        assertEquals(listOf(0L, 0L, -100L, -100L, -100L), series)
    }

    @Test
    fun refundsCountAsInflow() {
        val series =
            dailyNetSeries(
                listOf(expense(1, 500L), expense(2, 200L, refund = true)),
                emptyList(),
                month(10),
                zone,
                LocalDate.of(2026, 10, 3),
            )
        assertEquals(listOf(-500L, -300L, -300L), series)
    }

    @Test
    fun transactionsOutsideRange_areIgnored() {
        val series =
            dailyNetSeries(
                expenses = listOf(expense(28, 9_999L, month = 9)),
                income = listOf(income(1, 1_000L, month = 11)),
                range = month(10),
                zone = zone,
                today = LocalDate.of(2026, 10, 5),
            )
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), series)
    }

    @Test
    fun futureExpenseInRange_extendsSeriesAndIsInLastPoint() {
        val series =
            dailyNetSeries(
                listOf(expense(3, 100L), expense(10, 7_777L)),
                emptyList(),
                month(10),
                zone,
                LocalDate.of(2026, 10, 5),
            )
        assertEquals(10, series.size)
        assertEquals(-100L, series[4])
        assertEquals(-100L, series[8])
        assertEquals(-7_877L, series.last())
    }

    @Test
    fun futureIncomeInRange_extendsSeriesAndIsInLastPoint() {
        val series =
            dailyNetSeries(emptyList(), listOf(income(8, 500L)), month(10), zone, LocalDate.of(2026, 10, 5))
        assertEquals(8, series.size)
        assertEquals(0L, series[6])
        assertEquals(500L, series.last())
    }

    @Test
    fun futureEntryBeyondRangeEnd_isIgnored() {
        val series =
            dailyNetSeries(
                listOf(expense(2, 100L, month = 11)),
                listOf(income(1, 900L, month = 11)),
                month(10),
                zone,
                LocalDate.of(2026, 10, 5),
            )
        assertEquals(List(5) { 0L }, series)
    }

    @Test
    fun futureEntryOnLastDayOfRange_capsAtRangeEnd() {
        val series =
            dailyNetSeries(emptyList(), listOf(income(31, 200L)), month(10), zone, LocalDate.of(2026, 10, 5))
        assertEquals(31, series.size)
        assertEquals(200L, series.last())
    }

    @Test
    fun pastMonth_unaffectedByTodayOrLaterEntries() {
        val series =
            dailyNetSeries(
                listOf(expense(30, 100L, month = 9), expense(5, 50L, month = 10)),
                emptyList(),
                month(9),
                zone,
                LocalDate.of(2026, 10, 20),
            )
        assertEquals(30, series.size)
        assertEquals(-100L, series.last())
    }

    @Test
    fun lastPoint_equalsNetOfMixedInRangeCashFlow() {
        val expenses =
            listOf(
                expense(2, 1_000L),
                expense(4, 300L, refund = true),
                expense(15, 450L),
                expense(20, 999L, month = 9),
                expense(5, 11L, month = 11),
            )
        val incomes = listOf(income(1, 2_500L), income(12, 75L), income(1, 8L, month = 11))
        val series = dailyNetSeries(expenses, incomes, month(10), zone, LocalDate.of(2026, 10, 6))
        val expected = -1_000L + 300L - 450L + 2_500L + 75L
        assertEquals(15, series.size)
        assertEquals(expected, series.last())
    }

    @Test
    fun emptyData_isFlatZero() {
        val series = dailyNetSeries(emptyList(), emptyList(), month(9), zone, LocalDate.of(2026, 10, 20))
        assertEquals(List(30) { 0L }, series)
    }

    @Test
    fun singleDayWindow_returnsFlatTwoPoints() {
        val series = dailyNetSeries(listOf(expense(1, 250L)), emptyList(), month(10), zone, LocalDate.of(2026, 10, 1))
        assertEquals(listOf(-250L, -250L), series)
    }

    @Test
    fun rangeStartingTodayOrCustomOneDayRange_isTwoPoints() {
        val range = InstantRange(at(10, 5, 0), at(10, 6, 0))
        val series = dailyNetSeries(emptyList(), listOf(income(5, 40L)), range, zone, LocalDate.of(2026, 10, 20))
        assertEquals(listOf(40L, 40L), series)
    }

    @Test
    fun rollingRange_endingToday_includesTodaysTransactions() {
        val range = InstantRange(at(7, 20, 0), at(10, 21, 0))
        val today = LocalDate.of(2026, 10, 20)
        val startIncome = income(20, 5_000L, month = 7)
        val series = dailyNetSeries(listOf(expense(20, 100L)), listOf(startIncome), range, zone, today)
        assertEquals(93, series.size) // Jul 20 .. Oct 20 inclusive
        assertEquals(5_000L, series.first())
        assertEquals(4_900L, series.last())
    }

    @Test
    fun nonUtcZone_usesLocalDays() {
        val lisbon = ZoneId.of("Europe/Lisbon")
        val range =
            InstantRange(
                LocalDate.of(2026, 7, 1).atStartOfDay(lisbon).toInstant(),
                LocalDate.of(2026, 7, 4).atStartOfDay(lisbon).toInstant(),
            )
        // 23:30Z on Jul 1 is Jul 2 00:30 in Lisbon.
        val tx = Income("x", "x", 100L, Instant.parse("2026-07-01T23:30:00Z"), null)
        val series = dailyNetSeries(emptyList(), listOf(tx), range, lisbon, LocalDate.of(2026, 7, 30))
        assertEquals(listOf(0L, 100L, 100L), series)
    }

    @Test
    fun normalize_scalesMinToZeroMaxToOne() {
        assertEquals(listOf(0f, 0.5f, 1f), normalize(listOf(10L, 15L, 20L)))
    }

    @Test
    fun normalize_flatIsMiddle_emptyIsEmpty() {
        assertEquals(listOf(0.5f, 0.5f, 0.5f), normalize(listOf(7L, 7L, 7L)))
        assertEquals(listOf(0.5f), normalize(listOf(0L)))
        assertTrue(normalize(emptyList()).isEmpty())
    }

    @Test
    fun normalize_negativesOnly() {
        assertEquals(listOf(1f, 0f, 0.5f), normalize(listOf(-10L, -30L, -20L)))
    }

    @Test
    fun normalize_hugeValues_dontOverflow() {
        assertEquals(listOf(0f, 1f), normalize(listOf(Long.MIN_VALUE / 2, Long.MAX_VALUE / 2)))
    }

    @Test
    fun scrubIndex_roundsToNearest() {
        assertEquals(0, scrubIndex(0f, 100f, 5))
        assertEquals(2, scrubIndex(50f, 100f, 5))
        assertEquals(1, scrubIndex(24f, 100f, 5))
        assertEquals(1, scrubIndex(26f, 100f, 5)) // 1.04 -> 1
        assertEquals(2, scrubIndex(40f, 100f, 5)) // 1.6 -> 2
        assertEquals(4, scrubIndex(100f, 100f, 5))
    }

    @Test
    fun scrubIndex_clampsBothEnds() {
        assertEquals(0, scrubIndex(-50f, 100f, 5))
        assertEquals(4, scrubIndex(500f, 100f, 5))
    }

    @Test
    fun scrubIndex_degenerateInputs_returnZero() {
        assertEquals(0, scrubIndex(50f, 100f, 1))
        assertEquals(0, scrubIndex(50f, 100f, 0))
        assertEquals(0, scrubIndex(50f, 0f, 5))
        assertEquals(0, scrubIndex(50f, -10f, 5))
    }

    @Test
    fun resample_lengthAndEndpoints() {
        val out = resample(listOf(0f, 1f, 0.25f, 0.75f), 10)
        assertEquals(10, out.size)
        assertEquals(0f, out.first(), 0f)
        assertEquals(0.75f, out.last(), 1e-6f)
    }

    @Test
    fun resample_monotoneInputStaysMonotone() {
        val out = resample(listOf(0f, 0.1f, 0.4f, 0.4f, 1f), 23)
        for (i in 1 until out.size) assertTrue("at $i", out[i] >= out[i - 1] - 1e-6f)
    }

    @Test
    fun resample_linearUpsample_isExact() {
        assertArrayEquals(floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f), resample(listOf(0f, 1f), 5), 1e-6f)
    }

    @Test
    fun resample_sameCount_isIdentity() {
        val values = listOf(0f, 0.3f, 1f, 0.6f)
        assertArrayEquals(values.toFloatArray(), resample(values, 4), 1e-6f)
    }

    @Test
    fun resample_shortInputs_fillWithValueOrMiddle() {
        assertArrayEquals(floatArrayOf(0.2f, 0.2f, 0.2f), resample(listOf(0.2f), 3), 0f)
        assertArrayEquals(floatArrayOf(0.5f, 0.5f), resample(emptyList(), 2), 0f)
    }
}
