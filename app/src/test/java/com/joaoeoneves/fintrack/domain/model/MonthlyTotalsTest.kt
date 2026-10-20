package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/** [monthlyTotalsOf]: month filling, ordering, bucketing by zone, and refunds. */
class MonthlyTotalsTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val july = YearMonth.of(2026, 7)
    private val august = YearMonth.of(2026, 8)
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)

    private fun noon(month: YearMonth) = month.atDay(15).atTime(12, 0).toInstant(ZoneOffset.UTC)

    @Test
    fun emptyMonthsAreFilledWithZeros_ascending() {
        val totals =
            monthlyTotalsOf(
                expenses = listOf(testExpense("oct", 1_000L, noon(october)), testExpense("jul", 500L, noon(july))),
                income = emptyList(),
                from = july,
                to = october,
                zone = utc,
            )

        assertEquals(listOf(july, august, september, october), totals.map { it.month })
        assertEquals(listOf(500L, 0L, 0L, 1_000L), totals.map { it.summary.spendingCents })
        assertEquals(cashFlowSummaryOf(emptyList(), emptyList()), totals[1].summary)
    }

    @Test
    fun singleMonth() {
        val totals = monthlyTotalsOf(emptyList(), listOf(testIncome("i", 9_000L, noon(october))), october, october, utc)

        assertEquals(1, totals.size)
        assertEquals(october, totals.single().month)
        assertEquals(9_000L, totals.single().summary.incomeCents)
    }

    @Test
    fun fromAfterTo_isEmpty() {
        val totals = monthlyTotalsOf(listOf(testExpense("e", 1L, noon(october))), emptyList(), october, july, utc)

        assertTrue(totals.isEmpty())
    }

    @Test
    fun transactionsOutsideTheMonths_areIgnored() {
        val totals = monthlyTotalsOf(listOf(testExpense("e", 1_000L, noon(july))), emptyList(), august, september, utc)

        assertEquals(listOf(0L, 0L), totals.map { it.summary.spendingCents })
    }

    @Test
    fun midnightOnTheFirst_belongsToTheNewMonth() {
        val totals =
            monthlyTotalsOf(
                expenses =
                    listOf(
                        testExpense("first", 100L, Instant.parse("2026-10-01T00:00:00Z")),
                        testExpense("last", 7L, Instant.parse("2026-09-30T23:59:59.999Z")),
                    ),
                income = emptyList(),
                from = september,
                to = october,
                zone = utc,
            )

        assertEquals(listOf(7L, 100L), totals.map { it.summary.spendingCents })
    }

    @Test
    fun bucketingUsesTheZone_lisbonSummerTime() {
        // 23:30 UTC on 30 Sep is 00:30 WEST (UTC+1) on 1 Oct in Lisbon.
        val totals =
            monthlyTotalsOf(
                expenses = listOf(testExpense("e", 100L, Instant.parse("2026-09-30T23:30:00Z"))),
                income = emptyList(),
                from = september,
                to = october,
                zone = ZoneId.of("Europe/Lisbon"),
            )

        assertEquals(listOf(0L, 100L), totals.map { it.summary.spendingCents })
    }

    @Test
    fun refundsAreNettedPerMonth() {
        val totals =
            monthlyTotalsOf(
                expenses =
                    listOf(
                        testExpense("sep", 5_000L, noon(september)),
                        testExpense("octRefund", 2_000L, noon(october), isRefund = true),
                    ),
                income = emptyList(),
                from = september,
                to = october,
                zone = utc,
            )

        assertEquals(listOf(5_000L, -2_000L), totals.map { it.summary.spendingCents })
    }
}
