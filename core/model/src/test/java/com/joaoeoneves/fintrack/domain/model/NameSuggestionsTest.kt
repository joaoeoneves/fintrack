package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NameSuggestionsTest {
    private var seq = 0

    private fun expense(
        name: String,
        category: Category = BuiltInCategories.FOOD,
        date: String = "2026-03-01T10:00:00Z",
        refund: Boolean = false,
    ) = Expense("e${seq++}", name, 500L, category, Instant.parse(date), null, isRefund = refund)

    private fun names(
        expenses: List<Expense>,
        query: String,
        limit: Int = 3,
    ) = nameSuggestions(expenses, query, limit).map { it.name }

    @Test
    fun prefixMatches_comeBeforeContainsMatches_evenWhenLessFrequent() {
        val history = listOf(expense("Big Pingo"), expense("Big Pingo"), expense("Big Pingo"), expense("Pingo Doce"))

        assertEquals(listOf("Pingo Doce", "Big Pingo"), names(history, "ping"))
    }

    @Test
    fun withinAGroup_moreFrequentFirst_thenMoreRecent() {
        val history =
            listOf(
                expense("Cafe A", date = "2026-03-10T10:00:00Z"),
                expense("Cafe B", date = "2026-03-01T10:00:00Z"),
                expense("Cafe B", date = "2026-03-02T10:00:00Z"),
                expense("Cafe C", date = "2026-03-20T10:00:00Z"),
            )

        assertEquals(listOf("Cafe B", "Cafe C", "Cafe A"), names(history, "caf"))
    }

    @Test
    fun exactMatch_refund_andBlankName_areExcluded() {
        val history =
            listOf(expense("Lidl"), expense("Lidl Extra", refund = true), expense("  "), expense("Lidl Norte"))

        assertEquals(listOf("Lidl Norte"), names(history, "lidl"))
    }

    @Test
    fun exactMatch_ignoresCaseAndDiacritics() {
        assertTrue(names(listOf(expense("Café")), "CAFE").isEmpty())
    }

    @Test
    fun queryUnderTwoCharacters_returnsNothing() {
        val history = listOf(expense("Lidl"))

        assertTrue(names(history, "l").isEmpty())
        assertTrue(names(history, " l ").isEmpty())
        assertTrue(names(history, "").isEmpty())
    }

    @Test
    fun twoCharacterQuery_isEnough() = assertEquals(listOf("Lidl"), names(listOf(expense("Lidl")), "li"))

    @Test
    fun diacriticsAndCase_areIgnoredBothWays() {
        assertEquals(listOf("Padaria Açoreana"), names(listOf(expense("Padaria Açoreana")), "ACORE"))
        assertEquals(listOf("Cafe Central"), names(listOf(expense("Cafe Central")), "café"))
    }

    @Test
    fun limit_isApplied() {
        val history = listOf("Ab1", "Ab2", "Ab3", "Ab4").map { expense(it) }

        assertEquals(2, names(history, "ab", limit = 2).size)
    }

    @Test
    fun nameAndCategory_comeFromTheLatestExpense() {
        val history =
            listOf(
                expense("pingo doce", BuiltInCategories.FOOD, "2026-01-01T10:00:00Z"),
                expense("Pingo  Doce", BuiltInCategories.SHOPPING, "2026-05-01T10:00:00Z"),
            )

        assertEquals(
            listOf(NameSuggestion("Pingo  Doce", BuiltInCategories.SHOPPING.id)),
            nameSuggestions(history, "pin"),
        )
    }
}
