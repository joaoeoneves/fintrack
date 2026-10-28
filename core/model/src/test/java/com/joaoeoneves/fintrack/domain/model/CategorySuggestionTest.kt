package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CategorySuggestionTest {
    private fun expense(
        id: String,
        name: String,
        category: Category,
        date: String = "2026-03-01T10:00:00Z",
    ) = Expense(id, name, 500L, category, Instant.parse(date), null)

    @Test
    fun normalizedMerchantName_lowercases() = assertEquals("pingo doce", normalizedMerchantName("PiNgO DoCe"))

    @Test
    fun normalizedMerchantName_stripsDiacritics() {
        assertEquals("cafe sao joao", normalizedMerchantName("Café São João"))
    }

    @Test
    fun normalizedMerchantName_trimsAndCollapsesWhitespace() {
        assertEquals("a b c", normalizedMerchantName("  a \t b\n  c  "))
    }

    @Test
    fun normalizedMerchantName_blankBecomesEmpty() = assertEquals("", normalizedMerchantName("   "))

    @Test
    fun normalizedMerchantName_variantsCollide() =
        assertEquals(normalizedMerchantName("Padaria  Açoreana"), normalizedMerchantName("padaria acoreana "))

    @Test
    fun index_emptyInput_isEmpty() = assertTrue(categorySuggestionIndex(emptyList()).isEmpty())

    @Test
    fun index_latestByDateWins_regardlessOfListOrder() {
        val newer = expense("1", "Lidl", BuiltInCategories.SHOPPING, "2026-05-01T10:00:00Z")
        val older = expense("2", "Lidl", BuiltInCategories.RECURRING, "2026-01-01T10:00:00Z")

        val index = categorySuggestionIndex(listOf(newer, older))

        assertEquals(mapOf("lidl" to BuiltInCategories.SHOPPING.id), index)
    }

    @Test
    fun index_sameDate_largerIdWins() {
        val a = expense("a", "Lidl", BuiltInCategories.SHOPPING)
        val b = expense("b", "Lidl", BuiltInCategories.RECURRING)

        assertEquals(BuiltInCategories.RECURRING.id, categorySuggestionIndex(listOf(b, a))["lidl"])
        assertEquals(BuiltInCategories.RECURRING.id, categorySuggestionIndex(listOf(a, b))["lidl"])
    }

    @Test
    fun index_blankNamesIgnored() {
        val blanks =
            listOf(
                expense("1", "  ", BuiltInCategories.SHOPPING),
                expense("2", "", BuiltInCategories.SHOPPING),
            )
        val index = categorySuggestionIndex(blanks)

        assertTrue(index.isEmpty())
    }

    @Test
    fun index_keysAreNormalized_andNamesMerge() {
        val first = expense("1", "CAFÉ  Central", BuiltInCategories.SHOPPING, "2026-01-01T10:00:00Z")
        val second = expense("2", "cafe central", BuiltInCategories.RECURRING, "2026-02-01T10:00:00Z")

        val index = categorySuggestionIndex(listOf(first, second))

        assertEquals(mapOf("cafe central" to BuiltInCategories.RECURRING.id), index)
    }
}
