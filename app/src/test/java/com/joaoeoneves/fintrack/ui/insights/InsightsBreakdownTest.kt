package com.joaoeoneves.fintrack.ui.insights

import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.EnvelopeProgress
import com.joaoeoneves.fintrack.domain.model.EnvelopesSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [InsightsBreakdown.hasEnvelopes]. */
class InsightsBreakdownTest {
    private fun breakdown(envelopes: EnvelopesSummary) = InsightsBreakdown(emptyList(), emptyList(), false, envelopes)

    private fun progress() =
        EnvelopeProgress(
            Envelope("g1", "Food", 0),
            amountCents = null,
            spentCents = 0L,
            rows = emptyList(),
        )

    @Test
    fun default_hasNoEnvelopes() {
        assertFalse(InsightsBreakdown(emptyList(), emptyList(), hasExpenses = false).hasEnvelopes)
    }

    @Test
    fun onlyUnenvelopedRows_hasNoEnvelopes() {
        assertFalse(breakdown(EnvelopesSummary(emptyList(), emptyList(), 0L)).hasEnvelopes)
    }

    @Test
    fun anyRealEnvelope_hasEnvelopes() {
        assertTrue(breakdown(EnvelopesSummary(listOf(progress()), emptyList(), 0L)).hasEnvelopes)
    }
}
