package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.EnvelopeProgress
import com.joaoeoneves.fintrack.domain.model.EnvelopesSummary
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.PlannedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure mapping from envelope progress to the Plan tab's cards. */
class EnvelopeCardUiTest {
    private fun row(
        planned: Long?,
        source: PlannedSource,
        spent: Long = 0L,
        category: com.joaoeoneves.fintrack.domain.model.Category = BuiltInCategories.FOOD,
    ) = CategoryBudget(
        category = category,
        limitCents = if (source == PlannedSource.BUDGET) planned else null,
        spentCents = spent,
        planned = planned,
        plannedSource = source,
    )

    private fun progress(
        amount: Long?,
        spent: Long,
        rows: List<CategoryBudget>,
        own: Long? = null,
        line: PlannedIncome? = null,
    ) = EnvelopeProgress(
        envelope = Envelope("e1", "Home", 0, amountCents = own),
        amountCents = amount,
        spentCents = spent,
        rows = rows,
        fundingCents = line?.amountCents,
        fundingLine = line,
    )

    @Test
    fun ownAmount_isBudgeted() {
        val card = envelopeCardOf(progress(20_000L, 5_000L, emptyList(), own = 20_000L))

        card as EnvelopeCardUi.Budgeted
        assertEquals("e1", card.id)
        assertEquals("Home", card.name)
        assertEquals(20_000L, card.amountCents)
        assertEquals(15_000L, card.remainingCents)
        assertFalse(card.isOver)
    }

    @Test
    fun budgetRow_isBudgeted_andOverWhenSpentExceeds() {
        val card = envelopeCardOf(progress(10_000L, 10_001L, listOf(row(10_000L, PlannedSource.BUDGET, 10_001L))))

        card as EnvelopeCardUi.Budgeted
        assertTrue(card.isOver)
        assertEquals(-1L, card.remainingCents)
    }

    @Test
    fun atExactAmount_isNotOver() {
        val card = envelopeCardOf(progress(10_000L, 10_000L, listOf(row(10_000L, PlannedSource.BUDGET, 10_000L))))

        assertFalse((card as EnvelopeCardUi.Budgeted).isOver)
    }

    @Test
    fun mixedBudgetAndRecurring_isBudgeted() {
        val rows = listOf(row(8_000L, PlannedSource.BUDGET), row(4_000L, PlannedSource.RECURRING))

        val card = envelopeCardOf(progress(12_000L, 0L, rows))

        assertTrue(card is EnvelopeCardUi.Budgeted)
        assertEquals(12_000L, (card as EnvelopeCardUi.Budgeted).amountCents)
    }

    @Test
    fun recurringOnly_isBillsOnly() {
        val card = envelopeCardOf(progress(6_000L, 2_000L, listOf(row(6_000L, PlannedSource.RECURRING, 2_000L))))

        card as EnvelopeCardUi.BillsOnly
        assertEquals(6_000L, card.plannedCents)
        assertEquals(2_000L, card.paidCents)
    }

    @Test
    fun billsOnly_stillBillsOnlyWhenSpentExceedsPlanned() {
        val card = envelopeCardOf(progress(6_000L, 9_000L, listOf(row(6_000L, PlannedSource.RECURRING, 9_000L))))

        card as EnvelopeCardUi.BillsOnly
        assertEquals(9_000L, card.paidCents)
        assertEquals(6_000L, card.plannedCents)
    }

    @Test
    fun nothingPlanned_isTracking_withSpend() {
        val card = envelopeCardOf(progress(null, 3_000L, listOf(row(null, PlannedSource.NONE, 3_000L))))

        card as EnvelopeCardUi.Tracking
        assertEquals(3_000L, card.spentCents)
    }

    @Test
    fun noRowsAndNoAmount_isTrackingWithZero() {
        val card = envelopeCardOf(progress(null, 0L, emptyList()))

        assertEquals(EnvelopeCardUi.Tracking("e1", "Home", 0L), card)
    }

    @Test
    fun fundingAndOverflow_areCarried() {
        val salary = PlannedIncome(id = "s", label = "Salary", amountCents = 25_000L, order = 0)

        val card = envelopeCardOf(progress(30_000L, 0L, emptyList(), own = 30_000L, line = salary))

        assertEquals(salary, card.fundingLine)
        assertEquals(5_000L, card.overflowCents)
    }

    @Test
    fun fundingCents_isCarriedOnEveryCardKind_andZeroWhenUnfunded() {
        val salary = PlannedIncome(id = "s", label = "Salary", amountCents = 25_000L, order = 0)
        val bills = listOf(row(10_000L, PlannedSource.RECURRING))

        val budgeted = envelopeCardOf(progress(30_000L, 0L, emptyList(), own = 30_000L, line = salary))
        val billsOnly = envelopeCardOf(progress(10_000L, 0L, bills, line = salary))
        val tracking = envelopeCardOf(progress(null, 500L, emptyList(), line = salary))
        val unfunded = envelopeCardOf(progress(30_000L, 0L, emptyList(), own = 30_000L))

        assertTrue(budgeted is EnvelopeCardUi.Budgeted)
        assertTrue(billsOnly is EnvelopeCardUi.BillsOnly)
        assertTrue(tracking is EnvelopeCardUi.Tracking)
        assertEquals(25_000L, budgeted.fundingCents)
        assertEquals(25_000L, billsOnly.fundingCents)
        assertEquals(25_000L, tracking.fundingCents)
        assertEquals(0L, unfunded.fundingCents)
    }

    @Test
    fun cardsDifferingOnlyByFundingCents_areNotEqual() {
        val a = EnvelopeCardUi.Budgeted("e1", "Home", 100L, 0L, fundingCents = 50L)
        val b = a.copy(fundingCents = 60L)

        assertEquals(a, a.copy())
        assertFalse(a == b)
    }

    @Test
    fun unfunded_hasNoFundingAndNoOverflow() {
        val card = envelopeCardOf(progress(30_000L, 0L, emptyList(), own = 30_000L))

        assertNull(card.fundingLine)
        assertEquals(0L, card.overflowCents)
    }

    @Test
    fun unenveloped_emptyIsNull() {
        assertNull(unenvelopedCardOf(EnvelopesSummary(emptyList(), emptyList(), 0L)))
    }

    @Test
    fun unenveloped_withPlanned_isBudgetedWithNullIdAndName() {
        val summary =
            EnvelopesSummary(
                emptyList(),
                listOf(row(4_000L, PlannedSource.BUDGET, 1_000L), row(1_000L, PlannedSource.RECURRING, 500L)),
                0L,
            )

        val card = unenvelopedCardOf(summary)

        assertEquals(EnvelopeCardUi.Budgeted(null, null, 5_000L, 1_500L), card)
    }

    @Test
    fun unenveloped_spentOnly_isTracking() {
        val summary = EnvelopesSummary(emptyList(), listOf(row(null, PlannedSource.NONE, 700L)), 0L)

        assertEquals(EnvelopeCardUi.Tracking(null, null, 700L), unenvelopedCardOf(summary))
    }

    @Test
    fun unenveloped_plannedZero_isTracking() {
        val summary = EnvelopesSummary(emptyList(), listOf(row(0L, PlannedSource.BUDGET, 300L)), 0L)

        assertTrue(unenvelopedCardOf(summary) is EnvelopeCardUi.Tracking)
    }

    @Test
    fun cards_envelopesInOrder_thenUnenvelopedLast() {
        val first = progress(1_000L, 0L, emptyList(), own = 1_000L)
        val second = first.copy(envelope = Envelope("e2", "Fun", 1, amountCents = 2_000L), amountCents = 2_000L)
        val summary = EnvelopesSummary(listOf(first, second), listOf(row(null, PlannedSource.NONE, 50L)), 0L)

        val cards = envelopeCardsOf(summary)

        assertEquals(listOf<String?>("e1", "e2", null), cards.map { it.id })
        assertTrue(cards.last() is EnvelopeCardUi.Tracking)
    }

    @Test
    fun cards_withoutUnenveloped_areJustTheEnvelopes() {
        val summary = EnvelopesSummary(listOf(progress(1_000L, 0L, emptyList(), own = 1_000L)), emptyList(), 0L)

        assertEquals(1, envelopeCardsOf(summary).size)
        assertTrue(envelopeCardsOf(EnvelopesSummary(emptyList(), emptyList(), 0L)).isEmpty())
    }
}
