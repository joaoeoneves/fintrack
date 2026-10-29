package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** The month's headline numbers: income minus recurring, envelopes and saving (no rollover from earlier months). */
class MonthFiguresTest {
    private fun summary(
        free: Long = 0L,
        income: Long = 0L,
        recurring: Long = 0L,
        saving: Long = 0L,
    ) = EnvelopesSummary(
        envelopes = emptyList(),
        unenveloped = emptyList(),
        freeCents = free,
        baseCents = income,
        committedCents = 0L,
        savingCommittedCents = saving,
        incomeCents = income,
        recurringCents = recurring,
    )

    @Test
    fun monthFiguresOf_addUp_andRecurringIsNotCountedInEnvelopes() {
        val figures =
            monthFiguresOf(summary(free = 40_000L, income = 100_000L, recurring = 30_000L, saving = 10_000L))

        assertEquals(100_000L, figures.incomeCents)
        assertEquals(30_000L, figures.recurringCents)
        // 100 - 40 free - 10 saving - 30 recurring.
        assertEquals(20_000L, figures.envelopesCents)
        assertEquals(10_000L, figures.savingCents)
        assertEquals(100_000L - 30_000L - 20_000L - 10_000L, figures.availableCents)
        assertEquals(40_000L, figures.availableCents)
    }

    @Test
    fun availableCents_canBeNegative() {
        val figures = MonthFigures(10_000L, 50_000L, 0L, 0L)

        assertEquals(-40_000L, figures.availableCents)
    }

    @Test
    fun monthFiguresOf_emptySummary_isAllZero() {
        val figures = monthFiguresOf(EnvelopesSummary(emptyList(), emptyList(), 0L))

        assertEquals(MonthFigures(0L, 0L, 0L, 0L), figures)
        assertEquals(0L, figures.availableCents)
    }
}
