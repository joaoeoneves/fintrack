package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** [categoriesAboveAverage]: strict >, empty months ignored, at least three earlier months with spend. */
class CategoriesAboveAverageTest {
    private val food = CategoryId("FOOD")
    private val leisure = CategoryId("FUN")

    private fun above(
        series: Map<CategoryId, List<Long>>,
        index: Int,
        window: Int = COMPARISON_WINDOW_MONTHS,
    ) = categoriesAboveAverage(series, index, window)

    @Test
    fun aboveAverage_withThreeEarlierMonths_isFlagged() {
        assertEquals(setOf(food), above(mapOf(food to listOf(100L, 100L, 100L, 101L)), 3))
    }

    @Test
    fun equalToAverage_isNotFlagged_strict() {
        assertEquals(emptySet<CategoryId>(), above(mapOf(food to listOf(100L, 100L, 100L, 100L)), 3))
    }

    @Test
    fun lessThanThreeMonthsWithSpend_isNeverFlagged() {
        assertEquals(emptySet<CategoryId>(), above(mapOf(food to listOf(100L, 100L, 9_999L)), 2))
    }

    @Test
    fun emptyMonths_areNotCounted_neitherAsDataNorAsZeroInTheAverage() {
        // Two months with spend plus two empty ones: still below the three-month minimum.
        assertEquals(emptySet<CategoryId>(), above(mapOf(food to listOf(100L, 0L, 100L, 0L, 500L)), 4))
        // Three with spend and a gap: average is 100 (not 75), so 101 is above.
        assertEquals(setOf(food), above(mapOf(food to listOf(100L, 0L, 100L, 100L, 101L)), 4))
    }

    @Test
    fun onlyTheWindowCounts_olderMonthsAreIgnored() {
        val series = listOf(10_000L, 10_000L, 100L, 100L, 100L, 100L, 100L, 100L, 150L)

        assertEquals(setOf(food), above(mapOf(food to series), 8))
    }

    @Test
    fun nothingSpentThisMonth_isNotFlagged_andCategoriesAreIndependent() {
        val series =
            mapOf(
                food to listOf(100L, 100L, 100L, 0L),
                leisure to listOf(50L, 50L, 50L, 80L),
            )

        assertEquals(setOf(leisure), above(series, 3))
    }

    @Test
    fun indexBeyondSeries_andEmptyInput_yieldNothing() {
        assertEquals(emptySet<CategoryId>(), above(mapOf(food to listOf(100L, 100L, 100L)), 9))
        assertEquals(emptySet<CategoryId>(), above(emptyMap(), 0))
    }

    @Test
    fun customMinMonths_isHonoured() {
        val series = mapOf(food to listOf(100L, 100L, 200L))

        assertEquals(emptySet<CategoryId>(), categoriesAboveAverage(series, 2))
        assertEquals(setOf(food), categoriesAboveAverage(series, 2, minMonths = 2))
    }
}
