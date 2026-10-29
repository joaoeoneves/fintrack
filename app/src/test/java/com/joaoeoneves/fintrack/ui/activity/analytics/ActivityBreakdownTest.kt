package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.EnvelopeProgress
import com.joaoeoneves.fintrack.domain.model.EnvelopesSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ActivityBreakdown.hasEnvelopes] and [ActivityBreakdown.isEmpty]. */
class ActivityBreakdownTest {
    private fun breakdown(envelopes: EnvelopesSummary) = ActivityBreakdown(emptyList(), false, envelopes)

    private fun progress() =
        EnvelopeProgress(
            Envelope("g1", "Food", 0),
            amountCents = null,
            spentCents = 0L,
            rows = emptyList(),
        )

    @Test
    fun noEnvelopeConstant_isTheEmptyString() {
        assertEquals("", NO_ENVELOPE)
    }

    @Test
    fun default_hasNoEnvelopes() {
        assertFalse(ActivityBreakdown(emptyList(), hasExpenses = false).hasEnvelopes)
    }

    @Test
    fun onlyUnenvelopedRows_hasNoEnvelopes() {
        assertFalse(breakdown(EnvelopesSummary(emptyList(), emptyList(), 0L)).hasEnvelopes)
    }

    @Test
    fun anyRealEnvelope_hasEnvelopes() {
        assertTrue(breakdown(EnvelopesSummary(listOf(progress()), emptyList(), 0L)).hasEnvelopes)
    }

    @Test
    fun isEmpty_onlyWithoutSpending() {
        assertTrue(ActivityBreakdown(emptyList(), hasExpenses = false).isEmpty)
        assertFalse(ActivityBreakdown(emptyList(), hasExpenses = true).isEmpty)
    }
}
