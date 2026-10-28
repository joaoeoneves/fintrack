package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.EnvelopeProgress
import com.joaoeoneves.fintrack.domain.model.EnvelopesSummary
import com.joaoeoneves.fintrack.ui.dashboard.CategoryTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ActivityBreakdown.hasEnvelopes] and [ActivityBreakdown.isEmpty]. */
class ActivityBreakdownTest {
    private fun breakdown(envelopes: EnvelopesSummary) = ActivityBreakdown(emptyList(), emptyList(), false, envelopes)

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
        assertFalse(ActivityBreakdown(emptyList(), emptyList(), hasExpenses = false).hasEnvelopes)
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
    fun isEmpty_onlyWithoutSpendingAndWithoutPlan() {
        val slice = CategoryTotal(BuiltInCategories.FOOD, 100L)

        assertTrue(ActivityBreakdown(emptyList(), emptyList(), hasExpenses = false).isEmpty)
        assertFalse(ActivityBreakdown(emptyList(), emptyList(), hasExpenses = true).isEmpty)
        assertFalse(ActivityBreakdown(emptyList(), listOf(slice), hasExpenses = false).isEmpty)
    }
}
