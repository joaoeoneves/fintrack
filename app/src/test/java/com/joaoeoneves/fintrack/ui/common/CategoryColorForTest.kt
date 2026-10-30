package com.joaoeoneves.fintrack.ui.common

import androidx.compose.ui.graphics.Color
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the plain, non-composable [categoryColorFor] resolver in CategoryDisplay.kt,
 * which backs the theme-aware [BuiltInCategories.color] composable property used by the pie chart
 * and its legend.
 */
class CategoryColorForTest {
    // ---- light mode: fixed fintech-accent brand hues ----

    @Test
    fun transfer_lightMode_resolvesToBrandBlue() {
        assertEquals(Color(0xFF2563EB), categoryColorFor(BuiltInCategories.TRANSFER, isDarkTheme = false))
    }

    @Test
    fun investments_lightMode_resolvesToBrandGreen() {
        assertEquals(Color(0xFF059669), categoryColorFor(BuiltInCategories.INVESTMENTS, isDarkTheme = false))
    }

    @Test
    fun shopping_lightMode_resolvesToBrandYellow() {
        assertEquals(Color(0xFFB45309), categoryColorFor(BuiltInCategories.SHOPPING, isDarkTheme = false))
    }

    @Test
    fun food_lightMode_resolvesToBrandViolet() {
        assertEquals(Color(0xFF7C3AED), categoryColorFor(BuiltInCategories.FOOD, isDarkTheme = false))
    }

    @Test
    fun recurring_lightMode_resolvesToBrandRed() {
        assertEquals(Color(0xFFDC2626), categoryColorFor(BuiltInCategories.RECURRING, isDarkTheme = false))
    }

    // ---- dark mode: distinct tuned tones ----

    @Test
    fun transfer_darkMode_resolvesToTunedBlue() {
        assertEquals(Color(0xFF3B82F6), categoryColorFor(BuiltInCategories.TRANSFER, isDarkTheme = true))
    }

    @Test
    fun investments_darkMode_resolvesToTunedGreen() {
        assertEquals(Color(0xFF10B981), categoryColorFor(BuiltInCategories.INVESTMENTS, isDarkTheme = true))
    }

    @Test
    fun shopping_darkMode_resolvesToTunedYellow() {
        assertEquals(Color(0xFFF59E0B), categoryColorFor(BuiltInCategories.SHOPPING, isDarkTheme = true))
    }

    @Test
    fun food_darkMode_resolvesToTunedViolet() {
        assertEquals(Color(0xFF8B5CF6), categoryColorFor(BuiltInCategories.FOOD, isDarkTheme = true))
    }

    @Test
    fun recurring_darkMode_resolvesToTunedRed() {
        assertEquals(Color(0xFFEF4444), categoryColorFor(BuiltInCategories.RECURRING, isDarkTheme = true))
    }

    // ---- light vs. dark: every category's color must actually differ between themes ----

    @Test
    fun everyCategory_lightAndDarkColors_areDistinct() {
        for (category in BuiltInCategories.all) {
            val light = categoryColorFor(category, isDarkTheme = false)
            val dark = categoryColorFor(category, isDarkTheme = true)
            assertNotEquals(
                "Expected $category to have distinct light/dark colors, but both were $light",
                light,
                dark,
            )
        }
    }

    // ---- within a theme: no two categories may share a color (pie slices must be distinguishable) ----

    @Test
    fun lightColors_arePairwiseDistinct() {
        assertPairwiseDistinct(isDarkTheme = false)
    }

    @Test
    fun darkColors_arePairwiseDistinct() {
        assertPairwiseDistinct(isDarkTheme = true)
    }

    private fun assertPairwiseDistinct(isDarkTheme: Boolean) {
        val colors = BuiltInCategories.all.associateWith { categoryColorFor(it, isDarkTheme) }
        val duplicates = colors.entries.groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }
        assertTrue("Categories sharing a color (dark=$isDarkTheme): $duplicates", duplicates.isEmpty())
    }
}
