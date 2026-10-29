package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.model.AmountVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class AmountHistoryNoteTest {
    private fun v(
        m: Int,
        cents: Long?,
    ) = AmountVersion(YearMonth.of(2026, m), cents)

    @Test
    fun noVersions_orOne_isNull() {
        assertNull(amountHistoryNote(emptyList()))
        assertNull(amountHistoryNote(listOf(v(3, 100))))
    }

    @Test
    fun previousAmountNull_isNull() {
        assertNull(amountHistoryNote(listOf(v(3, null), v(9, 300))))
    }

    @Test
    fun untilIsTheLatestFromMinusOneMonth() {
        assertEquals(
            AmountHistoryNote(30_000L, YearMonth.of(2026, 8)),
            amountHistoryNote(listOf(v(5, 30_000), v(9, 40_000))),
        )
    }

    @Test
    fun usesTheLastTwoVersions_evenWhenUnsorted() {
        val note = amountHistoryNote(listOf(v(9, 400), v(1, 100), v(5, 300)))
        assertEquals(AmountHistoryNote(300L, YearMonth.of(2026, 8)), note)
    }

    @Test
    fun untilCrossesTheYearBoundary() {
        val versions = listOf(AmountVersion(YearMonth.of(2025, 6), 100L), AmountVersion(YearMonth.of(2026, 1), 200L))
        assertEquals(YearMonth.of(2025, 12), amountHistoryNote(versions)?.until)
    }
}
