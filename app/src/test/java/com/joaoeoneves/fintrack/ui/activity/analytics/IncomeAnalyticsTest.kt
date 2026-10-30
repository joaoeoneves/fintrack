package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.IncomeSourceHistory
import com.joaoeoneves.fintrack.domain.model.IncomeSourceKey
import com.joaoeoneves.fintrack.domain.model.MonthTotals
import com.joaoeoneves.fintrack.domain.model.SalaryComponent
import com.joaoeoneves.fintrack.domain.model.SourceMonth
import com.joaoeoneves.fintrack.domain.model.YearToDate
import com.joaoeoneves.fintrack.domain.usecase.MonthlyHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class IncomeAnalyticsTest {
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)
    private val months = listOf(sep, oct)
    private val salary = IncomeSourceKey.Row(SalaryComponent.SALARY, null, null)
    private val gift = IncomeSourceKey.Named("gift")

    private fun source(
        key: IncomeSourceKey,
        vararg values: Pair<Long, Long?>,
    ) = IncomeSourceHistory(
        key,
        "label",
        months.zip(values.toList()).map { (m, v) -> SourceMonth(m, v.first, v.second) },
    )

    private fun history(
        sources: List<IncomeSourceHistory>,
        period: List<YearMonth> = months,
        income: List<Long> = listOf(0L, 0L),
        today: YearMonth? = null,
    ) = ActivityHistory(
        period,
        period.size == 1,
        MonthlyHistory(
            months.zip(income).map { (m, i) -> MonthTotals(m, i, 0L, 0L) },
            emptyMap(),
            sources,
            YearToDate(2026, 0L, 0L, 0L),
        ),
        today,
    )

    @Test
    fun analytics_sumsByFundingMonth_overThePeriodOnly() {
        val h =
            history(
                listOf(source(salary, 100L to 100L, 100L to 100L)),
                period = listOf(oct),
                income = listOf(100L, 100L),
            )
        val a = h.incomeAnalytics()
        assertEquals(100L, a.receivedCents)
        assertEquals(100L, a.expectedCents)
        assertNull(a.missingCents)
        assertEquals(100L, a.sources.single().receivedCents)
    }

    @Test
    fun analytics_noExpectation_expectedAndMissingNull() {
        val h = history(listOf(source(gift, 50L to null, 0L to null)), income = listOf(50L, 0L))
        val a = h.incomeAnalytics()
        assertNull(a.expectedCents)
        assertNull(a.missingCents)
        assertEquals(50L, a.receivedCents)
    }

    @Test
    fun analytics_unplannedIncomeDoesNotOffsetTheMissing() {
        val h =
            history(
                listOf(source(salary, 80L to 100L, 80L to 100L), source(gift, 500L to null, 0L to null)),
                income = listOf(580L, 80L),
            )
        val a = h.incomeAnalytics()
        assertEquals(660L, a.receivedCents)
        assertEquals(200L, a.expectedCents)
        // Salary is 40 short over both months; the unplanned gift does not offset it.
        assertEquals(40L, a.missingCents)
        assertEquals(2, a.sources.size)
        assertNull(a.sources[1].expectedCents)
    }

    @Test
    fun missing_overReceived_isNull() {
        val h = history(listOf(source(salary, 0L to 0L, 140_000L to 136_273L)), income = listOf(0L, 140_000L))

        val a = h.incomeAnalytics()

        assertEquals(140_000L, a.receivedCents)
        assertEquals(136_273L, a.expectedCents)
        assertNull(a.missingCents)
    }

    @Test
    fun missing_belowExpected_isTheShortfall() {
        val h = history(listOf(source(salary, 0L to 0L, 100_000L to 136_273L)), income = listOf(0L, 100_000L))

        assertEquals(36_273L, h.incomeAnalytics().missingCents)
    }

    @Test
    fun missing_overReceivedSource_doesNotOffsetAnotherSourcesShortfall() {
        val bonus = IncomeSourceKey.Row(SalaryComponent.HOLIDAY, null, null)
        val h =
            history(
                listOf(source(salary, 150L to 100L, 0L to 100L), source(bonus, 0L to 50L, 0L to 0L)),
                income = listOf(150L, 0L),
            )

        val a = h.incomeAnalytics()

        assertEquals(150L, a.receivedCents)
        assertEquals(250L, a.expectedCents)
        // Salary expects 200 and got 150 (50 short); the bonus expects 50 and got nothing; the totals add up to 100.
        assertEquals(100L, a.missingCents)
    }

    @Test
    fun missing_sumsMonthsPerSourceBeforeClamping() {
        // Within one source, an over-received month offsets a short one.
        val h = history(listOf(source(salary, 90L to 100L, 110L to 100L)), income = listOf(90L, 110L))

        val a = h.incomeAnalytics()

        assertEquals(200L, a.receivedCents)
        assertEquals(200L, a.expectedCents)
        assertNull(a.missingCents)
    }

    @Test
    fun missing_futureMonthsCountAsStillToReceive() {
        val h =
            history(
                listOf(source(salary, 100L to 100L, 0L to 100L)),
                income = listOf(100L, 0L),
                today = sep,
            )

        assertEquals(100L, h.incomeAnalytics().missingCents)
    }

    @Test
    fun pastShort_onlyCountsMonthsBeforeToday() {
        val h = history(listOf(source(salary, 40L to 100L, 0L to 100L)), income = listOf(40L, 0L), today = oct)
        val row = h.incomeAnalytics().sources.single()

        // September is past and short by 60; October (current) is never short.
        assertEquals(60L, row.pastShortCents)
        assertEquals(160L, h.incomeAnalytics().missingCents)
    }

    @Test
    fun pastShort_currentAndFutureMonthsNeverShort() {
        val h = history(listOf(source(salary, 100L to 100L, 0L to 100L)), income = listOf(100L, 0L), today = oct)
        assertEquals(
            0L,
            h
                .incomeAnalytics()
                .sources
                .single()
                .pastShortCents,
        )
        val allFuture = history(listOf(source(salary, 0L to 100L, 0L to 100L)), today = sep)
        assertEquals(
            0L,
            allFuture
                .incomeAnalytics()
                .sources
                .single()
                .pastShortCents,
        )
    }

    @Test
    fun pastShort_unknownToday_treatsEveryMonthAsPast() {
        val h = history(listOf(source(salary, 0L to 100L, 0L to 100L)))
        assertEquals(
            200L,
            h
                .incomeAnalytics()
                .sources
                .single()
                .pastShortCents,
        )
    }

    @Test
    fun pastShort_overReceivedPastMonthsClampToZero() {
        val h = history(listOf(source(salary, 150L to 100L, 0L to 100L)), income = listOf(150L, 0L), today = oct)
        assertEquals(
            0L,
            h
                .incomeAnalytics()
                .sources
                .single()
                .pastShortCents,
        )
    }

    @Test
    fun incomeSeries_sourceWithMonthsBeyondTheWindow_alignsToTheWindow() {
        val nov = YearMonth.of(2026, 11)
        val extended =
            IncomeSourceHistory(
                salary,
                "label",
                listOf(SourceMonth(sep, 10L, 10L), SourceMonth(oct, 20L, 20L), SourceMonth(nov, 0L, 20L)),
            )
        val h = history(listOf(extended), income = listOf(10L, 20L))

        assertEquals(listOf(10L, 20L), h.incomeSeries(salary.encode()))
        assertEquals(h.monthly.months.size, h.incomeSeries(salary.encode()).size)
    }

    @Test
    fun analytics_sourceWithNothingInThePeriod_isDropped() {
        val h = history(listOf(source(gift, 50L to null, 0L to null)), period = listOf(oct))
        assertEquals(emptyList<IncomeSourceRow>(), h.incomeAnalytics().sources)
    }

    @Test
    fun incomeSeries_totalOrSelectedSource_staleKeyFallsBackToTotal() {
        val h = history(listOf(source(gift, 50L to null, 7L to null)), income = listOf(60L, 9L))
        assertEquals(listOf(60L, 9L), h.incomeSeries(null))
        assertEquals(listOf(50L, 7L), h.incomeSeries(gift.encode()))
        assertEquals(listOf(60L, 9L), h.incomeSeries("named:gone"))
    }

    @Test
    fun encode_isStableAndDistinguishesKeys_roundTripsThroughIncomeSeries() {
        val keys =
            listOf(
                salary,
                IncomeSourceKey.Row(null, null, "line-1"),
                IncomeSourceKey.Row(SalaryComponent.SALARY, "extra", null),
                gift,
                IncomeSourceKey.Named(""),
            )
        assertEquals(keys.size, keys.map { it.encode() }.toSet().size)
        assertEquals("row:SALARY||", salary.encode())
        assertEquals("named:gift", gift.encode())
        assertNotEquals(IncomeSourceKey.Row(null, null, "a").encode(), IncomeSourceKey.Row(null, "a", null).encode())
        keys.forEachIndexed { i, key ->
            val h = history(keys.mapIndexed { j, k -> source(k, (j + 1L) * 10 to null, (j + 1L) * 10 to null) })
            assertEquals(List(2) { (i + 1L) * 10 }, h.incomeSeries(key.encode()))
        }
    }
}
