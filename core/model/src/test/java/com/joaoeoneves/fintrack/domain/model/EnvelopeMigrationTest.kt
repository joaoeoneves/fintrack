package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeMigrationTest {
    private fun cat(
        id: String,
        groupId: String? = null,
        custom: String? = id,
    ) = Category(CategoryId(id), custom, "icon", "slate", CategoryKind.SPENDING, 0, false, false, groupId)

    private fun line(
        id: String,
        order: Int = 0,
        earmark: Earmark? = null,
    ) = PlannedIncome(id, id, 100L, order, earmark = earmark)

    private fun migrate(
        budgets: List<Budget> = emptyList(),
        categories: List<Category> = emptyList(),
        envelopes: List<Envelope> = emptyList(),
        lines: List<PlannedIncome> = emptyList(),
    ) = envelopeMigrationOf(budgets, categories, envelopes, lines) { "name-${it.id.value}" }

    private val food = Envelope("g-food", "Food", 0)
    private val fun1 = Envelope("g-fun", "Fun", 1)
    private val dinners = cat("dinners", "g-food")
    private val movies = cat("movies", "g-fun")
    private val rent = cat("rent")

    private fun edit(
        id: String,
        name: String,
        members: Set<String>,
        fundedBy: String? = null,
    ) = EnvelopeEdit(id, name, null, fundedBy, members.mapTo(mutableSetOf()) { CategoryId(it) }, emptyMap())

    @Test
    fun groupsStayAsTheyAre_nothingToDo() {
        val result = migrate(listOf(Budget(dinners.id, 100)), listOf(dinners, movies), listOf(food, fun1))

        assertTrue(result.isEmpty)
        assertTrue(result.dropped.isEmpty())
    }

    @Test
    fun empty_isEmpty() {
        assertTrue(migrate().isEmpty)
    }

    @Test
    fun budgetedUngroupedCategory_getsItsOwnEnvelopeWithDisplayName() {
        val result = migrate(listOf(Budget(rent.id, 100)), listOf(dinners, rent), listOf(food))

        assertEquals(listOf(edit("env_rent", "name-rent", setOf("rent"))), result.envelopes)
        assertNull(result.envelopes.single().amountCents)
        assertTrue(
            result.envelopes
                .single()
                .limits
                .isEmpty(),
        )
    }

    @Test
    fun unbudgetedUngroupedCategory_getsNothing() {
        assertTrue(migrate(emptyList(), listOf(rent)).isEmpty)
    }

    @Test
    fun budgetOfAnUnknownCategory_isIgnored() {
        assertTrue(migrate(listOf(Budget(CategoryId("ghost"), 100)), listOf(rent)).isEmpty)
    }

    @Test
    fun newEnvelopes_areNamedTrimmedAndCapped_andOrderedAfterExisting() {
        val long = cat("long")
        val result =
            envelopeMigrationOf(
                listOf(Budget(rent.id, 1), Budget(long.id, 1)),
                listOf(rent, long),
                listOf(Envelope("x", "X", 5)),
                emptyList(),
            ) { "  ${"n".repeat(80)}  " }

        assertEquals(2, result.envelopes.size)
        assertTrue(result.envelopes.all { it.name.length == 50 })
    }

    @Test
    fun groupEarmark_becomesTheEnvelopesFundingLine_andIsCleared() {
        val salary = line("meal", earmark = Earmark.Group("g-food"))

        val result = migrate(emptyList(), listOf(dinners), listOf(food), listOf(salary))

        assertEquals(listOf(edit("g-food", "Food", setOf("dinners"), fundedBy = "meal")), result.envelopes)
        assertEquals(listOf(salary.copy(earmark = null)), result.lines)
    }

    @Test
    fun categoryEarmark_fundsTheEnvelopeContainingTheCategory() {
        val result =
            migrate(
                emptyList(),
                listOf(dinners),
                listOf(food),
                listOf(line("meal", earmark = Earmark.Category("dinners"))),
            )

        assertEquals("meal", result.envelopes.single().fundedByLineId)
        assertEquals("g-food", result.envelopes.single().id)
    }

    @Test
    fun categoryEarmarkOnAnUngroupedCategory_createsItsEnvelope() {
        val result =
            migrate(
                emptyList(),
                listOf(rent),
                emptyList(),
                listOf(line("meal", earmark = Earmark.Category("rent"))),
            )

        assertEquals(listOf(edit("env_rent", "name-rent", setOf("rent"), fundedBy = "meal")), result.envelopes)
        assertNull(result.lines.single().earmark)
    }

    @Test
    fun earmarkOnAnUnknownCategory_isDroppedAndCleared() {
        val result = migrate(lines = listOf(line("meal", earmark = Earmark.Category("ghost"))))

        assertEquals(listOf("meal"), result.dropped)
        assertTrue(result.envelopes.isEmpty())
        assertNull(result.lines.single().earmark)
    }

    @Test
    fun conflicts_lowestOrderLineWinsAnEnvelope_loserDropped() {
        val a = line("a", order = 0, earmark = Earmark.Group("g-food"))
        val b = line("b", order = 1, earmark = Earmark.Group("g-food"))

        val result = migrate(emptyList(), listOf(dinners), listOf(food), listOf(b, a))

        assertEquals("a", result.envelopes.single().fundedByLineId)
        assertEquals(listOf("b"), result.dropped)
        assertEquals(setOf("a", "b"), result.lines.map { it.id }.toSet())
        assertTrue(result.lines.all { it.earmark == null })
    }

    @Test
    fun aLineFundsOnlyItsFirstEnvelope() {
        val one = line("a", earmark = Earmark.Group("g-food"))
        val two = line("a", earmark = Earmark.Group("g-fun"))

        val result = migrate(emptyList(), listOf(dinners, movies), listOf(food, fun1), listOf(one, two))

        assertEquals(1, result.envelopes.size)
        assertEquals("g-food", result.envelopes.single().id)
        assertEquals(1, result.dropped.size)
    }

    @Test
    fun envelopeAlreadyFunded_keepsItsLine_earmarkedLineDropped() {
        val funded = food.copy(fundedByLineId = "old")

        val result =
            migrate(
                emptyList(),
                listOf(dinners),
                listOf(funded),
                listOf(line("meal", earmark = Earmark.Group("g-food"))),
            )

        assertTrue(result.envelopes.isEmpty())
        assertEquals(listOf("meal"), result.dropped)
        assertNull(result.lines.single().earmark)
    }

    @Test
    fun managedLines_alsoLoseTheirEarmark() {
        val managed = line("m", earmark = Earmark.Group("g-food")).copy(managedKey = "key")

        val result = migrate(emptyList(), listOf(dinners), listOf(food), listOf(managed))

        assertEquals(listOf(managed.copy(earmark = null)), result.lines)
    }

    @Test
    fun partialCache_categoryWithADanglingGroup_isPending_noWrites() {
        val orphan = cat("orphan", "g-not-loaded")

        val result = migrate(listOf(Budget(orphan.id, 100)), listOf(orphan, dinners), listOf(food))

        assertTrue(result.isEmpty)
    }

    @Test
    fun partialCache_groupEarmarkToAMissingEnvelope_isLeftPending() {
        val earmarked = line("meal", earmark = Earmark.Group("g-not-loaded"))

        val result = migrate(emptyList(), listOf(dinners), listOf(food), listOf(earmarked))

        assertTrue(result.isEmpty)
        assertTrue(result.dropped.isEmpty())
    }

    @Test
    fun partialCache_categoryEarmarkWhoseGroupIsMissing_isLeftPending() {
        val orphan = cat("orphan", "g-not-loaded")

        val result =
            migrate(
                emptyList(),
                listOf(orphan),
                emptyList(),
                listOf(line("meal", earmark = Earmark.Category("orphan"))),
            )

        assertTrue(result.isEmpty)
    }

    @Test
    fun pendingLine_doesNotBlockOtherLinesMigrating() {
        val pending = line("p", earmark = Earmark.Group("g-not-loaded"))
        val ready = line("r", order = 1, earmark = Earmark.Group("g-food"))

        val result = migrate(emptyList(), listOf(dinners), listOf(food), listOf(pending, ready))

        assertEquals(listOf(ready.copy(earmark = null)), result.lines)
    }

    @Test
    fun idempotent_applyingTheResultAndRunningAgainIsEmpty() {
        val categories = listOf(dinners, rent, movies.copy(groupId = null))
        val budgets = listOf(Budget(rent.id, 1), Budget(movies.id, 1))
        val lines = listOf(line("a", earmark = Earmark.Category("rent")), line("b", 1, Earmark.Group("g-food")))

        val first = migrate(budgets, categories, listOf(food), lines)
        val envelopes =
            listOf(food) +
                first.envelopes.filter { it.id != "g-food" }.mapIndexed {
                    i,
                    e,
                    ->
                    Envelope(checkNotNull(e.id), e.name, 1 + i, null, e.fundedByLineId)
                }
        val withFunding =
            envelopes.map { env ->
                first.envelopes.find { it.id == env.id }?.let { env.copy(fundedByLineId = it.fundedByLineId) }
                    ?: env
            }
        val filed = first.envelopes.flatMap { e -> e.categoryIds.map { it to e.id } }.toMap()
        val after = categories.map { c -> filed[c.id]?.let { c.copy(groupId = it) } ?: c }

        val second = migrate(budgets, after, withFunding, first.lines)

        assertTrue(first.envelopes.isNotEmpty())
        assertTrue(second.isEmpty)
        assertEquals("a", withFunding.first { it.id == "env_rent" }.fundedByLineId)
        assertEquals("b", withFunding.first { it.id == "g-food" }.fundedByLineId)
    }
}
