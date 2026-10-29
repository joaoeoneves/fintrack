package com.joaoeoneves.fintrack.ui.plan.envelopes

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class EnvelopeDraftTest {
    private val a = CategoryId("a")
    private val b = CategoryId("b")
    private val c = CategoryId("c")
    private val cats =
        listOf(
            userCategory("a", order = 2).copy(groupId = "env"),
            userCategory("b", order = 1).copy(groupId = "env"),
            userCategory("c", order = 0).copy(groupId = "other"),
        )

    private fun draft(
        name: String = "Food",
        mode: EnvelopeBudgetMode = EnvelopeBudgetMode.SINGLE,
        amount: Long? = 5000,
        ids: List<CategoryId> = emptyList(),
        limits: Map<CategoryId, Long?> = emptyMap(),
    ) = EnvelopeDraft(null, name, mode, amount, ids, limits, null)

    @Test
    fun draftOf_null_isBlankSingle() {
        val d = draftOf(null, cats, emptyList())
        assertEquals(EnvelopeDraft(null, "", EnvelopeBudgetMode.SINGLE, null, emptyList(), emptyMap(), null), d)
    }

    @Test
    fun draftOf_amount_isSingleAndIgnoresLimits() {
        val d = draftOf(Envelope("env", "Food", 0, 9000, "line"), cats, listOf(Budget(a, 100)))
        assertEquals(EnvelopeBudgetMode.SINGLE, d.mode)
        assertEquals(9000L, d.amountCents)
        assertEquals("line", d.fundedByLineId)
        assertEquals(listOf(b, a), d.categoryIds)
        assertEquals(emptyMap<CategoryId, Long?>(), d.limits)
    }

    @Test
    fun draftOf_limitsWithoutAmount_isPerCategory() {
        val d = draftOf(Envelope("env", "Food", 0), cats, listOf(Budget(a, 100), Budget(c, 7)))
        assertEquals(EnvelopeBudgetMode.PER_CATEGORY, d.mode)
        assertEquals(mapOf(b to null, a to 100L), d.limits)
    }

    @Test
    fun draftOf_noAmountNoLimits_isNone() {
        val d = draftOf(Envelope("env", "Food", 0), cats, emptyList())
        assertEquals(EnvelopeBudgetMode.NONE, d.mode)
        assertEquals(listOf(b, a), d.categoryIds)
    }

    @Test
    fun draftOf_noMembers_isNone() {
        assertEquals(EnvelopeBudgetMode.NONE, draftOf(Envelope("x", "X", 0), cats, emptyList()).mode)
    }

    @Test
    fun validate_blankName() {
        assertEquals(EnvelopeDraftError.BlankName, validateEnvelope(draft(name = "   "), emptyList()))
        assertEquals(EnvelopeDraftError.BlankName, validateEnvelope(draft(name = ""), emptyList()))
    }

    @Test
    fun validate_duplicateNameIsCaseInsensitiveAndTrimmed() {
        assertEquals(EnvelopeDraftError.DuplicateName, validateEnvelope(draft(name = " food "), listOf("FOOD")))
        assertEquals(EnvelopeDraftError.DuplicateName, validateEnvelope(draft(name = "Food"), listOf(" food")))
        assertNull(validateEnvelope(draft(name = "Food"), listOf("Foods")))
    }

    @Test
    fun validate_blankNameWinsOverMissingAmount() {
        assertEquals(EnvelopeDraftError.BlankName, validateEnvelope(draft(name = "", amount = null), emptyList()))
    }

    @Test
    fun validate_singleNeedsPositiveAmount() {
        assertEquals(EnvelopeDraftError.MissingAmount, validateEnvelope(draft(amount = null), emptyList()))
        assertEquals(EnvelopeDraftError.MissingAmount, validateEnvelope(draft(amount = 0), emptyList()))
        assertEquals(EnvelopeDraftError.MissingAmount, validateEnvelope(draft(amount = -1), emptyList()))
        assertNull(validateEnvelope(draft(amount = 1), emptyList()))
    }

    @Test
    fun validate_otherModesDoNotNeedAmount() {
        assertNull(validateEnvelope(draft(mode = EnvelopeBudgetMode.PER_CATEGORY, amount = null), emptyList()))
        assertNull(validateEnvelope(draft(mode = EnvelopeBudgetMode.NONE, amount = null), emptyList()))
    }

    @Test
    fun edit_single_keepsAmountAndClearsOnlyRemovedLimits() {
        val edit =
            envelopeEditOf(
                draft(name = "  Food ", amount = 5000, ids = listOf(a)).copy(id = "env"),
                listOf(Budget(a, 100)),
                setOf(a, b),
            )
        assertEquals("env", edit.id)
        assertEquals("Food", edit.name)
        assertEquals(5000L, edit.amountCents)
        assertEquals(setOf(a), edit.categoryIds)
        assertEquals(mapOf<CategoryId, Budget?>(b to null), edit.limits)
    }

    @Test
    fun edit_perCategory_writesLimitsKeepingSkipMonths() {
        val edit =
            envelopeEditOf(
                draft(
                    mode = EnvelopeBudgetMode.PER_CATEGORY,
                    amount = 777,
                    ids = listOf(a, b, c),
                    limits = mapOf(a to 200L, b to 0L, c to null),
                ),
                listOf(Budget(a, 100, setOf(3, 4))),
                setOf(a, b, CategoryId("gone")),
            )
        assertNull(edit.amountCents)
        assertEquals(
            mapOf<CategoryId, Budget?>(
                CategoryId("gone") to null,
                a to Budget(a, 200, setOf(3, 4)),
                b to null,
                c to null,
            ),
            edit.limits,
        )
    }

    @Test
    fun edit_perCategory_newLimitHasNoSkipMonths() {
        val edit =
            envelopeEditOf(
                draft(mode = EnvelopeBudgetMode.PER_CATEGORY, ids = listOf(a), limits = mapOf(a to 50L)),
                emptyList(),
                emptySet(),
            )
        assertEquals(Budget(a, 50, emptySet()), edit.limits[a])
        assertNull(edit.id)
    }

    @Test
    fun edit_none_clearsAllMemberAndRemovedLimits() {
        val edit =
            envelopeEditOf(
                draft(mode = EnvelopeBudgetMode.NONE, amount = 9, ids = listOf(a)),
                listOf(Budget(a, 100)),
                setOf(a, b),
            )
        assertNull(edit.amountCents)
        assertEquals(mapOf<CategoryId, Budget?>(a to null, b to null), edit.limits)
    }

    @Test
    fun edit_carriesTheDraftsEffectiveFrom() {
        val month = YearMonth.of(2027, 3)
        val edit = envelopeEditOf(draft().copy(effectiveFrom = month), emptyList(), emptySet())
        assertEquals(month, edit.effectiveFrom)
    }

    @Test
    fun draftOf_carriesTheGivenEffectiveFrom() {
        val month = YearMonth.of(2027, 3)
        assertEquals(month, draftOf(null, cats, emptyList(), month).effectiveFrom)
        assertEquals(month, draftOf(Envelope("env", "Food", 0), cats, emptyList(), month).effectiveFrom)
    }

    @Test
    fun budgetChanged_ignoresTheMonthAndTheName() {
        val original = draft(amount = 5000)
        val renamed = original.copy(name = "Other", effectiveFrom = YearMonth.of(2030, 1))
        assertFalse(budgetChanged(original, renamed))
        assertTrue(budgetChanged(original, original.copy(amountCents = 6000)))
    }
}
