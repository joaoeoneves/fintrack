package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class QuickAddTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 6, 30)
    private var seq = 0

    @Suppress("LongParameterList")
    private fun expense(
        name: String,
        date: LocalDate = today,
        cents: Long = 500L,
        category: Category = BuiltInCategories.FOOD,
        id: String = "e${seq++}",
        refund: Boolean = false,
    ) = Expense(id, name, cents, category, Instant.parse("${date}T10:00:00Z"), null, isRefund = refund)

    private fun twice(
        name: String,
        date: LocalDate = today,
    ) = listOf(expense(name, date), expense(name, date))

    private fun run(expenses: List<Expense>) = quickAddSuggestions(expenses, today, zone)

    @Test
    fun day90_isIncluded_day91_isExcluded() {
        val day90 = today.minusDays(89)
        val day91 = today.minusDays(90)

        assertEquals(listOf("In"), run(twice("In", day90)).map { it.name })
        assertTrue(run(twice("Out", day91)).isEmpty())
    }

    @Test
    fun futureRecords_areOutsideTheWindow() {
        assertTrue(run(twice("Later", today.plusDays(1))).isEmpty())
    }

    @Test
    fun oneRecord_isNotEnough_twoAre() {
        assertTrue(run(listOf(expense("Cafe"))).isEmpty())
        assertEquals(2, run(twice("Cafe")).single().count)
    }

    @Test
    fun recordsOutsideTheWindow_doNotCountTowardsTheMinimum() {
        val history = listOf(expense("Cafe"), expense("Cafe", today.minusDays(120)))

        assertTrue(run(history).isEmpty())
    }

    @Test
    fun recEntries_refunds_andBlankNames_areExcluded() {
        val history =
            listOf(expense("Rent", id = "rec_r1_1"), expense("Rent", id = "rec_r1_2")) +
                listOf(expense("Ref", refund = true), expense("Ref", refund = true)) +
                listOf(expense("  "), expense(""))

        assertTrue(run(history).isEmpty())
    }

    @Test
    fun aRefund_doesNotCountTowardsAGroup() {
        assertTrue(run(listOf(expense("Cafe"), expense("Cafe", refund = true))).isEmpty())
    }

    @Test
    fun orderedByCount_thenLatestDate_thenName() {
        val history =
            twice("Beta", today.minusDays(5)) +
                twice("Alpha", today.minusDays(5)) +
                twice("Newer", today.minusDays(1)) +
                listOf(40L, 41L, 42L).map { expense("Many", today.minusDays(it)) }

        assertEquals(listOf("Many", "Newer", "Alpha", "Beta"), run(history).map { it.name })
    }

    @Test
    fun limit_isFour_byDefault() {
        val history = listOf("A", "B", "C", "D", "E").flatMap { twice(it) }

        assertEquals(4, run(history).size)
    }

    @Test
    fun latestRecord_suppliesNameAmountAndCategory_andNamesGroupIgnoringCaseAndDiacritics() {
        val history =
            listOf(
                expense("cafe", today.minusDays(10), 300L, BuiltInCategories.FOOD),
                expense("Café", today.minusDays(2), 450L, BuiltInCategories.SHOPPING),
                expense("CAFE", today.minusDays(20), 200L, BuiltInCategories.FOOD),
            )

        assertEquals(
            listOf(QuickAddSuggestion("Café", 450L, BuiltInCategories.SHOPPING.id, 3)),
            run(history),
        )
    }

    @Test
    fun windowUsesTheLocalDate_notUtc() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        // 2026-06-29T20:00Z is already June 30 in Tokyo, i.e. today.
        val e = Expense("x", "Cafe", 1L, BuiltInCategories.FOOD, Instant.parse("2026-06-29T20:00:00Z"), null)
        val f = e.copy(id = "y")

        assertEquals(1, quickAddSuggestions(listOf(e, f), today, tokyo).size)
    }
}
