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
    ) = ActivityHistory(
        period,
        period.size == 1,
        MonthlyHistory(
            months.zip(income).map { (m, i) -> MonthTotals(m, i, 0L, 0L) },
            emptyMap(),
            sources,
            YearToDate(2026, 0L, 0L, 0L),
        ),
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
        assertEquals(0L, a.differenceCents)
        assertEquals(100L, a.sources.single().receivedCents)
    }

    @Test
    fun analytics_noExpectation_expectedAndDifferenceNull() {
        val h = history(listOf(source(gift, 50L to null, 0L to null)), income = listOf(50L, 0L))
        val a = h.incomeAnalytics()
        assertNull(a.expectedCents)
        assertNull(a.differenceCents)
        assertEquals(50L, a.receivedCents)
    }

    @Test
    fun analytics_differenceOnlyOverExpectingSources() {
        val h =
            history(
                listOf(source(salary, 80L to 100L, 80L to 100L), source(gift, 500L to null, 0L to null)),
                income = listOf(580L, 80L),
            )
        val a = h.incomeAnalytics()
        assertEquals(660L, a.receivedCents)
        assertEquals(200L, a.expectedCents)
        // Unplanned gift income does not hide the 40 shortfall.
        assertEquals(-40L, a.differenceCents)
        assertEquals(2, a.sources.size)
        assertNull(a.sources[1].expectedCents)
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
