package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryBudgetProgressTest {
    private fun budget(
        limit: Long?,
        spent: Long,
    ) = CategoryBudget(BuiltInCategories.SHOPPING, limit, spent)

    @Test
    fun progressFraction_withinLimit() {
        assertEquals(0.5f, budget(1_000L, 500L).progressFraction(), 1e-6f)
        assertEquals(0f, budget(1_000L, 0L).progressFraction(), 0f)
        assertEquals(1f, budget(1_000L, 1_000L).progressFraction(), 0f)
    }

    @Test
    fun progressFraction_clampedWhenOver() {
        assertEquals(1f, budget(1_000L, 1_300L).progressFraction(), 0f)
    }

    @Test
    fun progressFraction_noLimitOrNonPositiveLimit_isZero() {
        assertEquals(0f, budget(null, 500L).progressFraction(), 0f)
        assertEquals(0f, budget(0L, 500L).progressFraction(), 0f)
        assertEquals(0f, budget(-100L, 500L).progressFraction(), 0f)
    }

    @Test
    fun progressFraction_negativeSpend_clampedToZero() {
        assertEquals(0f, budget(1_000L, -200L).progressFraction(), 0f)
    }

    @Test
    fun percentUsed_isUncapped() {
        assertEquals(130, budget(1_000L, 1_300L).percentUsed())
        assertEquals(50, budget(1_000L, 500L).percentUsed())
        assertEquals(0, budget(1_000L, 0L).percentUsed())
        assertEquals(100, budget(1_000L, 1_000L).percentUsed())
    }

    @Test
    fun percentUsed_rounds() {
        assertEquals(33, budget(300L, 100L).percentUsed())
        assertEquals(67, budget(300L, 200L).percentUsed())
    }

    @Test
    fun percentUsed_noLimit_isZero() {
        assertEquals(0, budget(null, 500L).percentUsed())
        assertEquals(0, budget(0L, 500L).percentUsed())
    }
}
