package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.YearMonth

class AmountVersionsTest {
    private fun ym(
        y: Int,
        m: Int,
    ) = YearMonth.of(y, m)

    private fun v(
        m: Int,
        cents: Long?,
    ) = AmountVersion(ym(2026, m), cents)

    @Test
    fun amountAt_empty_isFallback() {
        assertEquals(7L, emptyList<AmountVersion>().amountAt(ym(2026, 5), 7L))
        assertNull(emptyList<AmountVersion>().amountAt(ym(2026, 5), null))
    }

    @Test
    fun amountAt_boundaries() {
        val versions = listOf(v(3, 100), v(6, 200))
        assertEquals(9L, versions.amountAt(ym(2026, 2), 9L))
        assertEquals(100L, versions.amountAt(ym(2026, 3), 9L))
        assertEquals(100L, versions.amountAt(ym(2026, 5), 9L))
        assertEquals(200L, versions.amountAt(ym(2026, 6), 9L))
        assertEquals(200L, versions.amountAt(ym(2027, 1), 9L))
    }

    @Test
    fun amountAt_unsortedInput_stillPicksTheLatestApplicable() {
        val versions = listOf(v(6, 200), v(3, 100), v(9, 300))
        assertEquals(200L, versions.amountAt(ym(2026, 7), 0L))
    }

    @Test
    fun amountAt_nullVersionAmount_isReturnedOnceApplied() {
        val versions = listOf(v(3, 100), v(6, null))
        assertEquals(100L, versions.amountAt(ym(2026, 5), 9L))
        assertNull(versions.amountAt(ym(2026, 6), 9L))
    }

    @Test
    fun withVersion_appendsAndSorts() {
        val result = withVersion(listOf(v(8, 3), v(2, 1)), ym(2026, 5), 2)
        assertEquals(listOf(v(2, 1), v(5, 2), v(8, 3)), result)
    }

    @Test
    fun withVersion_replacesSameMonthAndKeepsLaterVersions() {
        val result = withVersion(listOf(v(2, 1), v(5, 2), v(8, 3)), ym(2026, 5), 9)
        assertEquals(listOf(v(2, 1), v(5, 9), v(8, 3)), result)
    }

    @Test
    fun withVersion_capsAtTwelve_dropsOldest() {
        val existing = (1..12).map { AmountVersion(ym(2025, it), it.toLong()) }
        val result = withVersion(existing, ym(2026, 1), 99)
        assertEquals(MAX_AMOUNT_VERSIONS, result.size)
        // The two oldest fold into one floor: oldest dropped month, newest dropped amount.
        assertEquals(AmountVersion(ym(2025, 1), 2), result.first())
        assertEquals(AmountVersion(ym(2026, 1), 99), result.last())
    }

    @Test
    fun withVersion_customCap() {
        val result = withVersion(listOf(v(1, 1), v(2, 2)), ym(2026, 3), 3, cap = 2)
        assertEquals(listOf(v(1, 2), v(3, 3)), result)
    }

    @Test
    fun withVersion_doesNotMutateInput() {
        val input = listOf(v(1, 1), v(5, 5))
        val copy = input.toList()
        withVersion(input, ym(2026, 3), 3)
        assertEquals(copy, input)
    }

    private val envelope = Envelope("e", "Food", 0, amountCents = 500)
    private val budget = Budget(CategoryId("FOOD"), 1_000)
    private val line = PlannedIncome("l", "Salary", 100_000L, 0)

    @Test
    fun envelopeAsOf_startMonthAndVersions() {
        val e = envelope.copy(startMonth = ym(2026, 3), versions = listOf(v(3, 500), v(6, null)))
        assertNull(e.asOf(ym(2026, 2)))
        assertEquals(500L, e.asOf(ym(2026, 3))?.amountCents)
        assertEquals(500L, e.asOf(ym(2026, 5))?.amountCents)
        // A null version amount means "sum of categories": the amount becomes null.
        assertNull(e.asOf(ym(2026, 6))?.amountCents)
        assertEquals("Food", e.asOf(ym(2026, 6))?.name)
    }

    @Test
    fun envelopeAsOf_noStart_isAlways() {
        assertEquals(envelope, envelope.asOf(ym(1999, 1)))
    }

    @Test
    fun budgetAsOf_startMonthAndVersions_keepOtherFields() {
        val b =
            budget.copy(skipMonths = setOf(2), startMonth = ym(2026, 3), versions = listOf(v(6, 2_000), v(9, null)))
        assertNull(b.asOf(ym(2026, 2)))
        assertEquals(1_000L, b.asOf(ym(2026, 3))?.limitCents)
        assertEquals(2_000L, b.asOf(ym(2026, 7))?.limitCents)
        // A null version amount keeps the current limit.
        assertEquals(1_000L, b.asOf(ym(2026, 9))?.limitCents)
        assertEquals(setOf(2), b.asOf(ym(2026, 7))?.skipMonths)
        assertEquals(CategoryId("FOOD"), b.asOf(ym(2026, 7))?.categoryId)
        assertEquals(budget, budget.asOf(ym(1999, 1)))
    }

    @Test
    fun plannedIncomeAsOf_startMonthAndVersions() {
        val l = line.copy(startMonth = ym(2026, 3), versions = listOf(v(6, 120_000), v(9, null)), payDay = 25)
        assertNull(l.asOf(ym(2026, 2)))
        assertEquals(100_000L, l.asOf(ym(2026, 3))?.amountCents)
        assertEquals(120_000L, l.asOf(ym(2026, 6))?.amountCents)
        assertEquals(100_000L, l.asOf(ym(2026, 9))?.amountCents)
        assertEquals(25, l.asOf(ym(2026, 6))?.payDay)
        assertEquals(line, line.asOf(ym(1999, 1)))
    }

    @Test
    fun listAsOf_filtersAndKeepsOrder() {
        val a = line.copy(id = "a")
        val b = line.copy(id = "b", startMonth = ym(2026, 6))
        val c = line.copy(id = "c", startMonth = ym(2026, 2))
        assertEquals(listOf("a", "c"), listOf(a, b, c).asOf(ym(2026, 3)).map { it.id })
        assertEquals(listOf("a", "b", "c"), listOf(a, b, c).asOf(ym(2026, 6)).map { it.id })
        val envs = listOf(envelope.copy(id = "x", startMonth = ym(2026, 9)), envelope.copy(id = "y"))
        assertEquals(listOf("y"), envs.asOf(ym(2026, 3)).map { it.id })
        val buds = listOf(budget.copy(startMonth = ym(2026, 9)), budget.copy(categoryId = CategoryId("B")))
        assertEquals(listOf(CategoryId("B")), buds.asOf(ym(2026, 3)).map { it.categoryId })
    }

    @Test
    fun editedFrom_sameAmount_returnsSameInstance() {
        assertSame(line, line.editedFrom(ym(2026, 5), 100_000L))
        assertSame(budget, budget.editedFrom(ym(2026, 5), 1_000L))
        assertSame(envelope, envelope.editedFrom(ym(2026, 5), 500L))
        val withStart = line.copy(startMonth = ym(2026, 2))
        assertSame(withStart, withStart.editedFrom(ym(2026, 5), 100_000L))
    }

    @Test
    fun editedFrom_firstChange_seedsOldAmountFromEarliestMonth() {
        val edited = line.editedFrom(ym(2026, 5), 120_000L)
        assertEquals(120_000L, edited.amountCents)
        assertNull(edited.startMonth)
        assertEquals(
            listOf(AmountVersion(EARLIEST_MONTH, 100_000L), AmountVersion(ym(2026, 5), 120_000L)),
            edited.versions,
        )
        assertEquals(100_000L, edited.asOf(ym(2026, 4))?.amountCents)
        assertEquals(120_000L, edited.asOf(ym(2026, 5))?.amountCents)
    }

    @Test
    fun editedFrom_withStart_seedsOldAmountFromStart() {
        val edited = budget.copy(startMonth = ym(2026, 2)).editedFrom(ym(2026, 5), 2_000L)
        assertEquals(listOf(v(2, 1_000), v(5, 2_000)), edited.versions)
        assertEquals(2_000L, edited.limitCents)
    }

    @Test
    fun editedFrom_beforeStart_movesStartBack() {
        val edited = line.copy(startMonth = ym(2026, 6)).editedFrom(ym(2026, 3), 90_000L)
        assertEquals(ym(2026, 3), edited.startMonth)
        assertEquals(90_000L, edited.asOf(ym(2026, 3))?.amountCents)
        // The later version is kept, so the latest amount is still the old one.
        assertEquals(100_000L, edited.asOf(ym(2026, 6))?.amountCents)
        assertEquals(100_000L, edited.amountCents)
    }

    @Test
    fun editedFrom_envelope_toNullAmount_andLaterEditReplacesSameMonth() {
        val e = envelope.editedFrom(ym(2026, 5), null)
        assertNull(e.amountCents)
        assertEquals(500L, e.asOf(ym(2026, 4))?.amountCents)
        val again = e.editedFrom(ym(2026, 5), 700L)
        assertEquals(700L, again.amountCents)
        assertEquals(2, again.versions.size)
    }

    @Test
    fun startingAt_setsStartAndClearsVersions() {
        val old = line.copy(versions = listOf(v(1, 1)))
        assertEquals(line.copy(startMonth = ym(2026, 7)), old.startingAt(ym(2026, 7)))
        val oldBudget = budget.copy(versions = listOf(v(1, 1)))
        assertEquals(budget.copy(startMonth = ym(2026, 7)), oldBudget.startingAt(ym(2026, 7)))
        assertEquals(envelope.copy(startMonth = ym(2026, 7)), envelope.startingAt(ym(2026, 7)))
    }
}
