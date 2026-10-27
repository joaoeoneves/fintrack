package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.importChunks
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RuleChunkingTest {
    private fun custom(i: Int) = CategoryId("c$i")

    @Test
    fun empty_isEmpty() {
        assertTrue(emptyList<Int>().chunkedForRules { null }.isEmpty())
    }

    @Test
    fun sizeCap_400PerChunk_orderPreserved() {
        val items = (0 until 801).toList()

        val chunks = items.chunkedForRules { null }

        assertEquals(listOf(400, 400, 1), chunks.map { it.size })
        assertEquals(items, chunks.flatten())
    }

    @Test
    fun exactly400_isOneChunk() {
        assertEquals(1, (0 until 400).toList().chunkedForRules { null }.size)
    }

    @Test
    fun sixteenthDistinctCustomCategory_startsNewChunk() {
        val items = (0 until 16).toList()

        val chunks = items.chunkedForRules { custom(it) }

        assertEquals(listOf(15, 1), chunks.map { it.size })
    }

    @Test
    fun fifteenDistinctCustomCategories_fitOneChunk() {
        assertEquals(1, (0 until 15).toList().chunkedForRules { custom(it) }.size)
    }

    @Test
    fun repeatedCustomCategory_countsOnce() {
        val items = (0 until 100).toList()

        assertEquals(1, items.chunkedForRules { custom(it % 15) }.size)
    }

    @Test
    fun builtInsAndNull_doNotCountTowardCustomLimit() {
        val items = (0 until 60).toList()

        val chunks =
            items.chunkedForRules {
                when {
                    it % 3 == 0 -> null
                    it % 3 == 1 -> BuiltInCategories.FOOD.id
                    else -> custom(it % 15)
                }
            }

        assertEquals(1, chunks.size)
    }

    @Test
    fun customSetResetsPerChunk_andSizeCapStillApplies() {
        val items = (0 until 30).toList()

        val chunks = items.chunkedForRules(maxSize = 10, maxCustomCategories = 3) { custom(it) }

        assertEquals(listOf(3, 3, 3, 3, 3, 3, 3, 3, 3, 3), chunks.map { it.size })
        assertEquals(items, chunks.flatten())
    }

    @Test
    fun customLimitAllowsMoreBuiltInsInSameChunk() {
        val items = listOf(custom(1), BuiltInCategories.FOOD.id, custom(2), BuiltInCategories.SHOPPING.id, custom(3))

        val chunks = items.chunkedForRules(maxCustomCategories = 2) { it }

        assertEquals(listOf(4, 1), chunks.map { it.size })
    }

    @Test
    fun importChunks_stillUsesExpenseCategoryAndDefaults() {
        val at = Instant.parse("2026-03-10T12:00:00Z")
        val expenses = (0 until 16).map { testExpense("e$it", 100, at, userCategory("c$it")) }

        val chunks = expenses.importChunks()

        assertEquals(listOf(15, 1), chunks.map { it.size })
        assertEquals(expenses, chunks.flatten())
        assertEquals(
            400,
            (0 until 500)
                .map { testExpense("b$it", 1, at) }
                .importChunks()
                .first()
                .size,
        )
    }
}
